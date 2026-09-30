package com.jcaa.usersmanagement.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jcaa.usersmanagement.domain.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockFilterChain;
import org.mockito.Mockito;

class JwtAuthenticationFilterTest {

  private final JwtTokenService jwtTokenService = Mockito.mock(JwtTokenService.class);
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldAcceptBearerSchemeWithoutCaseSensitivity() throws Exception {
    // Arrange
    final MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, "bearer signed-token");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain filterChain = new MockFilterChain();
    when(jwtTokenService.parse("signed-token"))
        .thenReturn(new JwtPrincipal("user-123", UserRole.ADMIN));

    // Act
    filter.doFilter(request, response, filterChain);

    // Assert
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo("user-123");
    assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
        .extracting("authority")
        .containsExactly("ROLE_ADMIN");
    verify(jwtTokenService).parse("signed-token");
  }
}
