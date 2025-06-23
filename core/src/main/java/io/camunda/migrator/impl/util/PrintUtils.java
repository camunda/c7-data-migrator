/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrintUtils {

  protected static final Logger PRINTER = LoggerFactory.getLogger("PRINTER");

  public static final String PREVIOUSLY_SKIPPED_INSTANCES_MESSAGE = "Previously skipped process instances:";
  public static final String NO_SKIPPED_INSTANCES_MESSAGE = "No process instances were skipped during previous migration";

  public static void printSkippedInstancesHeader(long count) {
    print(count > 0 ? PREVIOUSLY_SKIPPED_INSTANCES_MESSAGE : NO_SKIPPED_INSTANCES_MESSAGE);
  }

  public static void print(String message) {
    PRINTER.info(message);
  }
}