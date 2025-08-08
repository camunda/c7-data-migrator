/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.history;

import static io.camunda.migrator.qa.util.FilterFactory.incFilter;
import static io.camunda.migrator.qa.util.FilterFactory.procDefFilter;
import static io.camunda.migrator.qa.util.FilterFactory.procInstFilter;
import static io.camunda.migrator.qa.util.FilterFactory.userTasksFilter;
import static io.camunda.migrator.qa.util.FilterFactory.varFilter;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.MigratorMode;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryMigrationRetryTest extends HistoryMigrationAbstractTest {

  @Autowired
  private HistoryService historyService;

  @Autowired
  private ManagementService managementService;

    @Test
    public void shouldMigratePreviouslySkippedProcessDefinition() {
        // given state in c7
        deployer.deployCamunda7Process("userTaskProcess.bpmn");

        // and the process definition is manually set as skipped
        String legacyId = repositoryService.createProcessDefinitionQuery().singleResult().getId();
        dbClient.insert(legacyId, null, IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);

        // when history migration is retried
        historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
        historyMigrator.migrate();

        // then process definition is migrated and no longer skipped
        assertThat(searchHistoricProcessDefinitions(procDefFilter().processDefinitionIds("userTaskProcessId")).size()).isEqualTo(1);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION)).isEqualTo(0);
    }

  @Test
  public void shouldMigrateOnlyPreviouslySkippedElementsOnRetry() {
    // given state in c7
    deployer.deployCamunda7Process("includeAllSupportedElementsProcess.bpmn");
    for(int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("allElementsProcessId");
    }
    completeAllUserTasksWithDefaultUserTaskId();
    executeAllJobsWithRetry();

    // and some entities manually set as skipped
    String procDefId = repositoryService.createProcessDefinitionQuery().singleResult().getId();
    String procInstId = historyService.createHistoricProcessInstanceQuery().list().getFirst().getId();
    String actInstId = historyService.createHistoricActivityInstanceQuery().activityType("userTask").processInstanceId(procInstId).list().getFirst().getId();
    String taskId = historyService.createHistoricTaskInstanceQuery().activityInstanceIdIn(actInstId).list().getFirst().getId();
    String incidentId = historyService.createHistoricIncidentQuery().processInstanceId(procInstId).list().getFirst().getId();
    String varId = historyService.createHistoricVariableInstanceQuery().activityInstanceIdIn(actInstId).singleResult().getId();

    dbClient.insert(procDefId, null, IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);
    dbClient.insert(procInstId, null, IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);
    dbClient.insert(actInstId, null, IdKeyMapper.TYPE.HISTORY_FLOW_NODE);
    dbClient.insert(taskId, null, IdKeyMapper.TYPE.HISTORY_USER_TASK);
    dbClient.insert(varId, null, IdKeyMapper.TYPE.HISTORY_VARIABLE);
    dbClient.insert(incidentId, null, IdKeyMapper.TYPE.HISTORY_INCIDENT);

    // when migration is retried
    historyMigrator.setMode(MigratorMode.RETRY_SKIPPED);
    historyMigrator.migrate();

    // then only previously skipped entities are migrated
    assertThat(searchHistoricProcessDefinitions(procDefFilter().processDefinitionIds("allElementsProcessId")).size()).isEqualTo(1);
    List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(procInstFilter().processDefinitionIds("allElementsProcessId"));
    assertThat(processInstances.size()).isEqualTo(1);
    assertThat(searchHistoricUserTasks(userTasksFilter().processInstanceKeys(processInstances.getFirst().processInstanceKey())).size()).isEqualTo(1);
    assertThat(searchHistoricIncidents(incFilter().processDefinitionIds("allElementsProcessId")).size()).isEqualTo(1);
    assertThat(searchHistoricVariables(varFilter().names("userTaskVar")).size()).isEqualTo(1);

    // and nothing marked as skipped
    assertThat(dbClient.checkHasKey(procDefId)).isTrue();
    assertThat(dbClient.checkHasKey(procInstId)).isTrue();
    assertThat(dbClient.checkHasKey(actInstId)).isTrue();
    assertThat(dbClient.checkHasKey(taskId)).isTrue();
    assertThat(dbClient.checkHasKey(incidentId)).isTrue();
    assertThat(dbClient.checkHasKey(varId)).isTrue();
  }

  @Test
    public void shouldNotMigratePreviouslySkippedElementsOnRerun() {
        // given state in c7
        deployer.deployCamunda7Process("userTaskProcess.bpmn");
        for(int i = 0; i < 5; i++) {
            runtimeService.startProcessInstanceByKey("userTaskProcessId");
        }
        completeAllUserTasksWithDefaultUserTaskId();

        // and some entities manually set as skipped
        String procInstId = historyService.createHistoricProcessInstanceQuery().list().getFirst().getId();
        String actInstId = historyService.createHistoricActivityInstanceQuery().activityType("userTask").processInstanceId(procInstId).list().getFirst().getId();
        String taskId = historyService.createHistoricTaskInstanceQuery().activityInstanceIdIn(actInstId).list().getFirst().getId();

        dbClient.insert(procInstId, null, IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);
        dbClient.insert(actInstId, null, IdKeyMapper.TYPE.HISTORY_FLOW_NODE);
        dbClient.insert(taskId, null, IdKeyMapper.TYPE.HISTORY_USER_TASK);

        // when migration is run on migrate mode
        historyMigrator.migrate();

        // then only non skipped entities are migrated
        assertThat(searchHistoricProcessDefinitions(procDefFilter().processDefinitionIds("userTaskProcessId")).size()).isEqualTo(1);
        List<ProcessInstanceEntity> processInstances = searchHistoricProcessInstances(procInstFilter().processDefinitionIds("userTaskProcessId"));
        assertThat(processInstances.size()).isEqualTo(4);

        // and skipped entities are still skipped
        assertThat(dbClient.checkHasKey(procInstId)).isFalse();
        assertThat(dbClient.checkHasKey(actInstId)).isFalse();
        assertThat(dbClient.checkHasKey(taskId)).isFalse();
    }

  private void executeAllJobsWithRetry() {
    var jobs = managementService.createJobQuery().list();

    // Try executing the job multiple times to ensure incident is created
    for (var job : jobs) {
      for (int i = 0; i < 3; i++) {
        try {
          managementService.executeJob(job.getId());
        } catch (Exception e) {
          // expected - job will fail due to empty delegate expression
        }
      }
    }
  }
}
