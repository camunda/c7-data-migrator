/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.impl.logging;

import io.camunda.migrator.HistoryMigrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for HistoryMigrator.
 * Contains all log messages and string constants used in HistoryMigrator.
 */
public class HistoryMigratorLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(HistoryMigrator.class);

  // HistoryMigrator Messages
  public static final String MIGRATING_DEFINITIONS = "Migrating {} definitions";
  public static final String MIGRATING_DEFINITION = "Migrating {} definition with legacyId: [{}]";
  public static final String MIGRATING_DEFINITION_COMPLETE = "Migration of {} definition with legacyId [{}] completed";

  public static final String SKIPPING_DECISION_DEFINITION = "Migration of historic decision definition with legacyId [{}] skipped. Decision requirements definition not yet available.";

  public static final String MIGRATING_INSTANCES = "Migrating historic {} instances";
  public static final String MIGRATING_INSTANCE = "Migrating historic {} instance with legacyId: [{}]";
  public static final String MIGRATING_INSTANCE_COMPLETE = "Migration of historic {} instance with legacyId "
      + "[{}] completed";
  public static final String SKIPPING_INSTANCE_MISSING_PARENT = "Migration of historic {} instance with legacyId [{}] skipped. Parent instance not yet available.";
  public static final String SKIPPING_INSTANCE_MISSING_DEFINITION = "Migration of historic {} instance with legacyId [{}] skipped. {} definition not yet available.";

  public static final String MIGRATING_INCIDENTS = "Migrating historic incidents";
  public static final String MIGRATING_INCIDENT = "Migrating historic incident with legacyId: [{}]";
  public static final String MIGRATING_INCIDENT_COMPLETED = "Migration of historic incident with legacyId [{}] completed.";
  public static final String SKIPPING_INCIDENT = "Migration of historic incident with legacyId [{}] skipped. Process "
      + "instance not yet available.";

  public static final String MIGRATING_VARIABLES = "Migrating historic variables";
  public static final String MIGRATING_VARIABLE = "Migrating historic variables with legacyId: [{}]";
  public static final String MIGRATING_VARIABLE_COMPLETED = "Migration of historic variable with legacyId [{}] completed.";
  public static final String SKIPPING_VARIABLE_MISSING_FLOW_NODE = "Migration of historic variable with legacyId [{}] skipped. Flow node instance not yet available.";
  public static final String SKIPPING_VARIABLE_MISSING_PROCESS = "Migration of historic variable with legacyId [{}] skipped. Process instance not yet available.";
  public static final String SKIPPING_VARIABLE_MISSING_TASK = "Migration of historic variable with legacyId [{}] skipped. Associated task [{}] was skipped.";
  public static final String SKIPPING_VARIABLE_MISSING_SCOPE = "Migration of historic variable with legacyId [{}] skipped. Scope key is not yet available.";

  public static final String MIGRATING_USER_TASKS = "Migrating historic user tasks";
  public static final String MIGRATING_USER_TASK = "Migrating historic user task with legacyId: [{}]";
  public static final String MIGRATING_USER_TASK_COMPLETED = "Migration of historic user task with legacyId [{}] completed.";
  public static final String SKIPPING_MIGRATING_USER_TASK_MISSING_FLOW_NODE = "Migration of historic user task with legacyId [{}] skipped. Flow node instance yet not available.";
  public static final String SKIPPING_USER_TASK_MISSING_PROCESS = "Migration of historic user task with legacyId [{}] skipped. Process instance yet not available.";

  public static final String MIGRATING_FLOW_NODES = "Migrating historic flow nodes";
  public static final String MIGRATING_FLOW_NODE = "Migrating historic flow nodes with legacyId: [{}]";
  public static final String MIGRATING_FLOW_NODE_COMPLETED = "Migration of historic flow nodes with legacyId [{}] completed.";
  public static final String SKIPPING_FLOW_NODE = "Migration of historic flow nodes with legacyId [{}] skipped. Process instance yet not available.";

  public static final String MIGRATING_DECISION_REQUIREMENTS = "Migrating decision requirements";
  public static final String MIGRATING_DECISION_REQUIREMENT = "Migrating decision requirements with legacyId: [{}]";
  public static final String MIGRATING_DECISION_REQUIREMENT_COMPLETED = "Migration of decision requirements with legacyId [{}] completed.";

  public static void migratingDecisionDefinitions() {
    LOGGER.info(MIGRATING_DEFINITIONS, "decision");
  }

  public static void migratingDecisionDefinition(String legacyDecisionDefinitionId) {
    LOGGER.debug(MIGRATING_DEFINITION, "decision", legacyDecisionDefinitionId);
  }

  public static void migratingDecisionDefinitionCompleted(String legacyDecisionDefinitionId) {
    LOGGER.debug(MIGRATING_DEFINITION_COMPLETE, "decision", legacyDecisionDefinitionId);
  }

  public static void skippingDecisionDefinition(String legacyDecisionDefinitionId) {
    LOGGER.debug(SKIPPING_DECISION_DEFINITION, legacyDecisionDefinitionId);
  }

  public static void migratingProcessDefinitions() {
    LOGGER.info(MIGRATING_DEFINITIONS, "process");
  }

  public static void migratingProcessDefinition(String legacyProcessDefinitionId) {
    LOGGER.debug(MIGRATING_DEFINITION, "process", legacyProcessDefinitionId);
  }

  public static void migratingProcessDefinitionCompleted(String legacyProcessDefinitionId) {
    LOGGER.debug(MIGRATING_DEFINITION_COMPLETE, "process", legacyProcessDefinitionId);
  }

  public static void migratingProcessInstances() {
    LOGGER.info(MIGRATING_INSTANCES, "process");
  }

  public static void migratingProcessInstance(String legacyProcessInstanceId) {
    LOGGER.debug(MIGRATING_INSTANCE, "process", legacyProcessInstanceId);
  }

  public static void migratingProcessInstanceCompleted(String legacyProcessInstanceId) {
    LOGGER.debug(MIGRATING_INSTANCE_COMPLETE, "process", legacyProcessInstanceId);
  }

  public static void skippingProcessInstanceDueToMissingParent(String legacyProcessInstanceId) {
    LOGGER.debug(SKIPPING_INSTANCE_MISSING_PARENT, "process", legacyProcessInstanceId);
  }

  public static void skippingProcessInstanceDueToMissingDefinition(String legacyProcessInstanceId) {
    LOGGER.debug(SKIPPING_INSTANCE_MISSING_DEFINITION, "process", legacyProcessInstanceId, "process");
  }

  public static void migratingHistoricIncidents() {
    LOGGER.info(MIGRATING_INCIDENTS);
  }

  public static void migratingHistoricIncident(String legacyIncidentId) {
    LOGGER.debug(MIGRATING_INCIDENT, legacyIncidentId);
  }

  public static void migratingHistoricIncidentCompleted(String legacyIncidentId) {
    LOGGER.debug(MIGRATING_INCIDENT_COMPLETED, legacyIncidentId);
  }

  public static void skippingHistoricIncident(String legacyIncidentId) {
    LOGGER.debug(SKIPPING_INCIDENT, legacyIncidentId);
  }

  public static void migratingHistoricVariables() {
    LOGGER.info(MIGRATING_VARIABLES);
  }

  public static void migratingHistoricVariable(String legacyVariableId) {
    LOGGER.debug(MIGRATING_VARIABLE, legacyVariableId);
  }

  public static void migratingHistoricVariableCompleted(String legacyVariableId) {
    LOGGER.debug(MIGRATING_VARIABLE_COMPLETED, legacyVariableId);
  }

  public static void skippingHistoricVariableDueToMissingFlowNode(String legacyVariableId) {
    LOGGER.debug(SKIPPING_VARIABLE_MISSING_FLOW_NODE, legacyVariableId);
  }

  public static void skippingHistoricVariableDueToMissingProcessInstance(String legacyVariableId) {
    LOGGER.debug(SKIPPING_VARIABLE_MISSING_PROCESS, legacyVariableId);
  }

  public static void skippingHistoricVariableDueToMissingTask(String legacyVariableId, String taskId) {
    LOGGER.debug(SKIPPING_VARIABLE_MISSING_TASK, legacyVariableId, taskId);
  }

  public static void skippingHistoricVariableDueToMissingScopeKey(String legacyVariableId) {
    LOGGER.debug(SKIPPING_VARIABLE_MISSING_SCOPE, legacyVariableId);
  }

  public static void migratingHistoricUserTasks() {
    LOGGER.info(MIGRATING_USER_TASKS);
  }

  public static void migratingHistoricUserTask(String legacyUserTaskId) {
    LOGGER.debug(MIGRATING_USER_TASK, legacyUserTaskId);
  }

  public static void migratingHistoricUserTaskCompleted(String legacyUserTaskId) {
    LOGGER.debug(MIGRATING_USER_TASK_COMPLETED, legacyUserTaskId);
  }

  public static void skippingHistoricUserTaskDueToMissingFlowNode(String legacyUserTaskId) {
    LOGGER.debug(SKIPPING_MIGRATING_USER_TASK_MISSING_FLOW_NODE, legacyUserTaskId);
  }

  public static void skippingHistoricUserTaskDueToMissingProcessInstance(String legacyUserTaskId) {
    LOGGER.debug(SKIPPING_USER_TASK_MISSING_PROCESS, legacyUserTaskId);
  }

  public static void migratingHistoricFlowNodes() {
    LOGGER.info(MIGRATING_FLOW_NODES);
  }

  public static void migratingHistoricFlowNode(String legacyFlowNodeId) {
    LOGGER.debug(MIGRATING_FLOW_NODE, legacyFlowNodeId);
  }

  public static void migratingHistoricFlowNodeCompleted(String legacyFlowNodeId) {
    LOGGER.debug(MIGRATING_FLOW_NODE_COMPLETED, legacyFlowNodeId);
  }

  public static void skippingHistoricFlowNode(String legacyFlowNodeId) {
    LOGGER.debug(SKIPPING_FLOW_NODE, legacyFlowNodeId);
  }

  public static void migratingDecisionRequirements() {
    LOGGER.info(MIGRATING_DECISION_REQUIREMENTS);
  }

  public static void migratingDecisionRequirements(String legacyDecisionRequirementsId) {
    LOGGER.debug(MIGRATING_DECISION_REQUIREMENT, legacyDecisionRequirementsId);
  }

  public static void migratingDecisionRequirementsCompleted(String legacyDecisionRequirementsId) {
    LOGGER.debug(MIGRATING_DECISION_REQUIREMENT_COMPLETED, legacyDecisionRequirementsId);
  }
}
