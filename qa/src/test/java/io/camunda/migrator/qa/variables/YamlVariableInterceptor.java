/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.variables;

import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YamlVariableInterceptor implements VariableInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(YamlVariableInterceptor.class);

  // Configurable properties that can be set via YAML
  private String logMessage = "Hello from YAML interceptor configured via properties";
  private boolean enableTransformation = true;
  private String targetVariable = "yamlVar";

  @Override
  public void execute(VariableInvocation invocation) throws Exception {
    LOGGER.debug("Start {} execution for variable: {}", YamlVariableInterceptor.class,
        invocation.getC7Variable().getName());

    String variableName = invocation.getC7Variable().getName();

    if (targetVariable.equals(variableName)) {
      LOGGER.info(logMessage);

      if (enableTransformation) {
        String originalValue = String.valueOf(invocation.getC7Variable().getValue());
        String transformedValue = "transformedValue";
        invocation.setVariableValue(transformedValue);
        LOGGER.info("Transformed variable {} from '{}' to '{}'", variableName, originalValue, transformedValue);
      }
    }

    // Handle exception testing scenario
    if ("yamlExFlag".equals(variableName)) {
      if (Boolean.valueOf(invocation.getC7Variable().getValue().toString()) == true) {
        throw new RuntimeException("Expected exception from YAML interceptor");
      } else {
        LOGGER.info("Success from YAML interceptor");
      }
    }

    LOGGER.debug("End {} execution for variable: {}", YamlVariableInterceptor.class,
        invocation.getC7Variable().getName());
  }

  // Setter methods for YAML configuration
  public void setLogMessage(String logMessage) {
    this.logMessage = logMessage;
  }

  public void setEnableTransformation(boolean enableTransformation) {
    this.enableTransformation = enableTransformation;
  }

  public void setTargetVariable(String targetVariable) {
    this.targetVariable = targetVariable;
  }

  // Getter methods for testing
  public String getLogMessage() {
    return logMessage;
  }

  public boolean isEnableTransformation() {
    return enableTransformation;
  }

  public String getTargetVariable() {
    return targetVariable;
  }
}
