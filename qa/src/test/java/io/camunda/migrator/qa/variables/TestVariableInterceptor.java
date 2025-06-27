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
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TestVariableInterceptor implements VariableInterceptor {

  protected static final Logger LOGGER = LoggerFactory.getLogger(TestVariableInterceptor.class);

  @Override
  public void execute(VariableInvocation invocation) throws Exception {
    TypedValue typedValue = invocation.getVariable().getTypedValue(false);

//    ValueType type = typedValue.getType();

    LOGGER.info("Hello from interceptor");

    if(invocation.getVariable().getName().equals("exFlag") && Boolean.valueOf(typedValue.getValue().toString()) == true){
      throw new RuntimeException("Expected exception from Interceptor");
    }
  }
}
