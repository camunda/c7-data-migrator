/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl;

import static io.camunda.migrator.constants.MigratorConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.migrator.impl.util.C7Utils.getActiveActivityIdsById;
import static io.camunda.migrator.impl.util.ExceptionUtils.callApi;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.FAILED_TO_PARSE_BPMN_MODEL;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.FLOW_NODE_NOT_EXISTS_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.NO_C8_DEPLOYMENT_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.NO_NONE_START_EVENT_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.NO_EXECUTION_LISTENER_OF_TYPE_ERROR;
import static io.camunda.migrator.impl.logging.RuntimeValidatorLogs.CALL_ACTIVITY_LEGACY_ID_ERROR;

import io.camunda.migrator.impl.logging.RuntimeValidatorLogs;
import static io.camunda.zeebe.model.bpmn.Bpmn.readModelFromStream;

import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.clients.C7Client;
import io.camunda.migrator.impl.clients.C8Client;
import io.camunda.migrator.impl.model.FlowNode;
import io.camunda.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.camunda.zeebe.model.bpmn.impl.instance.zeebe.ZeebeExecutionListenersImpl;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Activity;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Dedicated class for all validation logic used in the migration process.
 * Consolidates validation methods that were previously scattered across C7Client, C8Client, and RuntimeMigrator.
 */
@Component
public class RuntimeValidator {

  @Autowired
  protected MigratorProperties properties;

  @Autowired
  protected C7Client c7Client;

  @Autowired
  protected C8Client c8Client;

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected RepositoryService repositoryService;

  /**
   * Validates process instance state with pagination across process instance hierarchy (starting from root process instance).
   */
  public void validateProcessInstanceState(String legacyRootProcessInstanceId, Consumer<ProcessInstance> validator) {
    ProcessInstanceQuery processInstanceQuery = runtimeService.createProcessInstanceQuery()
        .rootProcessInstanceId(legacyRootProcessInstanceId);

    new Pagination<ProcessInstance>()
        .pageSize(properties.getPageSize())
        .query(processInstanceQuery)
        .callback(validator);
  }

  /**
   * Validates C7 flow nodes for multi-instance loop characteristics.
   */
  public void validateC7FlowNodes(String processDefinitionId, String activityId) {
    BpmnModelInstance c7BpmnModelInstance = repositoryService.getBpmnModelInstance(processDefinitionId);
    FlowElement element = c7BpmnModelInstance.getModelElementById(activityId);
    if ((element instanceof Activity activity)
        && (activity.getLoopCharacteristics() instanceof MultiInstanceLoopCharacteristics)) {
      throw new IllegalStateException(String.format(MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR, element.getId()));
    }
  }

  /**
   * Validates C8 process structure and execution listeners.
   */
  public void validateC8Process(String xmlString, long processDefinitionKey) {
    var bpmnModelInstance = parseBpmnModel(xmlString);

    var processInstanceStartEvents = bpmnModelInstance.getDefinitions()
        .getModelInstance()
        .getModelElementsByType(StartEvent.class)
        .stream()
        .filter(startEvent -> startEvent.getParentElement() instanceof ProcessImpl)
        .toList();

    boolean hasNoneStartEvent = processInstanceStartEvents.stream()
        .anyMatch(startEvent -> startEvent.getEventDefinitions().isEmpty());
    if (!hasNoneStartEvent) {
      throw new IllegalStateException(String.format(NO_NONE_START_EVENT_ERROR, processDefinitionKey));
    }

    // Skip job type validation if disabled
    if (properties.isJobTypeValidationDisabled()) {
      RuntimeValidatorLogs.jobTypeValidationDisabled();
      return;
    }

    String validationJobType = properties.getEffectiveValidationJobType();
    processInstanceStartEvents.forEach(startEvent -> {
      var zBExecutionListeners = startEvent.getSingleExtensionElement(ZeebeExecutionListenersImpl.class);
      boolean hasMigratorListener = zBExecutionListeners != null && zBExecutionListeners.getExecutionListeners().stream()
          .anyMatch(listener -> validationJobType.equals(listener.getType()));

      if (!hasMigratorListener) {
        throw new IllegalStateException(String.format(NO_EXECUTION_LISTENER_OF_TYPE_ERROR,
            validationJobType, startEvent.getId(), processDefinitionKey, validationJobType));
      }
    });
  }

  /**
   * Validates C8 flow nodes exist in the BPMN model.
   */
  public void validateC8FlowNodes(String xmlString, String activityId) {
    var bpmnModelInstance = parseBpmnModel(xmlString);
    var element = bpmnModelInstance.getModelElementById(activityId);
    if (element == null) {
      throw new IllegalStateException(String.format(FLOW_NODE_NOT_EXISTS_ERROR, activityId));
    }

    // Check if it's a CallActivity and validate propagateAllParentVariables
    if (element instanceof io.camunda.zeebe.model.bpmn.instance.CallActivity callActivity) {
      var extensionElements = callActivity.getExtensionElements();
      if (extensionElements != null) {
        var calledElements = extensionElements.getElementsQuery()
            .filterByType(io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement.class)
            .list();

        for (var calledElement : calledElements) {
          String propagateAllParentVariables = calledElement.getDomElement().getAttribute("propagateAllParentVariables");
          if ("false".equalsIgnoreCase(propagateAllParentVariables)) {
            // Check if there's an explicit mapping for legacyId
            var ioMappings = extensionElements.getElementsQuery()
                .filterByType(io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping.class)
                .list();

            boolean hasLegacyIdMapping = ioMappings.stream()
                .flatMap(mapping -> mapping.getInputs().stream())
                .anyMatch(input -> LEGACY_ID_VAR_NAME.equals(input.getTarget()));

            if (!hasLegacyIdMapping) {
              throw new IllegalStateException(String.format(CALL_ACTIVITY_LEGACY_ID_ERROR, activityId));
            }
          }
        }
      }
    }
  }

  /**
   * Validates that C8 process definition exists for the given process definition ID.
   */
  public void validateC8DefinitionExists(List<ProcessDefinition> c8Definitions, String c8DefinitionId, String legacyProcessInstanceId) {
    if (c8Definitions.isEmpty()) {
      throw new IllegalStateException(String.format(NO_C8_DEPLOYMENT_ERROR, c8DefinitionId, legacyProcessInstanceId));
    }
  }

  /**
   * Parses BPMN model instance from XML string.
   */
  private io.camunda.zeebe.model.bpmn.BpmnModelInstance parseBpmnModel(String xmlString) {
    return callApi(() -> readModelFromStream(new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8))), FAILED_TO_PARSE_BPMN_MODEL);
  }

  /**
   * This method iterates over all the activity instances of the root process instance and its
   * children until it either finds an activityInstance that cannot be migrated or the iteration ends.
   * @param legacyProcessInstanceId the legacy id of the root process instance.
   */
  public void validateProcessInstanceState(String legacyProcessInstanceId) {
    RuntimeValidatorLogs.validateLegacyProcessInstance(legacyProcessInstanceId);

    validateProcessInstanceState(legacyProcessInstanceId, processInstance -> {
      String processInstanceId = processInstance.getId();
      String c7DefinitionId = processInstance.getProcessDefinitionId();
      String c8DefinitionId = processInstance.getProcessDefinitionKey();

      var c8Definitions = c8Client.searchProcessDefinitions(c8DefinitionId);
      validateC8DefinitionExists(c8Definitions.items(), c8DefinitionId, processInstanceId);

      var activityInstanceTree = c7Client.getActivityInstance(processInstanceId);

      long processDefinitionKey = c8Definitions.items().getFirst().getProcessDefinitionKey();
      String c8XmlString = c8Client.getProcessDefinitionXml(processDefinitionKey);

      validateC8Process(c8XmlString, processDefinitionKey);

      RuntimeValidatorLogs.collectingActiveDescendantActivitiesValidation(processInstanceId);
      Map<String, FlowNode> activityInstanceMap = getActiveActivityIdsById(activityInstanceTree, new HashMap<>());
      RuntimeValidatorLogs.foundActiveActivitiesToValidate(activityInstanceMap.size());

      for (FlowNode flowNode : activityInstanceMap.values()) {
        validateC7FlowNodes(c7DefinitionId, flowNode.activityId());
        validateC8FlowNodes(c8XmlString, flowNode.activityId());
      }
    });
  }

}
