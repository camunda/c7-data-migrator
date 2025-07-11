/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.logging;

import io.camunda.migrator.impl.VariableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging and constants class for VariableService.
 * Contains all logging statements, error messages, and constants used in variable processing.
 */
public class VariableServiceLogs {

  static final Logger LOGGER = LoggerFactory.getLogger(VariableService.class);

  // Constants
  public static final String LEGACY_ID_VARIABLE = "legacyId";

  // Error message templates
  public static final String VARIABLE_INTERCEPTOR_FAILED_MSG = "Variable interceptor %s failed for variable %s";

  /**
   * Logs an error message for variable interceptor failure.
   *
   * @param interceptorName the name of the failed interceptor
   * @param variableName the name of the variable being processed
   * @param exception the exception that occurred
   */
  public static void logInterceptorError(String interceptorName, String variableName, Exception exception) {
    String errorMsg = String.format(VARIABLE_INTERCEPTOR_FAILED_MSG, interceptorName, variableName);
    LOGGER.error(errorMsg, exception);
  }

  /**
   * Creates a formatted error message for variable interceptor failure.
   *
   * @param interceptorName the interceptor class name
   * @param variableName the variable name
   * @return formatted error message
   */
  public static String formatInterceptorError(String interceptorName, String variableName) {
    return String.format(VARIABLE_INTERCEPTOR_FAILED_MSG, interceptorName, variableName);
  }

}
