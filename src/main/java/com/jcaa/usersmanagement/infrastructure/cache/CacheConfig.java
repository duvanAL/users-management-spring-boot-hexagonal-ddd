package com.jcaa.usersmanagement.infrastructure.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.usersmanagement.infrastructure.entrypoint.rest.dto.response.UserRestResponse;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties({AppCacheProperties.class, ValkeyProperties.class})
public class CacheConfig implements CachingConfigurer {

    public static final String USERS_LIST = "users-list";
    public static final String USER_BY_ID = "users-by-id";
    private static final String KEY_PREFIX = "users-management:";
    private static final Logger LOG = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true")
    public LettuceConnectionFactory valkeyConnectionFactory(final ValkeyProperties p) {
        final RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(p.host(), p.port());
        if (StringUtils.hasText(p.username())) {
            server.setUsername(p.username());
        }
        if (StringUtils.hasText(p.password())) {
            server.setPassword(RedisPassword.of(p.password()));
        }
        final ClientOptions options =
                ClientOptions.builder()
                        .socketOptions(
                                SocketOptions.builder()
                                        .connectTimeout(Duration.ofMillis(p.connectTimeoutMs()))
                                        .build())
                        .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(p.commandTimeoutMs())))
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .build();
        final LettuceClientConfiguration.LettuceClientConfigurationBuilder client =
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofMillis(p.commandTimeoutMs()))
                        .clientOptions(options);
        if (p.sslEnabled()) {
            client.useSsl();
        }
        return new LettuceConnectionFactory(server, client.build());
    }

    @Bean
    @ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true")
    public CacheManager cacheManager(
            final LettuceConnectionFactory factory, final AppCacheProperties props) {
        final ObjectMapper mapper = new ObjectMapper();
        final JavaType listType =
                mapper.getTypeFactory().constructCollectionType(List.class, UserRestResponse.class);

        final RedisCacheConfiguration base =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofSeconds(props.ttlSeconds()))
                        .disableCachingNullValues()
                        .computePrefixWith(name -> KEY_PREFIX + name + ":")
                        .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()));

        return RedisCacheManager.builder(factory)
                .withCacheConfiguration(
                        USERS_LIST,
                        base.serializeValuesWith(
                                SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, listType))))
                .withCacheConfiguration(
                        USER_BY_ID,
                        base.serializeValuesWith(
                                SerializationPair.fromSerializer(
                                        new Jackson2JsonRedisSerializer<>(mapper, UserRestResponse.class))))
                .disableCreateOnMissingCache()
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.cache.enabled", havingValue = "false", matchIfMissing = true)
    public CacheManager noOpCacheManager() {
        return new NoOpCacheManager();
    }

    /** Cualquier fallo de Valkey se registra y la consulta sigue contra PostgreSQL. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override public void handleCacheGetError(RuntimeException e, Cache c, Object k) { warn("get", c, e); }
            @Override public void handleCachePutError(RuntimeException e, Cache c, Object k, Object v) { warn("put", c, e); }
            @Override public void handleCacheEvictError(RuntimeException e, Cache c, Object k) { warn("evict", c, e); }
            @Override public void handleCacheClearError(RuntimeException e, Cache c) { warn("clear", c, e); }

            private void warn(final String op, final Cache cache, final RuntimeException e) {
                // Sin claves, valores ni mensaje de la excepción: pueden traer host o datos personales.
                LOG.warn("Cache {} failed on '{}' ({}); continuing without cache",
                        op, cache.getName(), e.getClass().getSimpleName());
            }
        };
    }
}
