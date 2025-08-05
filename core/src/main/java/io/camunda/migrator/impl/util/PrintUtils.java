/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.util;

import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrintUtils {

  protected static final Logger PRINTER = LoggerFactory.getLogger("PRINTER");

  public static void printSkippedInstancesHeader(long count, TYPE entityType) {
    String entityName = entityType.getDisplayName();
    String message = count > 0
        ? "Previously skipped " + entityName + "s:"
        : "No " + entityName + "s were skipped during previous migration";
    print(message);
  }

  // Keep the old method for backward compatibility
  public static void printSkippedInstancesHeader(long count) {
    printSkippedInstancesHeader(count, TYPE.RUNTIME_PROCESS_INSTANCE);
  }


  public static void print(String message) {
    PRINTER.info(message);
  }
}