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
import java.text.SimpleDateFormat;
import java.util.Date;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DateVariableInterceptor implements VariableInterceptor {

  public static final SimpleDateFormat SIMPLE_DATE_FORMAT =
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
  protected static final Logger LOGGER = LoggerFactory.getLogger(DateVariableInterceptor.class);

  @Override
  public void execute(VariableInvocation invocation) throws Exception {
    VariableInstanceEntity variable = invocation.getVariable();
    TypedValue typedValue = variable.getTypedValue(false);

        ValueType type = typedValue.getType();
        LOGGER.info(type.toString());
    if (type.getName().equals("date")) {

      LOGGER.info(String.valueOf(type.getName().equals(ValueType.STRING.getName())));
      LOGGER.info("Date variable detected: {}", variable.getName());
      Date value = (Date) typedValue.getValue();
      LOGGER.debug(value.toString());

//      SIMPLE_DATE_FORMAT.setTimeZone(java.util.TimeZone.getTimeZone("UTC")); // TODO
             String newFormattedDate = SIMPLE_DATE_FORMAT.format(value);
//      variable.setValue(Variables.stringValue(newFormattedDate, false));
      invocation.setVariableValue(newFormattedDate);

      LOGGER.debug(value.toString());

    }
  }
}
