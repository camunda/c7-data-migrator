/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import static io.camunda.migrator.ExceptionUtils.callApi;
import static io.camunda.migrator.persistence.IdKeyMapper.TYPE;
import static io.camunda.migrator.MigratorMode.LIST_SKIPPED;
import static io.camunda.migrator.MigratorMode.MIGRATE;
import static io.camunda.migrator.MigratorMode.RETRY_SKIPPED;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.ProcessDefinition;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import io.camunda.migrator.persistence.IdKeyDbModel;
import io.camunda.migrator.persistence.IdKeyMapper;
import java.util.Date;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricProcessInstanceQuery;
import org.camunda.bpm.engine.impl.persistence.entity.ActivityInstanceImpl;
import org.camunda.bpm.engine.impl.persistence.entity.TransitionInstanceImpl;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
import org.camunda.bpm.engine.runtime.VariableInstance;
import org.camunda.bpm.engine.runtime.VariableInstanceQuery;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core component responsible for migrating Camunda 7 runtime process instances to Camunda 8.
 * Handles process instance migration, variable mapping, and state synchronization.
 *
 * @since 0.1.0
 */
@Component
public class RuntimeMigrator {

  private static final int DEFAULT_BATCH_SIZE = 500;

  // Activity types
  private static final String ACTIVITY_TYPE_SUBPROCESS = "subProcess";
  private static final String ACTIVITY_TYPE_MIGRATOR = "migrator";

  // Variable names
  private static final String VAR_LEGACY_ID = "legacyId";

  @Autowired
  protected RepositoryService repositoryService;

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected HistoryService historyService;

  @Autowired
  protected IdKeyMapper idKeyMapper;

  @Autowired
  protected CamundaClient camundaClient;

  @Value("${migrator.batch-size:" + DEFAULT_BATCH_SIZE + "}")
  protected int batchSize;

  protected MigratorMode mode = MIGRATE;

  public void start() {
    if (LIST_SKIPPED.equals(mode)) {
      PrintUtils.printSkippedInstancesHeader(idKeyMapper.findSkippedCount());
      listSkippedProcessInstances();
    } else {
      migrate();
    }
  }

  public void migrate() {
    fetchProcessInstancesToMigrate(legacyProcessInstance -> {
      String legacyProcessInstanceId = legacyProcessInstance.id();
      Date startDate = legacyProcessInstance.startDate();
      if (skipProcessInstance(legacyProcessInstanceId)) {
        MigratorLogger.infoSkippingProcessInstance(legacyProcessInstanceId);
        if (!legacyProcessInstance.skippedPreviously()) {
          storeMapping(legacyProcessInstanceId, startDate, null);
        }
      } else if (legacyProcessInstance.skippedPreviously() || !idKeyMapper.checkExists(legacyProcessInstanceId)) {
        MigratorLogger.debugStartingNewProcessInstance(legacyProcessInstanceId);
        Long processInstanceKey = startNewProcessInstance(legacyProcessInstanceId);
        MigratorLogger.debugStartedProcessInstance(processInstanceKey);
        if (processInstanceKey != null) {
          storeMapping(legacyProcessInstanceId, startDate, processInstanceKey);
        }
      }
    });

    activateMigratorJobs();
  }

  protected void listSkippedProcessInstances() {
   new Pagination<String>()
        .batchSize(batchSize)
        .maxCount(idKeyMapper::findSkippedCount)
        .page(offset -> idKeyMapper.findSkipped(offset, batchSize)
            .stream()
            .map(IdKeyDbModel::id)
            .collect(Collectors.toList()))
        .callback(PrintUtils::print);
  }

  protected boolean skipProcessInstance(String legacyProcessInstanceId) {
    try {
      validateProcessInstanceState(legacyProcessInstanceId);
    } catch (IllegalStateException e) {
      MigratorLogger.warnProcessInstanceCannotBeMigrated(legacyProcessInstanceId, e.getMessage());
      return true;
    }
    return false;
  }

  protected void fetchProcessInstancesToMigrate(Consumer<IdKeyDbModel> storeMappingConsumer) {
    MigratorLogger.infoFetchingProcessInstances();

    if (RETRY_SKIPPED.equals(mode)) {
      new Pagination<IdKeyDbModel>()
          .batchSize(batchSize)
          .maxCount(idKeyMapper::findSkippedCount)
          .page(offset -> idKeyMapper.findSkipped(0, batchSize))
          .callback(storeMappingConsumer);
    } else {
      MigratorLogger.debugFetchingLatestStartDate();
      Date latestStartDate = callApi(() -> idKeyMapper.findLatestStartDateByType(TYPE.RUNTIME_PROCESS_INSTANCE));
      MigratorLogger.debugLatestStartDate(latestStartDate);

      HistoricProcessInstanceQuery processInstanceQuery = historyService.createHistoricProcessInstanceQuery()
          .startedAfter(latestStartDate)
          .rootProcessInstances()
          .unfinished()
          .orderByProcessInstanceStartTime()
          .asc()
          // Ensure order is predictable with two order criteria:
          // Without second criteria and PIs have same start time, order is non-deterministic.
          .orderByProcessInstanceId()
          .asc();

      new Pagination<IdKeyDbModel>()
          .batchSize(batchSize)
          .maxCount(processInstanceQuery::count)
          .page(offset -> processInstanceQuery.listPage(offset, batchSize)
              .stream()
              .map(hpi -> new IdKeyDbModel(hpi.getId(), hpi.getStartTime()))
              .collect(Collectors.toList()))
          .callback(storeMappingConsumer);
    }
  }

  protected void storeMapping(String legacyProcessInstanceId, Date startDate, Long processInstanceKey) {
    var keyIdDbModel = new IdKeyDbModel();
    keyIdDbModel.setId(legacyProcessInstanceId);
    keyIdDbModel.setStartDate(startDate);
    keyIdDbModel.setInstanceKey(processInstanceKey);
    keyIdDbModel.setType(TYPE.RUNTIME_PROCESS_INSTANCE);

    if (RETRY_SKIPPED.equals(mode)) {
      MigratorLogger.debugUpdatingKey(legacyProcessInstanceId, processInstanceKey);
      callApi(() -> idKeyMapper.updateKeyById(keyIdDbModel));
    } else {
      MigratorLogger.debugInsertingRecord(keyIdDbModel);
      callApi(() -> idKeyMapper.insert(keyIdDbModel));
    }
  }

  protected Map<String, Object> getGlobalVariables(String legacyProcessInstanceId) {
    VariableInstanceQuery variableQuery = runtimeService.createVariableInstanceQuery()
        .activityInstanceIdIn(legacyProcessInstanceId);

    Map<String, Object> globalVariables = new Pagination<VariableInstance>()
        .batchSize(batchSize)
        .query(variableQuery)
        .toVariableMap();

    globalVariables.put(VAR_LEGACY_ID, legacyProcessInstanceId);
    return globalVariables;
  }

  protected Long startNewProcessInstance(String legacyProcessInstanceId) {
    var processInstanceQuery = runtimeService.createProcessInstanceQuery().processInstanceId(legacyProcessInstanceId);

    ProcessInstance processInstance = callApi(processInstanceQuery::singleResult,
        MigratorLogger.formatProcessInstanceFetchError(legacyProcessInstanceId));
    if (processInstance != null) {
      String bpmnProcessId = processInstance.getProcessDefinitionKey();
      var createProcessInstance = camundaClient.newCreateInstanceCommand()
          .bpmnProcessId(bpmnProcessId)
          .latestVersion()
          .variables(getGlobalVariables(legacyProcessInstanceId));

      return callApi(() -> createProcessInstance.send().join(),
          MigratorLogger.formatProcessInstanceCreateError(legacyProcessInstanceId))
          .getProcessInstanceKey();
    } else {
      MigratorLogger.warnProcessInstanceNoLongerExists(legacyProcessInstanceId);
      return null;
    }
  }

  /**
   * This method iterates over all the activity instances of the root process instance and its
   * children until it either finds an activityInstance that cannot be migrated or the iteration ends.
   * @param legacyProcessInstanceId the legacy id of the root process instance.
   */
  protected void validateProcessInstanceState(String legacyProcessInstanceId) {
    MigratorLogger.debugValidatingProcessInstance(legacyProcessInstanceId);
    ProcessInstanceQuery processInstanceQuery = runtimeService.createProcessInstanceQuery()
        .rootProcessInstanceId(legacyProcessInstanceId);

    new Pagination<ProcessInstance>()
        .batchSize(batchSize)
        .query(processInstanceQuery)
        .callback(processInstance -> {
          String processInstanceId = processInstance.getId();
          String c7DefinitionId = processInstance.getProcessDefinitionId();
          String c8DefinitionId = processInstance.getProcessDefinitionKey();

          var c8DefinitionSearchRequest = camundaClient.newProcessDefinitionSearchRequest()
              .filter(filter -> filter.processDefinitionId(c8DefinitionId))
              .sort((s) -> s.version().desc());

          List<ProcessDefinition> c8Definitions = callApi(c8DefinitionSearchRequest::execute).items();
          if (c8Definitions.isEmpty()) {
            throw new IllegalStateException(MigratorLogger.formatNoC8ProcessError(c8DefinitionId, legacyProcessInstanceId));
          }

          ActivityInstance activityInstanceTree = callApi(() -> runtimeService.getActivityInstance(processInstanceId));
          BpmnModelInstance c7BpmnModelInstance = callApi(() -> repositoryService.getBpmnModelInstance(c7DefinitionId));

          long processDefinitionKey = c8Definitions.getFirst().getProcessDefinitionKey();
          String c8XmlString =
              callApi(() -> camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).execute());
          BpmnModelInstance c8BpmnModelInstance = Bpmn.readModelFromStream(new ByteArrayInputStream(c8XmlString.getBytes(StandardCharsets.UTF_8)));

          MigratorLogger.debugCollectingActivityInstances(processInstanceId);
          Map<String, FlowNode> activityInstanceMap = getActiveActivityIdsById(activityInstanceTree, new HashMap<>());
          MigratorLogger.debugFoundActivityInstances(activityInstanceMap.size());

          for (FlowNode flowNode : activityInstanceMap.values()) {
            // validate no multi-instance loop characteristics
            FlowElement element = c7BpmnModelInstance.getModelElementById(flowNode.activityId());
            if ((element instanceof Activity activity) &&
                (activity.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics)) {
              throw new IllegalStateException(
                  MigratorLogger.formatMultiInstanceError(element.getName(), processInstance.getId()));
            }

            // validate element exists in C8 deployment
            if (c8BpmnModelInstance.getModelElementById(flowNode.activityId()) == null) {
              throw new IllegalStateException(
                  MigratorLogger.formatElementNotInC8Error(legacyProcessInstanceId, c8DefinitionId, flowNode.activityId()));
            }
          }
        });
  }

  protected void activateMigratorJobs() {
    MigratorLogger.infoActivatingMigratorJobs();
    List<ActivatedJob> migratorJobs = null;
    do {
      var jobQuery = camundaClient.newActivateJobsCommand()
          .jobType(ACTIVITY_TYPE_MIGRATOR)
          .maxJobsToActivate(batchSize);

      migratorJobs = callApi(() -> jobQuery.send().join().getJobs(), MigratorLogger.getMigratorJobsFetchError());
      MigratorLogger.debugMigratorJobsFound(migratorJobs.size());

      migratorJobs.forEach(activatedJob -> {
        String legacyId = (String) callApi(() -> activatedJob.getVariable(VAR_LEGACY_ID),
            MigratorLogger.formatLegacyIdFetchError(activatedJob.getProcessInstanceKey()));
        long processInstanceKey = activatedJob.getProcessInstanceKey();

        var modifyProcessInstance = camundaClient.newModifyProcessInstanceCommand(processInstanceKey);

        // Cancel start event instance where migrator job sits to avoid executing the activities twice.
        long elementInstanceKey = activatedJob.getElementInstanceKey();
        modifyProcessInstance.terminateElement(elementInstanceKey);

        ActivityInstance activityInstanceTree = callApi(() -> runtimeService.getActivityInstance(legacyId),
            MigratorLogger.formatActivityFetchError(legacyId));

        MigratorLogger.debugCollectingActivityInstances(activityInstanceTree.getActivityId());
        Map<String, FlowNode> activityInstanceMap = getActiveActivityIdsById(activityInstanceTree, new HashMap<>());
        MigratorLogger.debugFoundActivityInstances(activityInstanceMap.size());

        activityInstanceMap.forEach((activityInstanceId, flowNode) -> {
          String activityId = flowNode.activityId();
          var variableQuery = runtimeService.createVariableInstanceQuery().activityInstanceIdIn(activityInstanceId);

          Map<String, Object> localVariables = new Pagination<VariableInstance>().batchSize(batchSize)
              .query(variableQuery)
              .toVariableMap();

          String subProcessInstanceId = flowNode.subProcessInstanceId();
          if (subProcessInstanceId != null) {
            localVariables.put(VAR_LEGACY_ID, subProcessInstanceId);
          }

          modifyProcessInstance.activateElement(activityId).withVariables(localVariables, activityId);
        });

        callApi(() -> ((ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3) modifyProcessInstance)
            .send().join(), MigratorLogger.getJobsActivationError());
      });
    } while (!migratorJobs.isEmpty());
  }

  public Map<String, FlowNode> getActiveActivityIdsById(ActivityInstance activityInstance, Map<String, FlowNode> activeActivities) {
    Arrays.asList(activityInstance.getChildActivityInstances()).forEach(actInst -> {
      activeActivities.putAll(getActiveActivityIdsById(actInst, activeActivities));

      if (!ACTIVITY_TYPE_SUBPROCESS.equals(actInst.getActivityType())) {
        activeActivities.put(actInst.getId(), new FlowNode(actInst.getActivityId(), 
            ((ActivityInstanceImpl) actInst).getSubProcessInstanceId()));
      }
    });

    Arrays.asList(activityInstance.getChildTransitionInstances()).forEach(ti -> {
      var transitionInstance = ((TransitionInstanceImpl) ti);
      if (!ACTIVITY_TYPE_SUBPROCESS.equals(transitionInstance.getActivityType())) {
        activeActivities.put(transitionInstance.getId(), 
            new FlowNode(transitionInstance.getActivityId(), transitionInstance.getSubProcessInstanceId()));
      }
    });
    return activeActivities;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public record FlowNode(String activityId, String subProcessInstanceId) {
  }

  public void setMode(MigratorMode mode) {
    this.mode = mode;
  }

}
