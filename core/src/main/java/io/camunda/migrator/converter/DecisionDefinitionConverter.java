/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.converter;

import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import org.camunda.bpm.engine.repository.DecisionDefinition;

import static io.camunda.migrator.impl.util.ConverterUtil.getNextKey;

public class DecisionDefinitionConverter {

  public DecisionDefinitionDbModel apply(DecisionDefinition legacyDecisionDefinition) {

    return new DecisionDefinitionDbModel.DecisionDefinitionDbModelBuilder()
        .decisionDefinitionKey(getNextKey())
        .name(legacyDecisionDefinition.getName())
        .decisionDefinitionId(legacyDecisionDefinition.getId())
        .tenantId(legacyDecisionDefinition.getTenantId())
        .version(legacyDecisionDefinition.getVersion())
        .decisionRequirementsId(legacyDecisionDefinition.getDecisionRequirementsDefinitionId())
        .decisionRequirementsKey(null) //TODO C7 decision requirement key is String ?
        .build();
  }

}
