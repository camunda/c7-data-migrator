/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.app;

import io.camunda.migrator.HistoryMigrator;
import io.camunda.migrator.RuntimeMigrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MigratorApp {

  private static final Logger LOGGER = LoggerFactory.getLogger(MigratorApp.class);

  protected static final String RUN_HISTORY_MIGRATION = "history";
  protected static final String RUN_RUNTIME_MIGRATION = "runtime";

  public static void main(String[] args) {
    ConfigurableApplicationContext context = SpringApplication.run(MigratorApp.class, args);
    ApplicationArguments appArgs = new DefaultApplicationArguments(args);
    LOGGER.debug("Migrator started.");
    try {
      if (appArgs.containsOption(RUN_RUNTIME_MIGRATION)) {
        migrateRuntime(context);
      } else if (appArgs.containsOption(RUN_HISTORY_MIGRATION)) {
        migrateHistory(context);
      } else {
        LOGGER.debug("Migrating both runtime and history.");
        migrateRuntime(context);
        migrateHistory(context);
      }
      LOGGER.debug("Migration completed.");
    } catch (Exception e) {
      LOGGER.error("Migration failed.", e);
      throw e;
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
