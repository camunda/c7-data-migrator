/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl;

import static io.camunda.migrator.impl.logging.VariableServiceLogs.*;

import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import io.camunda.migrator.impl.logging.VariableServiceLogs;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Built-in implementation of {@link VariableInterceptor} that handles Date variable processing
 * during migration from Camunda 7 to Camunda 8.
 * <p>
 * Converts Date variables to ISO 8601 formatted strings compatible with Camunda 8.
 * The interceptor is ordered with priority 10 to ensure it runs after the default transformer.
 */
@Order(10)
@Component
public class BuiltInDateVariableTransformer implements VariableInterceptor {

  @Override
  public void execute(VariableInvocation invocation) {
    VariableInstanceEntity variable = invocation.getC7Variable();
    TypedValue typedValue = variable.getTypedValue(false);

    if (typedValue.getValue() instanceof Date value) {
      VariableServiceLogs.logConvertingDate(variable.getName());

      String formattedDate = new SimpleDateFormat(DATE_FORMAT_PATTERN).format(value);
      VariableServiceLogs.logConvertedDate(value, formattedDate);
      invocation.setVariableValue(formattedDate);
    }

  }
}