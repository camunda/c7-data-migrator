/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl;

import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.camunda.spin.plugin.variable.type.SpinValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link VariableInterceptor} that handles variable processing
 * during migration from Camunda 7 to Camunda 8.
 * <p>
 * The interceptor is ordered with priority 0 to ensure it runs first among interceptors.
 */
@Order(0)
@Component
public class DefaultVariableInterceptor implements VariableInterceptor {

  protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultVariableInterceptor.class);

  @Override
  public void execute(VariableInvocation invocation) throws Exception {
    LOGGER.debug("Start {} execution for variable: {}", DefaultVariableInterceptor.class,
        invocation.getC7Variable().getName());
    VariableInstanceEntity variable = invocation.getC7Variable();
    TypedValue typedValue = variable.getTypedValue(false);
    if (typedValue.getType().equals(ValueType.OBJECT)) {
      // skip the value deserialization
      invocation.setVariableValue(typedValue.getValue());
    } else if (typedValue.getType().equals(SpinValueType.JSON) || typedValue.getType().equals(SpinValueType.XML)) {
      // For Spin JSON/XML, explicitly set the string value
      invocation.setVariableValue(typedValue.getValue().toString());
    } else {
      invocation.setVariableValue(variable.getValue());
    }
    LOGGER.debug("End {} execution for variable: {}", DefaultVariableInterceptor.class,
        invocation.getC7Variable().getName());
  }

}
