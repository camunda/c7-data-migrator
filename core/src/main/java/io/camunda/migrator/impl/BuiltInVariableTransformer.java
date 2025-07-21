/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl;

import static org.camunda.bpm.engine.variable.Variables.SerializationDataFormats.*;
import static io.camunda.migrator.impl.logging.VariableServiceLogs.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.migrator.exception.VariableInterceptorException;
import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import io.camunda.migrator.impl.logging.VariableServiceLogs;
import java.util.Map;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.type.FileValueType;
import org.camunda.bpm.engine.variable.impl.value.ObjectValueImpl;
import org.camunda.bpm.engine.variable.type.PrimitiveValueType;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.camunda.spin.plugin.variable.type.SpinValueType;
import org.springframework.beans.factory.annotation.Autowired;
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
public class BuiltInVariableTransformer implements VariableInterceptor {

  @Autowired
  protected ObjectMapper objectMapper;

  @Override
  public void execute(VariableInvocation invocation) {
    VariableServiceLogs.logStartExecution(this.getClass(), invocation.getC7Variable().getName());
    VariableInstanceEntity variable = invocation.getC7Variable();
    TypedValue typedValue = variable.getTypedValue(false);

    if (ValueType.OBJECT.equals(typedValue.getType()) &&
        JSON.getName().equals(((ObjectValueImpl)typedValue).getSerializationDataFormat())) {
      // For JSON Object, convert the value into a C8 compatible Map format
      setJsonVariable(invocation, ((ObjectValueImpl)typedValue).getValueSerialized());

    } else if (SpinValueType.JSON.equals(typedValue.getType())) {
      // For Spin JSON, convert the value into a C8 compatible Map format
      setJsonVariable(invocation, typedValue.getValue().toString());

    } else if (SpinValueType.XML.equals(typedValue.getType())) {
      // For Spin XML, explicitly set the string value
      invocation.setVariableValue(typedValue.getValue().toString());

    } else if (typedValue.getType() instanceof PrimitiveValueType) {
      if (typedValue.getValue() instanceof byte[]) {
        throw new VariableInterceptorException(BYTE_ARRAY_UNSUPPORTED_ERROR, null);
      }

      invocation.setVariableValue(variable.getValue());

    } else if (ValueType.OBJECT.equals(typedValue.getType()) &&
        XML.getName().equals(((ObjectValueImpl)typedValue).getSerializationDataFormat())) {
      // Store raw string for unsupported types
      invocation.setVariableValue(((ObjectValueImpl)typedValue).getValueSerialized());

    } else if (typedValue.getType() instanceof FileValueType) {
      throw new VariableInterceptorException(FILE_TYPE_UNSUPPORTED_ERROR, null);

    } else if (ValueType.OBJECT.equals(typedValue.getType()) &&
        JAVA.getName().equals(((ObjectValueImpl)typedValue).getSerializationDataFormat())) {
      throw new VariableInterceptorException(JAVA_SERIALIZED_UNSUPPORTED_ERROR, null);

    } else {
      throw new VariableInterceptorException(String.format(GENERIC_TYPE_UNSUPPORTED_ERROR, typedValue.getType().toString()), null);

    }
    VariableServiceLogs.logEndExecution(this.getClass(), invocation.getC7Variable().getName());
  }

  protected void setJsonVariable(VariableInvocation invocation, String jsonString) {
    try {
      invocation.setVariableValue(objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {}));
    } catch (JsonProcessingException e) {
      throw new VariableInterceptorException(JSON_DESERIALIZATION_ERROR, e);
    }
  }

}
