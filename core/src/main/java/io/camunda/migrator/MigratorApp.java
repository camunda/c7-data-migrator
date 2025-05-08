/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class
})
public class MigratorApp {

  private static final Logger LOGGER = LoggerFactory.getLogger(MigratorApp.class);

  public static void main(String[] args) {
    ConfigurableApplicationContext context = SpringApplication.run(MigratorApp.class, args);
    ApplicationArguments appArgs = new DefaultApplicationArguments(args);
    try {
      if (appArgs.containsOption("runtime")) {
        migrateRuntime(context);
      } else if (appArgs.containsOption("history")) {
        migrateHistory(context);
      } else {
        LOGGER.info("Migrating both runtime and history.");
        migrateRuntime(context);
        migrateHistory(context);
      }
    } finally {
      SpringApplication.exit(context);
    }
  }

  public static void migrateRuntime(ConfigurableApplicationContext context) {
    LOGGER.info("Migrating runtime data...");
    RuntimeMigrator runtimeMigrator = context.getBean(RuntimeMigrator.class);
    runtimeMigrator.migrate();
  }

  public static void migrateHistory(ConfigurableApplicationContext context) {
    LOGGER.info("Migrating history data...");
    HistoryMigrator historyMigrator = context.getBean(HistoryMigrator.class);
    historyMigrator.migrate();
  }
}
