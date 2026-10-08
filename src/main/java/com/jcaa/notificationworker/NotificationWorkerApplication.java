package com.jcaa.notificationworker;

import com.jcaa.usersmanagement.infrastructure.config.KafkaMessagingConfiguration;
import com.jcaa.usersmanagement.infrastructure.config.SmtpSpringConfig;
import com.jcaa.usersmanagement.infrastructure.messaging.kafka.KafkaNotificationWorker;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/** Minimal Spring context for the independently deployable notification worker process. */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({SmtpSpringConfig.class, KafkaMessagingConfiguration.class, KafkaNotificationWorker.class})
public class NotificationWorkerApplication {}
