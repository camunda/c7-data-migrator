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
    String entityName = getEntityDisplayName(entityType);
    String message = count > 0
        ? "Previously skipped " + entityName + ":"
        : "No " + entityName + " were skipped during previous migration";
    print(message);
  }

  // Keep the old method for backward compatibility
  public static void printSkippedInstancesHeader(long count) {
    printSkippedInstancesHeader(count, TYPE.RUNTIME_PROCESS_INSTANCE);
  }

  private static String getEntityDisplayName(TYPE type) {
    return switch (type) {
      case HISTORY_PROCESS_DEFINITION -> "process definitions";
      case HISTORY_PROCESS_INSTANCE -> "process instances";
      case HISTORY_FLOW_NODE -> "flow nodes";
      case HISTORY_USER_TASK -> "user tasks";
      case HISTORY_VARIABLE -> "variables";
      case HISTORY_INCIDENT -> "incidents";
      case HISTORY_DECISION_DEFINITION -> "decision definitions";
      case HISTORY_DECISION_INSTANCE -> "decision instances";
      case RUNTIME_PROCESS_INSTANCE -> "process instances";
    };
  }

  public static void print(String message) {
    PRINTER.info(message);
  }
}