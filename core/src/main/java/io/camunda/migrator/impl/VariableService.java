/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.migrator.exception.VariableInterceptorException;
import io.camunda.migrator.impl.clients.C7Client;
import io.camunda.migrator.impl.clients.C8Client;
import io.camunda.migrator.impl.model.ActivityVariables;
import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import io.camunda.migrator.impl.logging.VariableServiceLogs;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.runtime.VariableInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Centralized service for handling all variable-related operations in the RuntimeMigrator.
 * This service consolidates variable retrieval, transformation, and management logic
 * that was previously scattered across multiple classes.
 */
@Service
public class VariableService {

  @Autowired
  private C7Client c7Client;

  @Autowired
  private C8Client c8Client;

  @Autowired(required = false)
  protected List<VariableInterceptor> configuredVariableInterceptors;

  /**
   * Retrieves and processes all variables for a process instance, including global variables
   * with the legacy ID added.
   *
   * @param legacyProcessInstanceId the legacy process instance ID
   * @return processed global variables ready for C8 process instance creation
   */
  public Map<String, Object> getGlobalVariables(String legacyProcessInstanceId) {
    ActivityVariables activityVariables = processVariablesToActivityGroups(c7Client.getAllVariables(legacyProcessInstanceId));
    Map<String, Object> globalVariables = activityVariables.getGlobalVariables(legacyProcessInstanceId);

    // Add legacy ID for tracking purposes
    globalVariables.put(VariableServiceLogs.LEGACY_ID_VARIABLE, legacyProcessInstanceId);

    return globalVariables;
  }

  /**
   * Retrieves and processes local variables for an activity instance.
   *
   * @param activityInstanceId the activity instance ID
   * @param subProcessInstanceId optional subprocess instance ID to include as legacy ID
   * @return processed local variables
   */
  public Map<String, Object> getLocalVariables(String activityInstanceId,
                                               String subProcessInstanceId) {
    Map<String, Object> localVariables = processVariablesToMapSingleActivity(c7Client.getLocalVariables(activityInstanceId));

    // Add legacy ID for subprocess tracking if present
    if (subProcessInstanceId != null) {
      localVariables.put(VariableServiceLogs.LEGACY_ID_VARIABLE, subProcessInstanceId);
    }

    return localVariables;
  }

  /**
   * Checks if a job was started externally (not through migration) by verifying
   * the presence of the legacy ID variable.
   *
   * @param job the activated job to check
   * @return true if the job was started externally, false if it's a migrated job
   */
  public boolean isExternallyStartedJob(ActivatedJob job) {
    return !job.getVariables().contains(VariableServiceLogs.LEGACY_ID_VARIABLE);
  }

  /**
   * Retrieves the legacy ID from a job's variables.
   *
   * @param job the activated job
   * @return the legacy ID from the job variables
   */
  public String getLegacyIdFromJob(ActivatedJob job) {
    return (String) c8Client.getJobVariable(job, VariableServiceLogs.LEGACY_ID_VARIABLE);
  }

  /**
   * Checks if there are any configured variable interceptors.
   *
   * @return true if interceptors are configured, false otherwise
   */
  private boolean hasInterceptors() {
    return configuredVariableInterceptors != null && !configuredVariableInterceptors.isEmpty();
  }

  /**
   * Processes a list of variable instances and converts them to ActivityVariables record.
   * This provides a more readable way to work with variables grouped by activity.
   *
   * @param variables the list of variable instances to process
   * @return ActivityVariables containing variables grouped by activity instance
   */
  public ActivityVariables processVariablesToActivityGroups(List<VariableInstance> variables) {
    Map<String, Map<String, Object>> result = new HashMap<>();

    for (VariableInstance variable : variables) {
      VariableInvocation variableInvocation = new VariableInvocation((VariableInstanceEntity) variable);
      executeInterceptors(variableInvocation);

      String activityInstanceId = variable.getActivityInstanceId();
      Map<String, Object> variableMap = result.computeIfAbsent(activityInstanceId, k -> new HashMap<>());
      variableMap.put(variableInvocation.getMigrationVariable().getName(), variableInvocation.getMigrationVariable().getValue());
    }

    return new ActivityVariables(result);
  }

  /**
   * Processes a list of variable instances and converts them to a single variable map.
   * This method applies all configured interceptors during processing.
   *
   * @param variables the list of variable instances to process
   * @return map of variable name to value pairs
   */
  public Map<String, Object> processVariablesToMapSingleActivity(List<VariableInstance> variables) {
    Map<String, Object> variableResult = new HashMap<>();

    for (VariableInstance variable : variables) {
      VariableInvocation variableInvocation = new VariableInvocation((VariableInstanceEntity) variable);
      executeInterceptors(variableInvocation);

      variableResult.put(variableInvocation.getMigrationVariable().getName(),
          variableInvocation.getMigrationVariable().getValue());
    }

    return variableResult;
  }

  /**
   * Executes all configured variable interceptors on the given variable invocation.
   *
   * @param variableInvocation the variable invocation to process
   * @throws VariableInterceptorException if any interceptor fails
   */
  private void executeInterceptors(VariableInvocation variableInvocation) {
    if (hasInterceptors()) {
      for (VariableInterceptor interceptor : configuredVariableInterceptors) {
        try {
          interceptor.execute(variableInvocation);
        } catch (Exception ex) {
          String interceptorName = interceptor.getClass().getSimpleName();
          String variableName = variableInvocation.getC7Variable().getName();
          VariableServiceLogs.logInterceptorWarn(interceptorName, variableName);

          if (ex instanceof VariableInterceptorException) {
            throw ex;

          } else {
            throw new VariableInterceptorException(VariableServiceLogs.formatInterceptorWarn(interceptorName, variableName), ex);
          }
        }
      }
    }
  }

}
