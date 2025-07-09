/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessInstanceDbQuery;
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
import io.camunda.migrator.persistence.IdKeyDbModel;
import io.camunda.migrator.persistence.IdKeyMapper;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
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

  public void migrate() {
    LOGGER.info("Migrating C7 data...");
    // Start process instance
    migrateProcessDefinitions();
    migrateProcessInstances();
    migrateFlowNodes();
    migrateUserTasks();
    migrateVariables();
    migrateIncidents();

    //migrateDecisionDefinitions();
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
        .batchSize(getBatchSize())
        .query(legacyDecisionDefinitionQuery)
        .callback(legacyDecisionDefinition -> {
          DecisionDefinitionDbModel dbModel = decisionDefinitionConverter.apply(legacyDecisionDefinition);
          decisionDefinitionMapper.insert(dbModel);
          String legacyId = legacyDecisionDefinition.getId();
          insertKeyIdMapping(legacyId, dbModel.decisionDefinitionKey(), IdKeyMapper.TYPE.HISTORY_DECISION_INSTANCE);
          LOGGER.debug("Migration of legacy decision definition with id '{}' completed", legacyId);
        });
  }

  private void migrateProcessDefinitions() {
    LOGGER.info("Migrating process definitions");

    ProcessDefinitionQueryImpl legacyProcessDefinitionQuery = (ProcessDefinitionQueryImpl) repositoryService.createProcessDefinitionQuery()
        .orderByProcessDefinitionId()
        .asc();

    String latestLegacyId = idKeyMapper.findLatestIdByType(IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);
    if (latestLegacyId != null) {
      legacyProcessDefinitionQuery.idAfter(latestLegacyId);
    }

    new Pagination<ProcessDefinition>()
        .batchSize(getBatchSize())
        .query(legacyProcessDefinitionQuery)
        .callback(legacyProcessDefinition -> {
          ProcessDefinitionDbModel dbModel = processDefinitionConverter.apply(legacyProcessDefinition);
          processDefinitionMapper.insert(dbModel);
          String legacyId = legacyProcessDefinition.getId();
          insertKeyIdMapping(legacyId, dbModel.processDefinitionKey(), IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);
          LOGGER.debug("Migration of legacy process definition with id '{}' completed", legacyId);
        });
  }

  private void migrateProcessInstances() {
    LOGGER.info("Migrating process instances");

    HistoricProcessInstanceQueryImpl legacyProcessInstanceQuery = (HistoricProcessInstanceQueryImpl) historyService.createHistoricProcessInstanceQuery()
        .orderByProcessInstanceId()
        .asc();

    String latestLegacyId = idKeyMapper.findLatestIdByType(IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);
    if (latestLegacyId != null) {
      legacyProcessInstanceQuery.idAfter(latestLegacyId);
    }

    new Pagination<HistoricProcessInstance>()
        .batchSize(getBatchSize())
        .query(legacyProcessInstanceQuery)
        .callback(legacyProcessInstance -> {
          String legacyProcessInstanceId = legacyProcessInstance.getId();

          Long processDefinitionKey = findProcessDefinitionKey(legacyProcessInstance.getProcessDefinitionId());
          if (processDefinitionKey != null) {
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
              insertKeyIdMapping(legacyProcessInstanceId, dbModel.processInstanceKey(), IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);
              LOGGER.debug("Migration of legacy process instances with id '{}' completed", legacyProcessInstanceId);
            } else {
              LOGGER.debug(
                  "Migration of legacy process instance with id '{}' skipped. Parent process instance not yet available.", legacyProcessInstanceId);
            }
          } else {
            LOGGER.debug(
                "Migration of legacy process instance with id '{}' skipped. Process definition not yet available.",
                legacyProcessInstanceId);
          }
        });
  }

  private void migrateIncidents() {
    LOGGER.info("Migrating incidents");

    HistoricIncidentQueryImpl legacyIncidentQuery = (HistoricIncidentQueryImpl) historyService.createHistoricIncidentQuery()
        .orderByIncidentId()
        .asc();

    String latestLegacyId = idKeyMapper.findLatestIdByType(IdKeyMapper.TYPE.HISTORY_INCIDENT);
    if (latestLegacyId != null) {
      legacyIncidentQuery.idAfter(latestLegacyId);
    }

    new Pagination<HistoricIncident>()
        .batchSize(getBatchSize())
        .query(legacyIncidentQuery)
        .callback(legacyIncident -> {
          String legacyIncidentId = legacyIncident.getId();
          ProcessInstanceEntity legacyProcessInstance = findProcessInstanceByLegacyId(legacyIncident.getProcessInstanceId());
          if (legacyProcessInstance != null) {
            Long processInstanceKey = legacyProcessInstance.processInstanceKey();
            if (processInstanceKey != null) {
              Long flowNodeInstanceKey = findFlowNodeKey(legacyIncident.getActivityId(), legacyIncident.getProcessInstanceId());
              Long processDefinitionKey = findProcessDefinitionKey(legacyIncident.getProcessDefinitionId());
              Long jobDefinitionKey = null; // TODO Job table doesn't exist yet.
              IncidentDbModel dbModel = incidentConverter.apply(legacyIncident, processDefinitionKey, processInstanceKey, jobDefinitionKey, flowNodeInstanceKey);
              incidentMapper.insert(dbModel);
              insertKeyIdMapping(legacyIncidentId, dbModel.incidentKey(), IdKeyMapper.TYPE.HISTORY_INCIDENT);
              LOGGER.debug("Migration of legacy incident with id '{}' completed.", legacyIncidentId);
            } else {
              LOGGER.debug("Migration of legacy incident with id '{}' skipped. Process instance not yet available.",
                  legacyIncidentId);
            }
          }
        });
  }

  private void migrateVariables() {
    LOGGER.info("Migrating variables");

    HistoricVariableInstanceQueryImpl legacyVariableQuery = (HistoricVariableInstanceQueryImpl) historyService.createHistoricVariableInstanceQuery()
        .orderByVariableId()
        .asc();

    String latestLegacyId = idKeyMapper.findLatestIdByType(IdKeyMapper.TYPE.HISTORY_VARIABLE);
    if (latestLegacyId != null) {
      legacyVariableQuery.idAfter(latestLegacyId);
    }

    new Pagination<HistoricVariableInstance>()
        .batchSize(getBatchSize())
        .query(legacyVariableQuery)
        .callback(legacyVariable -> {
          String legacyVariableId = legacyVariable.getId();
          String legacyProcessInstanceId = legacyVariable.getProcessInstanceId();
          ProcessInstanceEntity processInstance = findProcessInstanceByLegacyId(legacyProcessInstanceId);
          if (processInstance != null) {
            Long processInstanceKey = processInstance.processInstanceKey();
            Long scopeKey = findFlowNodeKey(legacyVariable.getActivityInstanceId()); // TODO does this cover scope correctly?
            if (scopeKey != null) {
              VariableDbModel dbModel = variableConverter.apply(legacyVariable, processInstanceKey, scopeKey);
              variableMapper.insert(dbModel);
              insertKeyIdMapping(legacyVariableId, dbModel.variableKey(), IdKeyMapper.TYPE.HISTORY_VARIABLE);
              LOGGER.debug("Migration of legacy variable with id '{}' completed.", legacyVariableId);
            } else {
              LOGGER.debug("Migration of legacy variable with id '{}' skipped. Activity instance not yet available.",
                  legacyVariableId);
            }
          } else {
            LOGGER.debug("Migration of legacy variable with id '{}' skipped. Process instance not yet available.",
                legacyVariableId);
          }
        });
  }

  private void migrateUserTasks() {
    LOGGER.info("Migrating user tasks");

    HistoricTaskInstanceQueryImpl legacyTaskQuery = (HistoricTaskInstanceQueryImpl) historyService.createHistoricTaskInstanceQuery()
        .orderByTaskId()
        .asc();

    String latestLegacyId = idKeyMapper.findLatestIdByType(IdKeyMapper.TYPE.HISTORY_USER_TASK);
    if (latestLegacyId != null) {
      legacyTaskQuery.idAfter(latestLegacyId);
    }

    new Pagination<HistoricTaskInstance>()
        .batchSize(getBatchSize())
        .query(legacyTaskQuery)
        .callback(legacyUserTask -> {
          String legacyUserTaskId = legacyUserTask.getId();
          ProcessInstanceEntity processInstance = findProcessInstanceByLegacyId(legacyUserTask.getProcessInstanceId());
          if (processInstance != null) {
            Long elementInstanceKey = findFlowNodeKey(legacyUserTask.getActivityInstanceId());
            if (elementInstanceKey != null) {
              Long processDefinitionKey = findProcessDefinitionKey(legacyUserTask.getProcessDefinitionId());
              UserTaskDbModel dbModel = userTaskConverter.apply(legacyUserTask, processDefinitionKey, processInstance,
                  elementInstanceKey);
              userTaskMapper.insert(dbModel);
              insertKeyIdMapping(legacyUserTaskId, dbModel.userTaskKey(), IdKeyMapper.TYPE.HISTORY_USER_TASK);
              LOGGER.debug("Migration of legacy user task with id '{}' completed.", legacyUserTaskId);
            } else {
              LOGGER.debug("Migration of legacy user task with id '{}' skipped. Flow node instance yet not available.",
                  legacyUserTaskId);
            }
          } else {
            LOGGER.debug("Migration of legacy user task with id '{}' skipped. Process instance '{}' not yet available.",
                legacyUserTaskId, legacyUserTask.getProcessInstanceId());
          }
        });
  }

  private void migrateFlowNodes() {
    LOGGER.info("Migrating flow nodes");

    HistoricActivityInstanceQueryImpl legacyFlowNodeQuery = (HistoricActivityInstanceQueryImpl) historyService.createHistoricActivityInstanceQuery()
        .orderByHistoricActivityInstanceId()
        .asc();

    String latestLegacyId = idKeyMapper.findLatestIdByType(IdKeyMapper.TYPE.HISTORY_FLOW_NODE);
    if (latestLegacyId != null) {
      legacyFlowNodeQuery.idAfter(latestLegacyId);
    }

    new Pagination<HistoricActivityInstance>()
        .batchSize(getBatchSize())
        .query(legacyFlowNodeQuery)
        .callback(legacyFlowNode -> {
          String legacyFlowNodeId = legacyFlowNode.getId();
          ProcessInstanceEntity processInstance = findProcessInstanceByLegacyId(legacyFlowNode.getProcessInstanceId());
          if (processInstance != null) {
            Long processInstanceKey = processInstance.processInstanceKey();
            Long processDefinitionKey = findProcessDefinitionKey(legacyFlowNode.getProcessDefinitionId());
            FlowNodeInstanceDbModel dbModel = flowNodeConverter.apply(legacyFlowNode, processDefinitionKey, processInstanceKey);
            flowNodeMapper.insert(dbModel);
            insertKeyIdMapping(legacyFlowNodeId, dbModel.flowNodeInstanceKey(), IdKeyMapper.TYPE.HISTORY_FLOW_NODE);
            LOGGER.debug("Migration of legacy flow node with id '{}' completed.", legacyFlowNodeId);
          } else {
            LOGGER.debug("Migration of legacy flow node with id '{}' skipped. Process instance not yet available.",
                legacyFlowNodeId);
          }
        });
  }

  protected void insertKeyIdMapping(String id, Long key, IdKeyMapper.TYPE type) {
    var idKeyDbModel = new IdKeyDbModel();
    idKeyDbModel.setId(id);
    idKeyDbModel.setInstanceKey(key);
    idKeyDbModel.setType(type);
    idKeyMapper.insert(idKeyDbModel);
  }

  protected ProcessInstanceEntity findProcessInstanceByLegacyId(String processInstanceId) {
    if (processInstanceId == null)
      return null;

    Long key = idKeyMapper.findKeyById(processInstanceId);
    if (key == null) {
      return null;
    }

    List<ProcessInstanceEntity> processInstances = processInstanceMapper.search(
        ProcessInstanceDbQuery.of(b -> b.filter(value -> value.processInstanceKeys(key))));

    if (!processInstances.isEmpty()) {
      return processInstances.get(0);
    } else {
      return null;
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

  public int getBatchSize() {
    return migratorProperties.getBatchSize();
  }

}
