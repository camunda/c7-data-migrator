/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import static io.camunda.migrator.ExceptionUtils.callApi;
import static io.camunda.migrator.MigratorMode.LIST_SKIPPED;
import static io.camunda.migrator.MigratorMode.MIGRATE;
import static io.camunda.migrator.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migrator.persistence.IdKeyMapper.TYPE;
import static io.camunda.zeebe.model.bpmn.Bpmn.readModelFromStream;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.migrator.persistence.IdKeyDbModel;
import io.camunda.migrator.persistence.IdKeyMapper;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeExecutionListenersImpl;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

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

  @Autowired
  private ApplicationContext context;


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
        if (!legacyProcessInstance.skippedPreviously()) {
          storeMapping(legacyProcessInstanceId, startDate, null);
        }

      } else if (legacyProcessInstance.skippedPreviously() || callApi(() -> !idKeyMapper.checkExists(legacyProcessInstanceId))) {
        LOGGER.debug("Starting new C8 process instance with legacyId: [{}]", legacyProcessInstanceId);
        Long processInstanceKey = null;
        try {
          processInstanceKey = startNewProcessInstance(legacyProcessInstanceId);
          LOGGER.debug("Started C8 process instance with processInstanceKey: [{}]", processInstanceKey);
          if (processInstanceKey != null) {
            storeMapping(legacyProcessInstanceId, startDate, processInstanceKey);
          }
        } catch (VariableInterceptorException e) {
          LOGGER.info(
              "Skipping process instance with legacyId: {}; due to: {} "
                  + "Enable DEBUG level to print the stacktrace.",
              legacyProcessInstanceId, e.getMessage());
          LOGGER.debug("Stacktrace:", e);
          if (!legacyProcessInstance.skippedPreviously()) {
            storeMapping(legacyProcessInstanceId, startDate, null);
          }
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
      LOGGER.warn("Skipping process instance with legacyId [{}]: {}", legacyProcessInstanceId, e.getMessage());
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

  protected Long startNewProcessInstance(String legacyProcessInstanceId) throws VariableInterceptorException {
    var processInstanceQuery = runtimeService.createProcessInstanceQuery().processInstanceId(legacyProcessInstanceId);

    String fetchProcessIdError = "Process instance fetching failed for legacyId: " + legacyProcessInstanceId;
    ProcessInstance processInstance = callApi(processInstanceQuery::singleResult, fetchProcessIdError);
    if (processInstance != null) {
      String bpmnProcessId = processInstance.getProcessDefinitionKey();

      // Ensure all variables are fetched and can be transformed before starting the new instance
      Map<String, Map<String, Object>> allVariables = getAllVariables(legacyProcessInstanceId);

      var createProcessInstance = camundaClient.newCreateInstanceCommand()
          .bpmnProcessId(bpmnProcessId)
          .latestVersion()
          .variables(getGlobalVariables(allVariables, legacyProcessInstanceId));

      String createProcessInstanceErrorMessage = "Creating process instance failed for legacyId: " + legacyProcessInstanceId;
      return callApi(createProcessInstance::execute, createProcessInstanceErrorMessage).getProcessInstanceKey();
    } else {
      LOGGER.warn("Process instance with legacyId {} doesn't exist anymore. Has it been completed or cancelled in the meantime?", legacyProcessInstanceId);
      return null;
    }
  }

  protected Map<String, Map<String, Object>> getAllVariables(String legacyProcessInstanceId) throws VariableInterceptorException {
    VariableInstanceQuery variableQuery = runtimeService.createVariableInstanceQuery()
        .processInstanceIdIn(legacyProcessInstanceId);

    Map<String, Map<String, Object>> allVariables = new Pagination<VariableInstance>()
        .batchSize(batchSize)
        .query(variableQuery)
        .context(context)
        .toVariableMapAll();
    return allVariables;
  }

  protected Map<String, Object> getGlobalVariables(Map<String, Map<String, Object>> allVariables,
                                                   String legacyProcessInstanceId) {
    Map<String, Object> globalVariables = allVariables.getOrDefault(legacyProcessInstanceId, new HashMap<>());

    globalVariables.put("legacyId", legacyProcessInstanceId);
    return globalVariables;
  }

  /**
   * This method iterates over all the activity instances of the root process instance and its
   * children until it either finds an activityInstance that cannot be migrated or the iteration ends.
   * @param legacyProcessInstanceId the legacy id of the root process instance.
   */
  protected void validateProcessInstanceState(String legacyProcessInstanceId) {
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

          var c8DefinitionSearchRequest = camundaClient.newProcessDefinitionSearchRequest()
              .filter(filter -> filter.processDefinitionId(c8DefinitionId))
              .sort((s) -> s.version().desc());

          List<ProcessDefinition> c8Definitions = callApi(c8DefinitionSearchRequest::execute).items();
          validateC8DefinitionExists(c8Definitions, c8DefinitionId, processInstanceId);

          ActivityInstance activityInstanceTree = callApi(() -> runtimeService.getActivityInstance(processInstanceId));
          var c7BpmnModelInstance = callApi(() -> repositoryService.getBpmnModelInstance(c7DefinitionId));

          long processDefinitionKey = c8Definitions.getFirst().getProcessDefinitionKey();
          String c8XmlString = callApi(() -> camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).execute());
          var c8BpmnModelInstance = readModelFromStream(new ByteArrayInputStream(c8XmlString.getBytes(StandardCharsets.UTF_8)));

          validateC8Process(c8BpmnModelInstance, processDefinitionKey);

          LOGGER.debug("Collecting active descendant activity instances for legacyId [{}]", processInstanceId);
          Map<String, FlowNode> activityInstanceMap = getActiveActivityIdsById(activityInstanceTree, new HashMap<>());
          LOGGER.debug("Found {} active activity instances to validate", activityInstanceMap.size());

          for (FlowNode flowNode : activityInstanceMap.values()) {
            validateC7FlowNodes(c7BpmnModelInstance, flowNode.activityId());
            validateC8FlowNodes(c8BpmnModelInstance, flowNode.activityId());
          }
        });
  }

  protected void validateC8Process(io.camunda.zeebe.model.bpmn.BpmnModelInstance bpmnModelInstance, long processDefinitionKey) {
    var processInstanceStartEvents = bpmnModelInstance.getDefinitions()
        .getModelInstance()
        .getModelElementsByType(StartEvent.class)
        .stream()
        .filter(startEvent -> startEvent.getParentElement() instanceof ProcessImpl)
        .toList();

    boolean hasNoneStartEvent = processInstanceStartEvents.stream().anyMatch(startEvent -> startEvent.getEventDefinitions().isEmpty());
    if (!hasNoneStartEvent) {
      throw new IllegalStateException(String.format("Couldn't find process None Start Event in C8 process with key [%s].", processDefinitionKey));
    }

    processInstanceStartEvents
        .forEach(startEvent -> {
          var zBExecutionListeners = startEvent.getSingleExtensionElement(ZeebeExecutionListenersImpl.class);
          if (zBExecutionListeners == null) {
            throw new IllegalStateException(String.format("Couldn't find execution listener of type 'migrator' "
                + "on start event [%s] in C8 process with key [%s].", startEvent.getId(), processDefinitionKey));
          } else {
            boolean hasMigratorListener = zBExecutionListeners.getExecutionListeners().stream()
                .anyMatch(listener -> "migrator".equals(listener.getType()));

            if (!hasMigratorListener) {
              throw new IllegalStateException(String.format(
                  "No execution listener of type 'migrator' found on start event [%s] in C8 process with id [%s]. " +
                  "At least one migrator listener is required.",
                  startEvent.getId(), processDefinitionKey));
            }
          }
        });
  }

  protected void validateC8FlowNodes(BpmnModelInstance c8BpmnModelInstance, String activityId) {
    if (c8BpmnModelInstance.getModelElementById(activityId) == null) {
      throw new IllegalStateException(String.format("Flow node with id [%s] "
          + "doesn't exist in the equivalent deployed C8 model.", activityId));
    }
  }

  protected void validateC7FlowNodes(org.camunda.bpm.model.bpmn.BpmnModelInstance c7BpmnModelInstance, String activityId) {
    FlowElement element = c7BpmnModelInstance.getModelElementById(activityId);
    if ((element instanceof Activity activity)
        && (activity.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics)) {
      throw new IllegalStateException(String.format("Found multi-instance loop characteristics "
          + "for flow node with id [%s] in C7 process instance.", element.getId()));
    }
  }

  protected void validateC8DefinitionExists(List<ProcessDefinition> c8Definitions, String c8DefinitionId, String legacyProcessInstanceId) {
    if (c8Definitions.isEmpty()) {
      throw new IllegalStateException(
          String.format("No C8 deployment found for process ID [%s] required for instance with legacyID [%s].", c8DefinitionId, legacyProcessInstanceId));
    }
  }

  protected void activateMigratorJobs() {
    LOGGER.info("Activating migrator jobs");
    List<ActivatedJob> migratorJobs = null;
    do {
      var jobQuery = camundaClient.newActivateJobsCommand()
          .jobType("migrator")
          .maxJobsToActivate(batchSize);

      String fetchMigratorJobsErrorMessage = "Error while fetching migrator jobs";
      migratorJobs = callApi(() -> jobQuery.execute().getJobs(), fetchMigratorJobsErrorMessage);

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
              .context(context)
              .toVariableMapSingleActivity();

          String subProcessInstanceId = flowNode.subProcessInstanceId();
          if (subProcessInstanceId != null) {
            localVariables.put("legacyId", subProcessInstanceId);
          }

          modifyProcessInstance.activateElement(activityId).withVariables(localVariables, activityId);
        });

        String batchUpdatedActivitiesErrorMessage = "Error while activating jobs";
        callApi(() -> ((ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3) modifyProcessInstance).execute(), batchUpdatedActivitiesErrorMessage);
        // no need to complete the job since the modification canceled the migrator job in the start event
      });
    } while (!migratorJobs.isEmpty());
  }

  public Map<String, FlowNode> getActiveActivityIdsById(ActivityInstance activityInstance, Map<String, FlowNode> activeActivities) {
    Arrays.asList(activityInstance.getChildActivityInstances()).forEach(actInst -> {
      activeActivities.putAll(getActiveActivityIdsById(actInst, activeActivities));

      if (!"subProcess".equals(actInst.getActivityType()) && !actInst.getActivityId().endsWith("#multiInstanceBody") ) {
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

}
