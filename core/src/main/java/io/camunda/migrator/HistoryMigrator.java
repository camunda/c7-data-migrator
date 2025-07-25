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

import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.domain.DecisionDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.migrator.config.C8DataSourceConfigured;
import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.converter.DecisionDefinitionConverter;
import io.camunda.migrator.converter.FlowNodeConverter;
import io.camunda.migrator.converter.IncidentConverter;
import io.camunda.migrator.converter.ProcessDefinitionConverter;
import io.camunda.migrator.converter.ProcessInstanceConverter;
import io.camunda.migrator.converter.UserTaskConverter;
import io.camunda.migrator.converter.VariableConverter;
import io.camunda.migrator.impl.Pagination;
import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import java.util.Date;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.history.HistoricActivityInstance;
import org.camunda.bpm.engine.history.HistoricIncident;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.repository.DecisionDefinition;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.repository.DecisionDefinitionQuery;
import org.camunda.bpm.engine.impl.HistoricActivityInstanceQueryImpl;
import org.camunda.bpm.engine.impl.HistoricIncidentQueryImpl;
import org.camunda.bpm.engine.impl.HistoricProcessInstanceQueryImpl;
import org.camunda.bpm.engine.impl.HistoricTaskInstanceQueryImpl;
import org.camunda.bpm.engine.impl.HistoricVariableInstanceQueryImpl;
import org.camunda.bpm.engine.impl.ProcessDefinitionQueryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Conditional(C8DataSourceConfigured.class)
public class HistoryMigrator {

  private static final Logger LOGGER = LoggerFactory.getLogger(HistoryMigrator.class);

  // Mappers

  @Autowired
  private IdKeyMapper idKeyMapper;

  @Autowired
  private ProcessInstanceMapper processInstanceMapper;

  @Autowired
  private UserTaskMapper userTaskMapper;

  @Autowired
  private VariableMapper variableMapper;

  @Autowired
  private IncidentMapper incidentMapper;

  @Autowired
  private ProcessDefinitionMapper processDefinitionMapper;

  @Autowired
  private DecisionDefinitionMapper decisionDefinitionMapper;

  @Autowired
  private FlowNodeInstanceMapper flowNodeMapper;

  // Services

  @Autowired
  private HistoryService historyService;

  @Autowired
  private RepositoryService repositoryService;

  @Autowired
  protected MigratorProperties migratorProperties;

  @Autowired
  protected DbClient dbClient;

  // Converters

  @Autowired
  private ProcessInstanceConverter processInstanceConverter;

  @Autowired
  private FlowNodeConverter flowNodeConverter;

  @Autowired
  private UserTaskConverter userTaskConverter;

  @Autowired
  private VariableConverter variableConverter;

  @Autowired
  private IncidentConverter incidentConverter;

  @Autowired
  private ProcessDefinitionConverter processDefinitionConverter;

  @Autowired
  private DecisionDefinitionConverter decisionDefinitionConverter;

  protected MigratorMode mode = MIGRATE;

  public void start() {
    if (LIST_SKIPPED.equals(mode)) {
      // TODO: list entities
    } else {
      migrate();
    }
  }

  public void migrate() {
    LOGGER.info("Migrating C7 data...");
    // Start process instance
    migrateProcessDefinitions();
    migrateProcessInstances();
    migrateFlowNodes();
    migrateUserTasks();
    migrateVariables();
    migrateIncidents();
    //      migrateDecisionDefinitions();
  }

  private void migrateDecisionDefinitions() {
    LOGGER.info("Migrating decision definitions");

    DecisionDefinitionQuery legacyDecisionDefinitionQuery = repositoryService.createDecisionDefinitionQuery()
        .orderByDecisionDefinitionId()
        .asc();

    String latestLegacyId = idKeyMapper.findLatestIdByType(IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE);
    if (latestLegacyId != null) {
      LOGGER.debug("Resume functionality not available for decision definitions - starting from beginning");
    }

    new Pagination<DecisionDefinition>()
        .pageSize(getPageSize())
        .query(legacyDecisionDefinitionQuery)
        .callback(legacyDecisionDefinition -> {
          String legacyId = legacyDecisionDefinition.getId();
          LOGGER.debug("Migrating legacy decision definition with id: [{}]", legacyId);
          DecisionDefinitionDbModel dbModel = decisionDefinitionConverter.apply(legacyDecisionDefinition);
          decisionDefinitionMapper.insert(dbModel);
          saveRecord(legacyId, dbModel.decisionDefinitionKey(), IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE);
          LOGGER.debug("Migration of legacy decision definition with id [{}] completed", legacyId);
        });
  }

  public void migrateProcessDefinitions() {
    LOGGER.info("Migrating process definitions");
    if (RETRY_SKIPPED.equals(mode)) {
     // TODO: handle retry
    } else {
      var legacyProcessDefinitionQuery = (ProcessDefinitionQueryImpl) repositoryService.createProcessDefinitionQuery().orderByProcessDefinitionId().asc();
      String latestLegacyId = idKeyMapper.findLatestIdByType(IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);
      if (latestLegacyId != null) {
        legacyProcessDefinitionQuery.idAfter(latestLegacyId);
      }
      new Pagination<ProcessDefinition>()
          .pageSize(getPageSize())
          .query(legacyProcessDefinitionQuery)
          .callback(this::migrateHistoricProcessDefinition);
    }
  }

  private void migrateHistoricProcessDefinition(ProcessDefinition legacyProcessDefinition) {
    String legacyId = legacyProcessDefinition.getId();
    LOGGER.debug("Migrating legacy process definition with id: [{}]", legacyId);
    ProcessDefinitionDbModel dbModel = processDefinitionConverter.apply(legacyProcessDefinition);
    processDefinitionMapper.insert(dbModel);
    saveRecord(legacyId, dbModel.processDefinitionKey(), IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);
    LOGGER.debug("Migration of legacy process definition with id [{}] completed", legacyId);
  }

  public void migrateProcessInstances() {
    LOGGER.info("Migrating process instances");

    if (RETRY_SKIPPED.equals(mode)) {
      // TODO: handle retry
    } else {
      var legacyProcessInstanceQuery = (HistoricProcessInstanceQueryImpl) historyService.createHistoricProcessInstanceQuery().orderByProcessInstanceId().asc();
      String latestLegacyId = idKeyMapper.findLatestIdByType(IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);
      if (latestLegacyId != null) {
        legacyProcessInstanceQuery.idAfter(latestLegacyId);
      }
      new Pagination<HistoricProcessInstance>()
          .pageSize(getPageSize())
          .query(legacyProcessInstanceQuery)
          .callback(this::migrateHistoricProcessInstance);
    }
  }

  private void migrateHistoricProcessInstance(HistoricProcessInstance legacyProcessInstance) {
    String legacyProcessInstanceId = legacyProcessInstance.getId();
    String processDefinitionId = legacyProcessInstance.getProcessDefinitionId();
    LOGGER.debug("Migrating process instance with legacy id: [{}]", legacyProcessInstanceId);
    if(isMigrated(processDefinitionId)) {
      Long processDefinitionKey = findProcessDefinitionKey(processDefinitionId);
      String legacySuperProcessInstanceId = legacyProcessInstance.getSuperProcessInstanceId();
      Long parentProcessInstanceKey = null;
      if (legacySuperProcessInstanceId != null) {
        ProcessInstanceEntity parentInstance = findProcessInstanceByLegacyId(legacySuperProcessInstanceId);
        if (parentInstance != null) {
          parentProcessInstanceKey = parentInstance.processInstanceKey();
        }
      }

      if (parentProcessInstanceKey != null || legacySuperProcessInstanceId == null) {
        // Continue if PI has no parent
        ProcessInstanceDbModel dbModel = processInstanceConverter.apply(legacyProcessInstance, processDefinitionKey, parentProcessInstanceKey);
        processInstanceMapper.insert(dbModel);
        saveRecord(legacyProcessInstanceId, dbModel.processInstanceKey(), IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);
        LOGGER.debug("Migration of legacy process instances with id [{}] completed", legacyProcessInstanceId);
      } else {
        saveRecord(legacyProcessInstanceId, null, IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);
        LOGGER.debug("Migration of legacy process instance with id [{}] skipped. Parent process instance not yet available.", legacyProcessInstanceId);
      }
    } else {
      saveRecord(legacyProcessInstanceId, null, IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);
      LOGGER.debug("Migration of legacy process instance with id [{}] skipped. Process definition not yet available.", legacyProcessInstanceId);
    }
  }

  public void migrateIncidents() {
    LOGGER.info("Migrating incidents");

    if (RETRY_SKIPPED.equals(mode)) {
      // TODO: handle retry
    } else {
      var legacyIncidentQuery = (HistoricIncidentQueryImpl) historyService.createHistoricIncidentQuery().orderByIncidentId().asc();
      String latestLegacyId = idKeyMapper.findLatestIdByType(IdKeyMapper.TYPE.HISTORY_INCIDENT);
      if (latestLegacyId != null) {
        legacyIncidentQuery.idAfter(latestLegacyId);
      }
      new Pagination<HistoricIncident>()
          .pageSize(getPageSize())
          .query(legacyIncidentQuery)
          .callback(this::migrateHistoricIncident);
    }
  }

  private void migrateHistoricIncident(HistoricIncident legacyIncident) {
    String legacyIncidentId = legacyIncident.getId();
    LOGGER.debug("Migrating legacy incident with id: [{}]", legacyIncidentId);
    if (isMigrated(legacyIncident.getProcessInstanceId())) {
      ProcessInstanceEntity legacyProcessInstance = findProcessInstanceByLegacyId(legacyIncident.getProcessInstanceId());
      Long processInstanceKey = legacyProcessInstance.processInstanceKey();
      Long flowNodeInstanceKey = findFlowNodeKey(legacyIncident.getActivityId(), legacyIncident.getProcessInstanceId());
      Long processDefinitionKey = findProcessDefinitionKey(legacyIncident.getProcessDefinitionId());
      Long jobDefinitionKey = null; // TODO Job table doesn't exist yet.
      IncidentDbModel dbModel = incidentConverter.apply(legacyIncident, processDefinitionKey, processInstanceKey, jobDefinitionKey, flowNodeInstanceKey);
      incidentMapper.insert(dbModel);
      saveRecord(legacyIncidentId, dbModel.incidentKey(), IdKeyMapper.TYPE.HISTORY_INCIDENT);
      LOGGER.debug("Migration of legacy incident with id [{}] completed.", legacyIncidentId);
    } else {
      saveRecord(legacyIncidentId, null, IdKeyMapper.TYPE.HISTORY_INCIDENT);
      LOGGER.debug("Migration of legacy incident with id [{}] skipped. Process instance not yet available.", legacyIncidentId);
    }
  }

  public void migrateVariables() {
    LOGGER.info("Migrating variables");

    if (RETRY_SKIPPED.equals(mode)) {
      // TODO: handle retry
    } else {
      var legacyVariableQuery = (HistoricVariableInstanceQueryImpl) historyService.createHistoricVariableInstanceQuery().orderByVariableId()          .asc();
      String latestLegacyId = idKeyMapper.findLatestIdByType(IdKeyMapper.TYPE.HISTORY_VARIABLE);
      if (latestLegacyId != null) {
        legacyVariableQuery.idAfter(latestLegacyId);
      }
      new Pagination<HistoricVariableInstance>()
          .pageSize(getPageSize())
          .query(legacyVariableQuery)
          .callback(this::migrateHistoricVariable);
    }

  }

  private void migrateHistoricVariable(HistoricVariableInstance legacyVariable) {
    String legacyVariableId = legacyVariable.getId();
    LOGGER.debug("Migrating legacy variable with id: [{}]", legacyVariableId);

    if (isMigrated(legacyVariable.getProcessInstanceId())) {
      String legacyProcessInstanceId = legacyVariable.getProcessInstanceId();
      ProcessInstanceEntity processInstance = findProcessInstanceByLegacyId(legacyProcessInstanceId);
      if (isMigrated(legacyVariable.getActivityInstanceId())) {
        Long processInstanceKey = processInstance.processInstanceKey();
        Long scopeKey = findFlowNodeKey(legacyVariable.getActivityInstanceId()); // TODO does this cover scope correctly?
        VariableDbModel dbModel = variableConverter.apply(legacyVariable, processInstanceKey, scopeKey);
        variableMapper.insert(dbModel);
        saveRecord(legacyVariableId, dbModel.variableKey(), IdKeyMapper.TYPE.HISTORY_VARIABLE);
        LOGGER.debug("Migration of legacy variable with id [{}] completed.", legacyVariableId);
      } else {
        saveRecord(legacyVariableId, null, IdKeyMapper.TYPE.HISTORY_VARIABLE);
        LOGGER.debug("Migration of legacy variable with id [{}] skipped. Activity instance not yet available.", legacyVariableId);
      }
    } else {
      saveRecord(legacyVariableId, null, IdKeyMapper.TYPE.HISTORY_VARIABLE);
      LOGGER.debug("Migration of legacy variable with id [{}] skipped. Process instance not yet available.", legacyVariableId);
    }
  }

  public void migrateUserTasks() {
    LOGGER.info("Migrating user tasks");

    if (RETRY_SKIPPED.equals(mode)) {
      // TODO: handle retry
    } else {
      var legacyTaskQuery = (HistoricTaskInstanceQueryImpl) historyService.createHistoricTaskInstanceQuery().orderByTaskId().asc();
      String latestLegacyId = idKeyMapper.findLatestIdByType(IdKeyMapper.TYPE.HISTORY_USER_TASK);
      if (latestLegacyId != null) {
        legacyTaskQuery.idAfter(latestLegacyId);
      }
      new Pagination<HistoricTaskInstance>()
          .pageSize(getPageSize())
          .query(legacyTaskQuery)
          .callback(this::migrateHistoricUserTask);
    }
  }

  private void migrateHistoricUserTask(HistoricTaskInstance legacyUserTask) {
    String legacyUserTaskId = legacyUserTask.getId();
    LOGGER.debug("Migrating legacy user task with id: [{}]", legacyUserTaskId);
    ProcessInstanceEntity processInstance = findProcessInstanceByLegacyId(legacyUserTask.getProcessInstanceId());
    if (isMigrated(legacyUserTask.getProcessInstanceId())) {
      if (isMigrated(legacyUserTask.getActivityInstanceId())) {
        Long elementInstanceKey = findFlowNodeKey(legacyUserTask.getActivityInstanceId());
        Long processDefinitionKey = findProcessDefinitionKey(legacyUserTask.getProcessDefinitionId());
        UserTaskDbModel dbModel = userTaskConverter.apply(legacyUserTask, processDefinitionKey, processInstance, elementInstanceKey);
        userTaskMapper.insert(dbModel);
        saveRecord(legacyUserTaskId, dbModel.userTaskKey(), IdKeyMapper.TYPE.HISTORY_USER_TASK);
        LOGGER.debug("Migration of legacy user task with id [{}] completed.", legacyUserTaskId);
      } else {
        saveRecord(legacyUserTaskId, null, IdKeyMapper.TYPE.HISTORY_USER_TASK);
        LOGGER.debug("Migration of legacy user task with id [{}] skipped. Flow node instance yet not available.", legacyUserTaskId);
      }
    } else {
      saveRecord(legacyUserTaskId, null, IdKeyMapper.TYPE.HISTORY_USER_TASK);
      LOGGER.debug("Migration of legacy user task with id [{}] skipped. Process instance [{}] not yet available.", legacyUserTaskId, legacyUserTask.getProcessInstanceId());
    }
  }

  public void migrateFlowNodes() {
    LOGGER.info("Migrating flow nodes");

    if (RETRY_SKIPPED.equals(mode)) {
      // TODO: handle retry
    } else {
      var legacyFlowNodeQuery = (HistoricActivityInstanceQueryImpl) historyService.createHistoricActivityInstanceQuery().orderByHistoricActivityInstanceId().asc();
      String latestLegacyId = idKeyMapper.findLatestIdByType(IdKeyMapper.TYPE.HISTORY_FLOW_NODE);
      if (latestLegacyId != null) {
        legacyFlowNodeQuery.idAfter(latestLegacyId);
      }
      new Pagination<HistoricActivityInstance>()
          .pageSize(getPageSize())
          .query(legacyFlowNodeQuery)
          .callback(this::migrateHistoricFlowNode);
    }
  }

  private void migrateHistoricFlowNode(HistoricActivityInstance legacyFlowNode) {
    String legacyFlowNodeId = legacyFlowNode.getId();
    LOGGER.debug("Migrating legacy flow node with id: [{}]", legacyFlowNodeId);
    if (isMigrated(legacyFlowNode.getProcessInstanceId())) {
      ProcessInstanceEntity processInstance = findProcessInstanceByLegacyId(legacyFlowNode.getProcessInstanceId());
      Long processInstanceKey = processInstance.processInstanceKey();
      Long processDefinitionKey = findProcessDefinitionKey(legacyFlowNode.getProcessDefinitionId());
      FlowNodeInstanceDbModel dbModel = flowNodeConverter.apply(legacyFlowNode, processDefinitionKey, processInstanceKey);
      flowNodeMapper.insert(dbModel);
      saveRecord(legacyFlowNodeId, dbModel.flowNodeInstanceKey(), IdKeyMapper.TYPE.HISTORY_FLOW_NODE);
      LOGGER.debug("Migration of legacy flow node with id [{}] completed.", legacyFlowNodeId);
    } else {
      saveRecord(legacyFlowNodeId, null, IdKeyMapper.TYPE.HISTORY_FLOW_NODE);
      LOGGER.debug("Migration of legacy flow node with id [{}] skipped. Process instance not yet available.", legacyFlowNodeId);
    }
  }

  protected ProcessInstanceEntity findProcessInstanceByLegacyId(String processInstanceId) {
    if (processInstanceId == null)
      return null;

    Long key = idKeyMapper.findKeyById(processInstanceId);
    if (key == null) {
      return null;
    }

    return processInstanceMapper.findOne(key);
  }

  private boolean isMigrated(String id) {
    return dbClient.checkHasKey(id);
  }

  protected void saveRecord(String entityId, Long entityKey, IdKeyMapper.TYPE type) {
    if (RETRY_SKIPPED.equals(mode)) {
      dbClient.updateKeyById(entityId, entityKey, type);
    } else if (MIGRATE.equals(mode)) {
      dbClient.insert(entityId, entityKey, type);
    }
  }

  private Long findProcessDefinitionKey(String processDefinitionId) {
    Long key = idKeyMapper.findKeyById(processDefinitionId);
    if (key == null) {
      return null;
    }

    List<ProcessDefinitionEntity> processDefinitions = processDefinitionMapper.search(
        ProcessDefinitionDbQuery.of(b -> b.filter(value -> value.processDefinitionKeys(key))));

    if (!processDefinitions.isEmpty()) {
      return processDefinitions.get(0).processDefinitionKey();
    } else {
      return null;
    }
  }

  private Long findFlowNodeKey(String activityId, String processInstanceId) {
    Long key = idKeyMapper.findKeyById(processInstanceId);
    if (key == null) {
      return null;
    }

    List<FlowNodeInstanceEntity> flowNodes = flowNodeMapper.search(FlowNodeInstanceDbQuery.of(
        b -> b.filter(FlowNodeInstanceFilter.of(f -> f.flowNodeIds(activityId).flowNodeInstanceKeys(key)))));

    if (!flowNodes.isEmpty()) {
      return flowNodes.get(0).flowNodeInstanceKey();
    } else {
      return null;
    }
  }

  private Long findFlowNodeKey(String activityInstanceId) {
    Long key = idKeyMapper.findKeyById(activityInstanceId);
    if (key == null) {
      return null;
    }

    List<FlowNodeInstanceEntity> flowNodes = flowNodeMapper.search(
        FlowNodeInstanceDbQuery.of(b -> b.filter(f -> f.flowNodeInstanceKeys(key))));

    if (!flowNodes.isEmpty()) {
      return flowNodes.get(0).flowNodeInstanceKey();
    } else {
      return null;
    }
  }

  public int getPageSize() {
    return migratorProperties.getPageSize();
  }

}
