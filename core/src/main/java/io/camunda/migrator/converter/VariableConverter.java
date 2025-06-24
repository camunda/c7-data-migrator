/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.variable.impl.value.NullValueImpl;
import org.camunda.bpm.engine.variable.impl.value.ObjectValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import static io.camunda.migrator.impl.util.ConverterUtil.getNextKey;

public class VariableConverter {

  private static final Logger LOGGER = LoggerFactory.getLogger(VariableConverter.class);

  @Autowired
  private ObjectMapper objectMapper;

  public VariableDbModel apply(HistoricVariableInstance historicVariable, Long processInstanceKey, Long scopeKey) {
    // TODO currently the VariableDbModelBuilder maps all variables to String type
    return new VariableDbModel.VariableDbModelBuilder()
        .variableKey(getNextKey())
        .name(historicVariable.getName())
        .value(convertValue(historicVariable)) //TODO ?
        .scopeKey(scopeKey) //TODO ?
        .processInstanceKey(processInstanceKey)
        .processDefinitionId(historicVariable.getProcessDefinitionKey())
        .tenantId(historicVariable.getTenantId())
        .build(); //FIXME boolean values should be mapped to boolean by rdbms. Update version and fix
  }

  private String convertValue(HistoricVariableInstance variable) {
    var variableId = variable.getId();

    if (isNullValueType(variable)) {
      LOGGER.info("Converting variable id={} of type: NullValue", variableId);
      return null;
    }

    if (isPrimitiveType(variable)) {
      LOGGER.info("Converting variable id={} of type: Primitive", variableId);
      var typedValue = variable.getTypedValue().getValue();

      return typedValue != null ? typedValue.toString() : null;
    }

    if (isObjectType(variable)) {
      ObjectValueImpl typedValue = (ObjectValueImpl) (variable.getTypedValue());
      Class<?> objectType = typedValue.getObjectType();
      LOGGER.info("Converting variable id={} of type: {}", variableId, objectType.getSimpleName());

      return getJsonValue(typedValue);
    }

    LOGGER.warn("No existing handling for variable with id= {}, type: {}, returning null.", variableId, "unknown"/*variable.getTypeName()*/);
    return null;
  }

  private boolean isNullValueType(HistoricVariableInstance variable) {
    return variable.getTypedValue() instanceof NullValueImpl;
  }

  private boolean isObjectType(HistoricVariableInstance variable) {
    return variable.getTypedValue() instanceof ObjectValueImpl;
  }

  private boolean isPrimitiveType(HistoricVariableInstance variable) {
    return variable.getTypedValue() instanceof PrimitiveTypeValueImpl;
  }

  private String getJsonValue(ObjectValueImpl typedValue) {
    try {
      return objectMapper.writeValueAsString(typedValue.getValue());
    } catch (JsonProcessingException e) {
      LOGGER.error("Error converting typed value to json: {}, exception: {}. Mapped to null", typedValue, e.getMessage());
      return null;
    }
  }


}
