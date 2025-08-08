/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.config;

import io.camunda.migrator.config.property.InterceptorProperty;
import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.exception.MigratorException;
import io.camunda.migrator.impl.logging.ConfigurationLogs;
import io.camunda.migrator.interceptor.VariableInterceptor;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.handler.NoUnboundElementsBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for managing interceptors from both Spring context and config data files.
 */
@Configuration
public class InterceptorConfiguration {

  @Autowired
  protected ApplicationContext context;

  @Autowired
  protected MigratorProperties migratorProperties;

  /**
   * Creates a composite list of variable interceptors from both Spring context and config data files.
   *
   * @return List of configured variable interceptors
   */
  @Bean
  public List<VariableInterceptor> configuredVariableInterceptors() {
    ConfigurationLogs.logConfiguringInterceptors();

    // Get interceptors from Spring context (annotated with @Component)
    List<VariableInterceptor> contextInterceptors = new ArrayList<>(
        context.getBeansOfType(VariableInterceptor.class).values());

    // Add interceptors from configuration
    registerYamlInterceptors(contextInterceptors, migratorProperties.getInterceptors());

    // Sort by order annotation if present
    AnnotationAwareOrderComparator.sort(contextInterceptors);

    ConfigurationLogs.logTotalInterceptorsConfigured(contextInterceptors.size());
    return contextInterceptors;
  }

  /**
   * Creates variable interceptor instances from config data files and adds them to the context interceptors list.
   *
   * @param contextInterceptors List of interceptors discovered from Spring context
   * @param yamlInterceptors List of interceptor configurations from config data files
   */
  public void registerYamlInterceptors(List<VariableInterceptor> contextInterceptors,
                                              List<InterceptorProperty> yamlInterceptors) {
    if (yamlInterceptors == null || yamlInterceptors.isEmpty()) {
      ConfigurationLogs.logNoYamlInterceptors();
      return;
    }

    for (InterceptorProperty interceptorProperty : yamlInterceptors) {
      try {
        VariableInterceptor interceptor = createInterceptorInstance(interceptorProperty);
        contextInterceptors.add(interceptor);
        ConfigurationLogs.logSuccessfullyRegistered(interceptorProperty.getClassName());
      } catch (Exception e) {
        ConfigurationLogs.logFailedToRegister(interceptorProperty.getClassName(), e);
        throw new MigratorException(ConfigurationLogs.getFailedToRegisterError(interceptorProperty.getClassName()), e);
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
  protected VariableInterceptor createInterceptorInstance(InterceptorProperty interceptorProperty) throws Exception {
    String className = interceptorProperty.getClassName();
    if (className == null || className.trim().isEmpty()) {
      throw new IllegalArgumentException(ConfigurationLogs.getClassNameNullOrEmptyError());
    }

    ConfigurationLogs.logCreatingInstance(className);

    Class<?> clazz = Class.forName(className);
    if (!VariableInterceptor.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException(ConfigurationLogs.getClassNotImplementInterfaceError(className));
    }

    VariableInterceptor interceptor = (VariableInterceptor) clazz.getDeclaredConstructor().newInstance();

    // Set properties if provided
    Map<String, Object> properties = interceptorProperty.getProperties();
    if (properties != null && !properties.isEmpty()) {
      ConfigurationLogs.logSettingProperties(className);
      applyProperties(interceptor, properties, false);
    }

    return interceptor;
  }

  protected static <T> void applyProperties(T target, Map<String, Object> sourceMap, boolean ignoreUnknownFields) {
    ConfigurationPropertySource source = new MapConfigurationPropertySource(sourceMap);
    Binder binder = new Binder(source);
    try {
      if (ignoreUnknownFields) {
        binder.bind(ConfigurationPropertyName.EMPTY, Bindable.ofInstance(target));
      } else {
        binder.bind(ConfigurationPropertyName.EMPTY, Bindable.ofInstance(target),
            new NoUnboundElementsBindHandler(BindHandler.DEFAULT));
      }
    } catch (Exception e) {
      throw new MigratorException(ConfigurationLogs.getParsingConfigurationError(), e);
    }
  }

}
