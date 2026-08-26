package com.jcaa.usersmanagement.infrastructure.entrypoint.rest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class HealthRestController {

  private static final String STATUS_UP = "UP";

  @GetMapping({"/", "/health"})
  public HealthResponse health() {
    return new HealthResponse(STATUS_UP);
  }

  public record HealthResponse(String status) {
  }
}
