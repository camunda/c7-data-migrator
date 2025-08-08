/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import static io.camunda.migrator.MigratorMode.LIST_SKIPPED;
import static io.camunda.migrator.MigratorMode.MIGRATE;
import static io.camunda.migrator.MigratorMode.RETRY_SKIPPED;

import io.camunda.migrator.impl.logging.RuntimeMigratorLogs;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.exception.VariableInterceptorException;
import io.camunda.migrator.impl.clients.C7Client;
import io.camunda.migrator.impl.clients.C8Client;
import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.impl.util.C7Utils;
import io.camunda.migrator.impl.util.ExceptionUtils;
import io.camunda.migrator.impl.util.PrintUtils;
import io.camunda.migrator.impl.model.FlowNode;
import io.camunda.migrator.impl.model.FlowNodeActivation;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.impl.VariableService;
import io.camunda.migrator.impl.RuntimeValidator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RuntimeMigrator {

  @Autowired
  protected C7Client c7Client;

  @Autowired
  protected C8Client c8Client;

  @Autowired
  protected DbClient dbClient;

  @Autowired
  protected VariableService variableService;

  @Autowired
  protected RuntimeValidator runtimeValidator;

  @Autowired
  protected MigratorProperties migratorProperties;

  protected MigratorMode mode = MIGRATE;

  public void start() {
    try {
      ExceptionUtils.setContext(ExceptionUtils.ExceptionContext.RUNTIME);
      if (LIST_SKIPPED.equals(mode)) {
        PrintUtils.printSkippedInstancesHeader(dbClient.countSkippedByType(TYPE.RUNTIME_PROCESS_INSTANCE));
        dbClient.listSkippedRuntimeProcessInstances();
      } else {
        migrate();
      }
    } finally {
      ExceptionUtils.clearContext();
    }
  }

  protected void migrate() {
    fetchProcessInstancesToMigrate(legacyProcessInstance -> {
      String legacyProcessInstanceId = legacyProcessInstance.id();
      Date startDate = legacyProcessInstance.startDate();

      if (shouldStartProcessInstance(legacyProcessInstanceId)) {
        startProcessInstance(legacyProcessInstanceId, startDate);

      } else if (isUnknown(legacyProcessInstanceId)) {
        dbClient.insert(legacyProcessInstanceId, startDate, null, TYPE.RUNTIME_PROCESS_INSTANCE);
      }
    });

    activateMigratorJobs();
  }

  protected boolean shouldStartProcessInstance(String legacyProcessInstanceId) {
    if (skipProcessInstance(legacyProcessInstanceId)) {
      return false;
    }

    return RETRY_SKIPPED.equals(mode) || isUnknown(legacyProcessInstanceId);
  }

  protected boolean isUnknown(String legacyProcessInstanceId) {
    return MIGRATE.equals(mode) && !dbClient.checkExists(legacyProcessInstanceId);
  }

  protected void startProcessInstance(String legacyProcessInstanceId, Date startDate) {
    RuntimeMigratorLogs.startingNewC8ProcessInstance(legacyProcessInstanceId);

    try {
      Long processInstanceKey = startNewProcessInstance(legacyProcessInstanceId);
      RuntimeMigratorLogs.startedC8ProcessInstance(processInstanceKey);

      if (processInstanceKey != null) {
        saveRecord(legacyProcessInstanceId, startDate, processInstanceKey);
      }
    } catch (VariableInterceptorException e) {
      handleVariableInterceptorException(e, legacyProcessInstanceId, startDate);
    }
  }

  protected void handleVariableInterceptorException(VariableInterceptorException e, String legacyProcessInstanceId, Date startDate) {
    RuntimeMigratorLogs.skippingProcessInstanceVariableError(legacyProcessInstanceId, e.getMessage());
    RuntimeMigratorLogs.stacktrace(e);

    if (MIGRATE.equals(mode)) {
      dbClient.insert(legacyProcessInstanceId, startDate, null, TYPE.RUNTIME_PROCESS_INSTANCE);
    }
  }

  protected void saveRecord(String legacyProcessInstanceId, Date startDate, Long processInstanceKey) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateKeyById(legacyProcessInstanceId, processInstanceKey, TYPE.RUNTIME_PROCESS_INSTANCE);
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(legacyProcessInstanceId, startDate, processInstanceKey, TYPE.RUNTIME_PROCESS_INSTANCE);
    }
  }

  protected boolean skipProcessInstance(String legacyProcessInstanceId) {
    try {
      runtimeValidator.validateProcessInstanceState(legacyProcessInstanceId);
    } catch (IllegalStateException e) {
      RuntimeMigratorLogs.skippingProcessInstanceValidationError(legacyProcessInstanceId, e.getMessage());
      return true;
    }

    return false;
  }

  protected void fetchProcessInstancesToMigrate(Consumer<IdKeyDbModel> storeMappingConsumer) {
    RuntimeMigratorLogs.fetchingProcessInstances();

    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.fetchAndHandleSkippedForType(TYPE.RUNTIME_PROCESS_INSTANCE, storeMappingConsumer);
    } else {
      RuntimeMigratorLogs.fetchingLatestStartDate();
      Date latestStartDate = dbClient.findLatestStartDateByType(TYPE.RUNTIME_PROCESS_INSTANCE);
      RuntimeMigratorLogs.latestStartDate(latestStartDate);

      c7Client.fetchAndHandleHistoricRootProcessInstances(storeMappingConsumer, latestStartDate);
    }
  }

  protected Long startNewProcessInstance(String legacyProcessInstanceId) throws VariableInterceptorException {
    var processInstance = c7Client.getProcessInstance(legacyProcessInstanceId);
    if (processInstance != null) {
      String bpmnProcessId = processInstance.getProcessDefinitionKey();
      String tenantId = processInstance.getTenantId();
      Map<String, Object> globalVariables = variableService.getGlobalVariables(legacyProcessInstanceId);
      return c8Client.createProcessInstance(bpmnProcessId, tenantId, globalVariables).getProcessInstanceKey();
    } else {
      RuntimeMigratorLogs.processInstanceNotExists(legacyProcessInstanceId);
      return null;
    }
  }

  protected void activateMigratorJobs() {
    RuntimeMigratorLogs.activatingMigratorJobs();
    List<ActivatedJob> migratorJobs;
    do {
      migratorJobs = c8Client.activateJobs(migratorProperties.getJobActivationType());

      RuntimeMigratorLogs.migratorJobsFound(migratorJobs.size());

      migratorJobs.forEach(job -> {
        boolean externallyStarted = variableService.isExternallyStartedJob(job);
        if (!externallyStarted) {
          String legacyId = variableService.getLegacyIdFromJob(job);
          var activityInstanceTree = c7Client.getActivityInstance(legacyId);

          RuntimeMigratorLogs.collectingActiveDescendantActivities(activityInstanceTree.getActivityId());
          Map<String, FlowNode> activityInstanceMap = C7Utils.getActiveActivityIdsById(activityInstanceTree, new HashMap<>());
          RuntimeMigratorLogs.foundActiveActivitiesToActivate(activityInstanceMap.size());

          List<FlowNodeActivation> flowNodeActivations = activityInstanceMap.entrySet().stream()
              .map(entry -> {
                String activityInstanceId = entry.getKey();
                FlowNode flowNode = entry.getValue();

                Map<String, Object> localVariables = variableService.getLocalVariables(activityInstanceId, flowNode.subProcessInstanceId());
                String activityId = flowNode.activityId();
                return new FlowNodeActivation(activityId, localVariables);
              })
              .collect(Collectors.toList());

          long processInstanceKey = job.getProcessInstanceKey();
          long elementInstanceKey = job.getElementInstanceKey();
          c8Client.modifyProcessInstance(processInstanceKey, elementInstanceKey, flowNodeActivations);
          // no need to complete the job since the modification canceled the migrator job in the start event
        } else {
          RuntimeMigratorLogs.externallyStartedProcessInstance(job.getProcessInstanceKey());
        }
      });

    } while (!migratorJobs.isEmpty());
  }

  public void setMode(MigratorMode mode) {
    this.mode = mode;
  }

}
