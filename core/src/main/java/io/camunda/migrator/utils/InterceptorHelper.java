/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.utils;

import io.camunda.migrator.config.property.InterceptorProperty;
import io.camunda.migrator.exception.MigratorException;
import io.camunda.migrator.interceptor.VariableInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.Map;

/**
 * Helper class for managing interceptors loaded from YAML configuration.
 * This allows interceptors to be packaged in JARs and configured via application.yml
 * without requiring Spring Boot annotations.
 */
public class InterceptorHelper {

  protected static final Logger LOGGER = LoggerFactory.getLogger(InterceptorHelper.class);

  private InterceptorHelper() {
    // Prevent instantiation
  }

  /**
   * Creates variable interceptor instances from YAML configuration and adds them to the context interceptors list.
   *
   * @param contextInterceptors List of interceptors discovered from Spring context
   * @param yamlInterceptors List of interceptor configurations from YAML
   */
  public static void registerYamlInterceptors(List<VariableInterceptor> contextInterceptors,
                                              List<InterceptorProperty> yamlInterceptors) {
    if (yamlInterceptors == null || yamlInterceptors.isEmpty()) {
      LOGGER.debug("No variable interceptors configured in YAML");
      return;
    }

    for (InterceptorProperty interceptorProperty : yamlInterceptors) {
      try {
        VariableInterceptor interceptor = createInterceptorInstance(interceptorProperty);
        contextInterceptors.add(interceptor);
        LOGGER.info("Successfully registered variable interceptor: {}", interceptorProperty.getClassName());
      } catch (Exception e) {
        LOGGER.error("Failed to register variable interceptor: {}", interceptorProperty.getClassName(), e);
        throw new MigratorException("Failed to register variable interceptor: " + interceptorProperty.getClassName(), e);
      }
    }
  }

  /**
   * Creates a variable interceptor instance from the configuration.
   *
   * @param interceptorProperty Interceptor configuration
   * @return VariableInterceptor instance
   * @throws Exception if instantiation fails
   */
  protected static VariableInterceptor createInterceptorInstance(InterceptorProperty interceptorProperty) throws Exception {
    String className = interceptorProperty.getClassName();
    if (className == null || className.trim().isEmpty()) {
      throw new IllegalArgumentException("Variable interceptor class name cannot be null or empty");
    }

    LOGGER.debug("Creating variable interceptor instance for class: {}", className);

    Class<?> clazz = Class.forName(className);
    if (!VariableInterceptor.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException("Class " + className + " does not implement VariableInterceptor interface");
    }

    VariableInterceptor interceptor = (VariableInterceptor) clazz.getDeclaredConstructor().newInstance();

    // Set properties if provided
    Map<String, Object> properties = interceptorProperty.getProperties();
    if (properties != null && !properties.isEmpty()) {
      LOGGER.debug("Setting properties for variable interceptor: {}", className);
      BeanUtils.copyProperties(properties, interceptor);
    }

    return interceptor;
  }
}
