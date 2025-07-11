/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package com.example;

import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example variable interceptor that can be packaged as a JAR
 * and configured via YAML without Spring Boot annotations.
 *
 * This demonstrates:
 * - How to create a standalone interceptor without @Component
 * - How to handle configurable properties
 * - How to perform variable transformations
 */
public class MyCustomVariableInterceptor implements VariableInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyCustomVariableInterceptor.class);

    // Configurable properties that can be set via YAML
    private boolean enableLogging = true;
    private String targetVariableType = "String";

    @Override
    public void execute(VariableInvocation invocation) throws Exception {
        if (enableLogging) {
            LOGGER.info("Processing variable: {} with value: {}",
                invocation.getC7Variable().getName(),
                invocation.getC7Variable().getValue());
        }

        // Example transformation: convert value to string if specified
        if ("String".equals(targetVariableType)) {
            Object originalValue = invocation.getMigrationVariable().getValue();
            if (originalValue != null) {
                String stringValue = originalValue.toString();
                invocation.setVariableValue(stringValue);

                if (enableLogging) {
                    LOGGER.info("Converted variable {} from {} to String: {}",
                        invocation.getC7Variable().getName(), invocation.getC7Variable().getValue(), stringValue);
                }
            }
        }

        if (enableLogging) {
            LOGGER.info("Finished processing variable: {} with transformed value: {}",
                invocation.getMigrationVariable().getName(),
                invocation.getMigrationVariable().getValue());
        }
    }

    // Setter methods for YAML configuration
    public void setEnableLogging(boolean enableLogging) {
        this.enableLogging = enableLogging;
    }

    public void setTargetVariableType(String targetVariableType) {
        this.targetVariableType = targetVariableType;
    }

    // Getter methods
    public boolean isEnableLogging() {
        return enableLogging;
    }

    public String getTargetVariableType() {
        return targetVariableType;
    }
}
