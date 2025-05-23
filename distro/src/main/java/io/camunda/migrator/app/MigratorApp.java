/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.app;

import io.camunda.migrator.AutoDeployer;
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
  protected static final String RUN_RETRY_MIGRATION = "retry";

  public static void main(String[] args) {
    ConfigurableApplicationContext context = SpringApplication.run(MigratorApp.class, args);
    ApplicationArguments appArgs = new DefaultApplicationArguments(args);
    boolean retryMode = appArgs.containsOption(RUN_RETRY_MIGRATION);
    try {
      AutoDeployer autoDeployer = context.getBean(AutoDeployer.class);
      autoDeployer.deploy();
      if (shouldRunFullMigration(appArgs)) {
        LOGGER.info("Migrating both runtime and history");
        migrateRuntime(context, retryMode);
        migrateHistory(context, retryMode);
      }
      else if (appArgs.containsOption(RUN_RUNTIME_MIGRATION)) {
        migrateRuntime(context, retryMode);
      } else if (appArgs.containsOption(RUN_HISTORY_MIGRATION)) {
        migrateHistory(context, retryMode);
      } else {
        LOGGER.warn("Invalid argument combination");
      }
    } finally {
      SpringApplication.exit(context);
    }
  }

  public static void migrateRuntime(ConfigurableApplicationContext context, boolean retryMode) {
    LOGGER.info("Migrating runtime data...");
    RuntimeMigrator runtimeMigrator = context.getBean(RuntimeMigrator.class);
    runtimeMigrator.setRetryMode(retryMode);
    runtimeMigrator.migrate();
  }

  public static void migrateHistory(ConfigurableApplicationContext context, boolean retryMode) {
    if (retryMode) {
      LOGGER.warn("Retrying history migration is not implemented yet, history migration will not be executed");
      return;
    }
    LOGGER.info("Migrating history data...");
    HistoryMigrator historyMigrator = context.getBean(HistoryMigrator.class);
    historyMigrator.migrate();
  }

  private static boolean shouldRunFullMigration(ApplicationArguments appArgs) {
    // Return true either when both --runtime and --history are present or when neither is present
    return (appArgs.containsOption(RUN_RUNTIME_MIGRATION) && appArgs.containsOption(RUN_HISTORY_MIGRATION)) ||
        (!appArgs.containsOption(RUN_RUNTIME_MIGRATION) && !appArgs.containsOption(RUN_HISTORY_MIGRATION));
  }
}
