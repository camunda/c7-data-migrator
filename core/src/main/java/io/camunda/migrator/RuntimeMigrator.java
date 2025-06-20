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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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

  private final Cache<Long, BpmnModelInstance> c8DefinitionKeyToModelCache =
      Caffeine.newBuilder().maximumSize(50).build();

  public void start() {
    if (LIST_SKIPPED.equals(mode)) {
      PrintUtils.printSkippedInstancesHeader(idKeyMapper.findSkippedCount());
      listSkippedProcessInstances();
    } else {
      migrate();
    }
  }

  public void migrate() {
    Map<String, Long> c8DefinitionIdToDefinitionKey = getC8DeploymentIdToKeyMap();

    fetchProcessInstancesToMigrate(legacyProcessInstance -> {

      String legacyProcessInstanceId = legacyProcessInstance.id();
      Date startDate = legacyProcessInstance.startDate();
      if (skipProcessInstance(legacyProcessInstanceId, c8DefinitionIdToDefinitionKey)) {
        LOGGER.info("Skipping process instance with legacyId: {}", legacyProcessInstanceId);
        if (!legacyProcessInstance.skippedPreviously()) {
          storeMapping(legacyProcessInstanceId, startDate, null);
        }

      } else if (legacyProcessInstance.skippedPreviously() || !idKeyMapper.checkExists(legacyProcessInstanceId)) {
        LOGGER.debug("Starting new C8 process instance with legacyId: [{}]", legacyProcessInstanceId);
        Long processInstanceKey = startNewProcessInstance(legacyProcessInstanceId);
        LOGGER.debug("Started C8 process instance with processInstanceKey: [{}]", processInstanceKey);
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

  protected boolean skipProcessInstance(String legacyProcessInstanceId, Map<String, Long> c8DefinitionIdToDefinitionKey) {
    try {
      validateProcessInstanceState(legacyProcessInstanceId, c8DefinitionIdToDefinitionKey);
    } catch (IllegalStateException e) {
      LOGGER.warn("Process instance with legacyId [{}] can't be migrated: {}", legacyProcessInstanceId, e.getMessage());
      return true;
    }

    return false;
  }

  protected void fetchProcessInstancesToMigrate(Consumer<IdKeyDbModel> storeMappingConsumer) {
    LOGGER.info("Fetching process instances to migrate");

    if (RETRY_SKIPPED.equals(mode)) {
      new Pagination<IdKeyDbModel>()
          .batchSize(batchSize)
          .maxCount(idKeyMapper::findSkippedCount)
          // Hardcode offset to 0 since each callback updates the database and leads to fresh results.
          .page(offset -> idKeyMapper.findSkipped(0, batchSize))
          .callback(storeMappingConsumer);

    } else {
      LOGGER.debug("Fetching latest start date of process instances");
      Date latestStartDate = callApi(() -> idKeyMapper.findLatestStartDateByType(TYPE.RUNTIME_PROCESS_INSTANCE));
      LOGGER.debug("Latest start date: {}", latestStartDate);

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
        .toVariableMap();

    globalVariables.put("legacyId", legacyProcessInstanceId);
    return globalVariables;
  }

  /**
   * This method iterates over all the activity instances of the root process instance and its
   * children until it either finds an activityInstance that cannot be migrated or the iteration ends.
   * For now, only multi-instance activities will fail validation.
   * @param legacyProcessInstanceId the legacy id of the root process instance.
   */
  protected void validateProcessInstanceState(String legacyProcessInstanceId, Map<String, Long> c8DefinitionIdToDefinitionKey) {
    LOGGER.debug("Validate legacy process instance by ID: {}", legacyProcessInstanceId);
    ProcessInstanceQuery processInstanceQuery = runtimeService.createProcessInstanceQuery()
        .rootProcessInstanceId(legacyProcessInstanceId);

    new Pagination<ProcessInstance>()
        .batchSize(batchSize)
        .query(processInstanceQuery)
        .callback(processInstance -> {
          String processInstanceId = processInstance.getId();
          String c7DefinitionId = processInstance.getProcessDefinitionId();
          String c8DefinitionId = processInstance.getProcessDefinitionKey();

          // validate C8 deployment exists
          if (!c8DefinitionIdToDefinitionKey.containsKey(c8DefinitionId)) {
            throw new IllegalStateException(
                String.format("No C8 deployment found for process ID [%s] required for instance with legacyID [%s].",
                    c8DefinitionId, legacyProcessInstanceId));
          }

          ActivityInstance activityInstanceTree = callApi(() -> runtimeService.getActivityInstance(processInstanceId));
          BpmnModelInstance c7BpmnModelInstance = callApi(
              () -> repositoryService.getBpmnModelInstance(c7DefinitionId));
          BpmnModelInstance c8BpmnModelInstance = getC8ModelInstance(c8DefinitionIdToDefinitionKey.get(c8DefinitionId));

          LOGGER.debug("Collecting active descendant activity instances for legacyId [{}]", processInstanceId);
          Map<String, FlowNode> activityInstanceMap = getActiveActivityIdsById(activityInstanceTree, new HashMap<>());
          LOGGER.debug("Found {} active activity instances to validate", activityInstanceMap.size());

          for (FlowNode flowNode : activityInstanceMap.values()) {
            // validate no multi-instance loop characteristics
            FlowElement element = c7BpmnModelInstance.getModelElementById(flowNode.activityId());
            if ((element instanceof Activity activity) && (activity.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics)) {
              throw new IllegalStateException("Found multi-instance loop characteristics for " + element.getName() +
                  " in C7 process instance " + processInstance.getId() + ".");
            }

            // validate element exists in C8 deployment
            if (c8BpmnModelInstance.getModelElementById(flowNode.activityId()) == null) {
              throw new IllegalStateException(String.format(
                  "C7 instance detected which is currently in a C7 model element which does not exist in the equivalent deployed C8 model. "
                      + "Instance legacyId: [%s], Model legacyId: [%s], Element Id: [%s].",
                  legacyProcessInstanceId, c8DefinitionId, flowNode.activityId));
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
              .toVariableMap();

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

  public void setMode(MigratorMode mode) {
    this.mode = mode;
  }

  private Map<String, Long> getC8DeploymentIdToKeyMap() {
    int from = 0;
    List<ProcessDefinition> c8Deployments;
    Map<String, Long> idToKey = new HashMap<>();

    do {
      final int finalFrom = from;
      c8Deployments = camundaClient.newProcessDefinitionSearchRequest().page(p -> {
        p.limit(batchSize);
        p.from(finalFrom);
      }).execute().items();

      idToKey.putAll(c8Deployments.stream()
          .collect(
              Collectors.toMap(ProcessDefinition::getProcessDefinitionId, ProcessDefinition::getProcessDefinitionKey)));
      from = from + c8Deployments.size();
    } while (c8Deployments.size() == batchSize);

    return idToKey;
  }

  private BpmnModelInstance getC8ModelInstance(Long c8DefinitionKey) {
    return c8DefinitionKeyToModelCache.get(c8DefinitionKey, key -> {
      String c8XmlString = camundaClient.newProcessDefinitionGetXmlRequest(key).execute();
      return Bpmn.readModelFromStream(new ByteArrayInputStream(c8XmlString.getBytes(StandardCharsets.UTF_8)));
    });
  }

}
