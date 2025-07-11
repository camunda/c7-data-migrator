/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.config;

import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.utils.InterceptorHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for managing interceptors from both Spring context and YAML.
 */
@Configuration
public class InterceptorConfiguration {

  protected static final Logger LOGGER = LoggerFactory.getLogger(InterceptorConfiguration.class);

  @Autowired
  protected ApplicationContext context;

  @Autowired
  protected MigratorProperties migratorProperties;

  /**
   * Creates a composite list of variable interceptors from both Spring context and YAML configuration.
   *
   * @return List of configured variable interceptors
   */
  @Bean
  public List<VariableInterceptor> configuredVariableInterceptors() {
    LOGGER.info("Configuring variable interceptors from Spring context and YAML");

    // Get interceptors from Spring context (annotated with @Component)
    List<VariableInterceptor> contextInterceptors = new ArrayList<>(
        context.getBeansOfType(VariableInterceptor.class).values());

    // Add interceptors from YAML configuration
    InterceptorHelper.registerYamlInterceptors(contextInterceptors, migratorProperties.getInterceptors());

    // Sort by order annotation if present
    AnnotationAwareOrderComparator.sort(contextInterceptors);

    LOGGER.info("Total {} variable interceptors configured", contextInterceptors.size());
    return contextInterceptors;
  }
}
