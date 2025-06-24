/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.converter;

import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import org.camunda.bpm.engine.history.HistoricProcessInstance;

import static io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import static io.camunda.migrator.impl.util.ConverterUtil.convertDate;
import static io.camunda.migrator.impl.util.ConverterUtil.getNextKey;
import static io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;

public class ProcessInstanceConverter {

  public ProcessInstanceDbModel apply(HistoricProcessInstance processInstance,
                                      Long processDefinitionKey,
                                      Long parentProcessInstanceKey) {
    return new ProcessInstanceDbModelBuilder()
        .processInstanceKey(getNextKey())
        // Get key from runtime instance/model migration
        .processDefinitionKey(processDefinitionKey)
        .processDefinitionId(processInstance.getProcessDefinitionKey())
        .startDate(convertDate(processInstance.getStartTime()))
        .endDate(convertDate(processInstance.getEndTime()))
        .state(convertState(processInstance.getState()))
        .tenantId(processInstance.getTenantId())
        .version(processInstance.getProcessDefinitionVersion())
        // parent and super process instance are used synonym (process instance that contained the call activity)
        .parentProcessInstanceKey(parentProcessInstanceKey)
        .elementId(null) // TODO: activityId in C7 but not part of the historic process instance. Not yet populated by RDBMS.
        .parentElementInstanceKey(null) // TODO: Call activity instance id that created the process. Not part of C7 historic process instance.
        .numIncidents(0) // TODO: Incremented/decremented whenever incident is created/resolved. RDBMS specific.
        .build();
  }

  private ProcessInstanceState convertState(String state) {
    return switch (state) {
      case "ACTIVE", "SUSPENDED" -> ProcessInstanceState.ACTIVE;
      case "COMPLETED" -> ProcessInstanceState.COMPLETED;
      case "EXTERNALLY_TERMINATED", "INTERNALLY_TERMINATED" -> ProcessInstanceState.CANCELED;

      default -> throw new IllegalArgumentException("Unknown state: " + state);
    };
  }
}
