/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import static io.camunda.migrator.ExceptionUtils.callApi;
import static io.camunda.migrator.history.IdKeyMapper.TYPE;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.migrator.history.IdKeyDbModel;
import io.camunda.migrator.history.IdKeyMapper;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.impl.ProcessInstanceQueryImpl;
import org.camunda.bpm.engine.impl.persistence.entity.ActivityInstanceImpl;
import org.camunda.bpm.engine.impl.persistence.entity.TransitionInstanceImpl;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
import org.camunda.bpm.engine.runtime.VariableInstance;
import org.camunda.bpm.engine.runtime.VariableInstanceQuery;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RuntimeMigrator {

  protected static final Logger LOGGER = LoggerFactory.getLogger(RuntimeMigrator.class);

  public final static int DEFAULT_BATCH_SIZE = 500;

  @Autowired
  private RepositoryService repositoryService;

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected IdKeyMapper idKeyMapper;

  @Autowired
  protected CamundaClient camundaClient;

  @Value("${migrator.batch-size:" + DEFAULT_BATCH_SIZE + "}")
  protected int batchSize;

  protected boolean retryMode = false;

  public void migrate() {
    fetchProcessInstancesToMigrate(legacyProcessInstanceId -> {
      if (skipProcessInstance(legacyProcessInstanceId)) {
        LOGGER.info("Skipping process instance with legacyId: {}", legacyProcessInstanceId);
        storeMapping(legacyProcessInstanceId, null);

      } else {
        LOGGER.debug("Starting new C8 process instance with legacyId: [{}]", legacyProcessInstanceId);
        Long processInstanceKey = startNewProcessInstance(legacyProcessInstanceId);
        LOGGER.debug("Started C8 process instance with processInstanceKey: [{}]", processInstanceKey);
        if (processInstanceKey != null) {
          storeMapping(legacyProcessInstanceId, processInstanceKey);
        }
      }
    });

    activateMigratorJobs();
  }

  protected boolean skipProcessInstance(String legacyProcessInstanceId) {
    try {
      validateProcessInstanceState(legacyProcessInstanceId);
    } catch (IllegalStateException e) {
      LOGGER.warn("Process instance with legacyId [{}] can't be migrated: {}", legacyProcessInstanceId, e.getMessage());
      return true;
    }

    return false;
  }

  protected void fetchProcessInstancesToMigrate(Consumer<String> storeMappingConsumer) {
    LOGGER.info("Fetching process instances to migrate");
    if (retryMode) {
      new Pagination<String>()
          .batchSize(batchSize)
          .maxCount(idKeyMapper::findSkippedProcessInstanceIdsCount)
          // Hardcode offset to 0 since each callback updates the database and leads to fresh results.
          .page(offset -> idKeyMapper.findSkippedProcessInstanceIds(0, batchSize))
          .callback(storeMappingConsumer);
    } else {
      LOGGER.debug("Fetching Legacy ID for latest Process Instance");
      String latestLegacyId = callApi(() -> idKeyMapper.findLatestIdByType(TYPE.RUNTIME_PROCESS_INSTANCE));
      LOGGER.debug("Legacy ID of latest migrated process instance: [{}]", latestLegacyId);

      ProcessInstanceQuery processInstanceQuery = ((ProcessInstanceQueryImpl) runtimeService.createProcessInstanceQuery())
          .idAfter(latestLegacyId)
          .rootProcessInstances()
          .orderByProcessInstanceId()
          .asc();

      new Pagination<String>()
          .batchSize(batchSize)
          .maxCount(processInstanceQuery::count)
          .page(offset -> processInstanceQuery.listPage(offset, batchSize)
              .stream()
              .map(ProcessInstance::getId)
              .collect(Collectors.toSet()))
          .callback(storeMappingConsumer);
    }
  }

  protected void storeMapping(String legacyProcessInstanceId, Long processInstanceKey) {
    var keyIdDbModel = new IdKeyDbModel();
    keyIdDbModel.setId(legacyProcessInstanceId);
    keyIdDbModel.setItemKey(processInstanceKey);
    keyIdDbModel.setType(TYPE.RUNTIME_PROCESS_INSTANCE);

    if (retryMode) {
      LOGGER.debug("Updating key for legacyId [{}] with value [{}]", legacyProcessInstanceId, processInstanceKey);
      callApi(() -> idKeyMapper.updateKeyById(keyIdDbModel));
    } else {
      LOGGER.debug("Inserting record [{}]", keyIdDbModel);
      callApi(() -> idKeyMapper.insert(keyIdDbModel));
    }
  }

  protected Long startNewProcessInstance(String legacyProcessInstanceId) {
    var processInstanceQuery = runtimeService.createProcessInstanceQuery().processInstanceId(legacyProcessInstanceId);

    String fetchProcessIdError = "Process instance fetching failed for legacyId: " + legacyProcessInstanceId;
    ProcessInstance processInstance = callApi(processInstanceQuery::singleResult, fetchProcessIdError);
    if (processInstance != null) {
      String bpmnProcessId = processInstance.getProcessDefinitionKey();

      var createProcessInstance = camundaClient.newCreateInstanceCommand()
          .bpmnProcessId(bpmnProcessId)
          .latestVersion()
          .variables(getGlobalVariables(legacyProcessInstanceId));

      String createProcessInstanceErrorMessage = "Creating process instance failed for legacyId: " + legacyProcessInstanceId;
      return callApi(() -> createProcessInstance.send().join(), createProcessInstanceErrorMessage).getProcessInstanceKey();
    } else {
      LOGGER.warn("Process instance with legacyId {} doesn't exist anymore. Has it been completed or cancelled in the meantime?", legacyProcessInstanceId);
      return null;
    }
  }

  protected Map<String, Object> getGlobalVariables(String legacyProcessInstanceId) {
    VariableInstanceQuery variableQuery = runtimeService.createVariableInstanceQuery()
        .activityInstanceIdIn(legacyProcessInstanceId);

    Map<String, Object> globalVariables = new Pagination<VariableInstance>()
        .batchSize(batchSize)
        .query(variableQuery)
        .toMap(VariableInstance::getName, VariableInstance::getValue);

    globalVariables.put("legacyId", legacyProcessInstanceId);
    return globalVariables;
  }

  /**
   * This method iterates over all the activity instances of the root process instance and its
   * children until it either finds an activityInstance that cannot be migrated or the iteration ends.
   * For now, only multi-instance activities will fail validation.
   * @param legacyProcessInstanceId the legacy id of the root process instance.
   */
  protected void validateProcessInstanceState(String legacyProcessInstanceId) {
    ProcessInstanceQuery processInstanceQuery = runtimeService.createProcessInstanceQuery()
        .rootProcessInstanceId(legacyProcessInstanceId);

    new Pagination<ProcessInstance>()
        .batchSize(batchSize)
        .query(processInstanceQuery)
        .callback(processInstance -> {
          String processInstanceId = processInstance.getId();
          ActivityInstance activityInstanceTree = callApi(() -> runtimeService.getActivityInstance(processInstanceId));

          String processDefinitionId = processInstance.getProcessDefinitionId();
          BpmnModelInstance bpmnModelInstance = callApi(() -> repositoryService.getBpmnModelInstance(processDefinitionId));

          LOGGER.debug("Collecting active descendant activity instances for legacyId [{}]", processInstanceId);
          Map<String, FlowNode> activityInstanceMap = getActiveActivityIdsById(activityInstanceTree, new HashMap<>());
          LOGGER.debug("Found {} active activity instances to validate", activityInstanceMap.size());

          for (FlowNode flowNode : activityInstanceMap.values()) {
            FlowElement element = bpmnModelInstance.getModelElementById(flowNode.activityId());
            if ((element instanceof Activity activity) && (activity.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics)) {
              throw new IllegalStateException("Found multi-instance loop characteristics for " + element.getName() +
                  " in C7 process instance " + processInstance.getId() + ".");
            }
          }
        });
  }

  protected void activateMigratorJobs() {
    LOGGER.info("Activating migrator jobs");
    List<ActivatedJob> migratorJobs = null;
    do {
      var jobQuery = camundaClient.newActivateJobsCommand()
          .jobType("migrator")
          .maxJobsToActivate(batchSize);

      String fetchMigratorJobsErrorMessage = "Error while fetching migrator jobs";
      migratorJobs = callApi(() -> jobQuery.send().join().getJobs(), fetchMigratorJobsErrorMessage);

      LOGGER.debug("Migrator jobs found: {}", migratorJobs.size());

      migratorJobs.forEach(activatedJob -> {
        String fetchLegacyIdErrorMessage =
            String.format("Error while fetching legacyId for job with key:" + activatedJob.getProcessInstanceKey());
        String legacyId = (String) callApi(() -> activatedJob.getVariable("legacyId"), fetchLegacyIdErrorMessage);
        long processInstanceKey = activatedJob.getProcessInstanceKey();

        var modifyProcessInstance = camundaClient.newModifyProcessInstanceCommand(processInstanceKey);

        // Cancel start event instance where migrator job sits to avoid executing the activities twice.
        long elementInstanceKey = activatedJob.getElementInstanceKey();
        modifyProcessInstance.terminateElement(elementInstanceKey);

        String fetchActivityErrorMessage = "Error while fetching activity for job with legacyId:" + legacyId;
        ActivityInstance activityInstanceTree = callApi(() -> runtimeService.getActivityInstance(legacyId), fetchActivityErrorMessage);

        LOGGER.debug("Collecting active descendant activity instances for activityId [{}]", activityInstanceTree.getActivityId());
        Map<String, FlowNode> activityInstanceMap = getActiveActivityIdsById(activityInstanceTree, new HashMap<>());
        LOGGER.debug("Found {} active activity instances to activate", activityInstanceMap.size());

        activityInstanceMap.forEach((activityInstanceId, flowNode) -> {
          String activityId = flowNode.activityId();
          var variableQuery = runtimeService.createVariableInstanceQuery().activityInstanceIdIn(activityInstanceId);

          Map<String, Object> localVariables = new Pagination<VariableInstance>().batchSize(batchSize)
              .query(variableQuery)
              .toMap(VariableInstance::getName, VariableInstance::getValue);

          String subProcessInstanceId = flowNode.subProcessInstanceId();
          if (subProcessInstanceId != null) {
            localVariables.put("legacyId", subProcessInstanceId);
          }

          modifyProcessInstance.activateElement(activityId).withVariables(localVariables, activityId);
        });

        String batchUpdatedActivitiesErrorMessage = "Error while activating jobs";
        callApi(() -> ((ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3) modifyProcessInstance).send().join(), batchUpdatedActivitiesErrorMessage);
        // no need to complete the job since the modification canceled the migrator job in the start event
      });
    } while (!migratorJobs.isEmpty());
  }

  public Map<String, FlowNode> getActiveActivityIdsById(ActivityInstance activityInstance, Map<String, FlowNode> activeActivities) {
    Arrays.asList(activityInstance.getChildActivityInstances()).forEach(actInst -> {
      activeActivities.putAll(getActiveActivityIdsById(actInst, activeActivities));

      if (!"subProcess".equals(actInst.getActivityType())) {
        activeActivities.put(actInst.getId(), new FlowNode(actInst.getActivityId(), ((ActivityInstanceImpl) actInst).getSubProcessInstanceId()));
      }
    });

    /* TODO: Transition instances might map to start before or after.
    When it maps to asyncBefore it should be fine. When it maps to asyncAfter an execution is fired twice in C7 and C8.
     */
    Arrays.asList(activityInstance.getChildTransitionInstances()).forEach(ti -> {
      var transitionInstance = ((TransitionInstanceImpl) ti);
      if (!"subProcess".equals(transitionInstance.getActivityType())) {
        activeActivities.put(transitionInstance.getId(), new FlowNode(transitionInstance.getActivityId(), transitionInstance.getSubProcessInstanceId()));
      }
    });
    return activeActivities;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public record FlowNode(String activityId, String subProcessInstanceId) {
  }

  public void setRetryMode(boolean retryMode) {
    this.retryMode = retryMode;
  }

}
