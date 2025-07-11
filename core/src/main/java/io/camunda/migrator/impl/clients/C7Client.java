/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.clients;

import static io.camunda.migrator.impl.util.ExceptionUtils.callApi;
import static io.camunda.migrator.impl.logging.RuntimeMigratorLogs.FAILED_TO_FETCH_ACTIVITY_INSTANCE;
import static io.camunda.migrator.impl.logging.RuntimeMigratorLogs.PROCESS_INSTANCE_FETCHING_FAILED;

import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.Pagination;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.VariableInstance;
import org.camunda.bpm.engine.runtime.VariableInstanceQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class C7Client {

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected HistoryService historyService;

  @Autowired
  protected MigratorProperties properties;

  @Autowired
  protected ApplicationContext context;

  /**
   * Gets a single process instance by ID.
   */
  public ProcessInstance getProcessInstance(String processInstanceId) {
    var query = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId);
    return callApi(query::singleResult, PROCESS_INSTANCE_FETCHING_FAILED + processInstanceId);
  }

  /**
   * Gets the activity instance tree for a process instance.
   */
  public ActivityInstance getActivityInstance(String processInstanceId) {
    return callApi(() -> runtimeService.getActivityInstance(processInstanceId),
        FAILED_TO_FETCH_ACTIVITY_INSTANCE + processInstanceId);
  }

  /**
   * Gets all variables for a process instance with pagination and variable transformation.
   */
  public List<VariableInstance> getAllVariables(String legacyProcessInstanceId) {
    VariableInstanceQuery variableQuery = runtimeService.createVariableInstanceQuery()
        .processInstanceIdIn(legacyProcessInstanceId);

    return new Pagination<VariableInstance>()
        .pageSize(properties.getPageSize())
        .query(variableQuery)
        .toList();
  }

  /**
   * Gets local variables for an activity instance with pagination and variable transformation.
   */
  public List<VariableInstance> getLocalVariables(String activityInstanceId) {
    VariableInstanceQuery variableQuery = runtimeService.createVariableInstanceQuery()
        .activityInstanceIdIn(activityInstanceId);

    return new Pagination<VariableInstance>()
        .pageSize(properties.getPageSize())
        .query(variableQuery)
        .toList();
  }

  /**
   * Processes historic process instances with pagination using the provided query.
   */
  public void fetch(Consumer<IdKeyDbModel> callback, Date startedAfter) {
    var query = historyService.createHistoricProcessInstanceQuery()
        .startedAfter(startedAfter)
        .rootProcessInstances()
        .unfinished()
        .orderByProcessInstanceStartTime()
        .asc()
        // Ensure order is predictable with two order criteria:
        // Without second criteria and PIs have same start time, order is non-deterministic.
        .orderByProcessInstanceId()
        .asc();

    new Pagination<IdKeyDbModel>()
        .pageSize(properties.getPageSize())
        .maxCount(query::count)
        .page(offset -> query.listPage(offset, properties.getPageSize())
            .stream()
            .map(hpi -> new IdKeyDbModel(hpi.getId(), hpi.getStartTime()))
            .collect(Collectors.toList()))
        .callback(callback);
  }

}
