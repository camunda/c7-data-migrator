/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.example;

import io.camunda.migrator.RuntimeMigrator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RuntimeExampleApplication {

  public static void main(String[] args) {
    var context = SpringApplication.run(RuntimeExampleApplication.class, args);

    try {
      RuntimeMigrator runtimeMigrator = context.getBean(RuntimeMigrator.class);
      runtimeMigrator.start();
    } finally {
      SpringApplication.exit(context);
    }

  }

}
