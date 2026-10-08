package com.jcaa.usersmanagement;

import com.jcaa.notificationworker.NotificationWorkerApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {

  public static void main(String[] args) {
    if (isNotificationWorker(args)) {
      final SpringApplication workerApplication =
          new SpringApplication(NotificationWorkerApplication.class);
      workerApplication.setWebApplicationType(WebApplicationType.NONE);
      workerApplication.run(args);
      return;
    }
    SpringApplication.run(Main.class, args);
  }

  private static boolean isNotificationWorker(final String[] args) {
    String runtimeRole = System.getenv("APP_RUNTIME_ROLE");
    if (runtimeRole == null || runtimeRole.isBlank()) {
      runtimeRole = System.getProperty("app.runtime.role", "api");
    }
    for (int index = 0; index < args.length; index++) {
      if (args[index].startsWith("--app.runtime.role=")) {
        runtimeRole = args[index].substring("--app.runtime.role=".length());
      } else if ("--app.runtime.role".equals(args[index]) && index + 1 < args.length) {
        runtimeRole = args[index + 1];
      }
    }
    return "notification-worker".equalsIgnoreCase(runtimeRole);
  }
}
