/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.history;

import static io.camunda.migrator.qa.util.FilterFactory.incFilter;
import static io.camunda.migrator.qa.util.FilterFactory.procInstFilter;
import static io.camunda.migrator.qa.util.FilterFactory.userTasksFilter;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.HistoryMigrator;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.Map;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({OutputCaptureExtension.class})
public class HistoryMigrationSkippingTest extends HistoryMigrationAbstractTest {

    @RegisterExtension
    protected LogCapturer logs = LogCapturer.create().captureForType(HistoryMigrator.class, Level.DEBUG);

    @Autowired
    private ManagementService managementService;

    @Autowired
    protected HistoryService historyService;

    @Test
    public void shouldSkipElementsWhenProcessDefinitionIsSkipped() {
        // given state in c7
        deployer.deployCamunda7Process("userTaskProcess.bpmn");

        for(int i = 0; i < 5; i++) {
            runtimeService.startProcessInstanceByKey("userTaskProcessId");
        }
        for (Task task : taskService.createTaskQuery().list()) {
            taskService.complete(task.getId());
        }

        // and the process definition is manually set as skipped
        String legacyId = repositoryService.createProcessDefinitionQuery().singleResult().getId();
        dbClient.insert(legacyId, null, IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);

        // when history is migrated
        historyMigrator.migrate();

        // then nothing was migrated
        assertThat(searchHistoricProcessInstances(procInstFilter().processDefinitionIds("userTaskProcessId")).size()).isEqualTo(0);

        // and all elements for the definition were skipped
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE)).isEqualTo(5);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_FLOW_NODE)).isEqualTo(15);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_USER_TASK)).isEqualTo(5);
    }

    @Test
    public void shouldNotMigrateAlreadySkippedProcessInstance() {
        // given state in c7
        deployer.deployCamunda7Process("userTaskProcess.bpmn");
        var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");

        // and the process instance is manually set as skipped
        dbClient.insert(processInstance.getId(), null, IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);

        // when history is migrated
        historyMigrator.migrate();

        // then no process instances were migrated
        assertThat(searchHistoricProcessInstances(procInstFilter().processDefinitionIds("userTaskProcessId")).size()).isEqualTo(0);

        // verify the process instance was skipped exactly once
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE)).isEqualTo(1);

        // and verify logs don't contain any additional skip operations for this process instance
        logs.assertDoesNotContain("Migration of historic process instance with legacyId [" + processInstance.getId() + "] skipped");
    }

    @Test
    public void shouldSkipUserTasksWhenProcessInstanceIsSkipped() {
        // given state in c7
        deployer.deployCamunda7Process("userTaskProcess.bpmn");
        var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
        var task = taskService.createTaskQuery().singleResult();
        taskService.complete(task.getId());

        // and the process instance is manually set as skipped
        dbClient.insert(processInstance.getId(), null, IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);

        // when history is migrated
        historyMigrator.migrate();

        // then process instance and user task were skipped
        assertThat(searchHistoricProcessInstances(procInstFilter().processDefinitionIds("userTaskProcessId")).size()).isEqualTo(0);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_USER_TASK)).isEqualTo(1);
        logs.assertContains("Migration of historic user task with legacyId [" + task.getId() + "] skipped");
    }

    @Test
    public void shouldNotMigrateAlreadySkippedUserTask() {
        // given state in c7
        deployer.deployCamunda7Process("userTaskProcess.bpmn");
        runtimeService.startProcessInstanceByKey("userTaskProcessId");
        var task = taskService.createTaskQuery().singleResult();
        taskService.complete(task.getId());

        // and the user task is manually set as skipped
        String taskId = historyService.createHistoricTaskInstanceQuery().singleResult().getId();
        dbClient.insert(taskId, null, IdKeyMapper.TYPE.HISTORY_USER_TASK);

        // when history is migrated
        historyMigrator.migrate();

        // then process instance was migrated but user task was not
        var historicProcesses = searchHistoricProcessInstances(procInstFilter().processDefinitionIds("userTaskProcessId"));
        assertThat(historicProcesses.size()).isEqualTo(1);
        var processInstance = historicProcesses.getFirst();
        assertThat(searchHistoricUserTasks(userTasksFilter().processInstanceKeys(processInstance.processInstanceKey()))).isEmpty();

        // verify the task was skipped exactly once
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_USER_TASK)).isEqualTo(1);

        // and verify logs don't contain any additional skip operations for this task
        logs.assertDoesNotContain("Migration of legacy user task with id [" + task.getId() + "] skipped");

    }

    @Test
    public void shouldSkipIncidentsWhenProcessInstanceIsSkipped() {
        // given state in c7
        deployer.deployCamunda7Process("failingServiceTaskProcess.bpmn");
        var processInstance = runtimeService.startProcessInstanceByKey("failingServiceTaskProcessId");

        // execute the job to trigger the incident
        var jobs = managementService.createJobQuery().list();
        assertThat(jobs).hasSize(1);

        // Try executing the job multiple times to ensure incident is created
        for (int i = 0; i < 3; i++) {
            try {
                managementService.executeJob(jobs.getFirst().getId());
            } catch (Exception e) {
                // expected - job will fail due to empty delegate expression
            }
        }
        assertThat(historyService.createHistoricIncidentQuery().count()).as("Expected one incident to be created").isEqualTo(1);
        String incidentId = historyService.createHistoricIncidentQuery().singleResult().getId();

        // and the process instance is manually set as skipped
        dbClient.insert(processInstance.getId(), null, IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);

        // when history is migrated
        historyMigrator.migrate();

        // then process instance and incidents were skipped
        assertThat(searchHistoricProcessInstances(procInstFilter().processDefinitionIds("failingServiceTaskProcessId")).size()).isEqualTo(0);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_INCIDENT)).isEqualTo(1);
        logs.assertContains("Migration of historic incident with legacyId [" + incidentId + "] skipped");
    }

    @Test
    public void shouldNotMigrateAlreadySkippedIncident() {
        // given state in c7 with a failing service task
        deployer.deployCamunda7Process("failingServiceTaskProcess.bpmn");
        var processInstance = runtimeService.startProcessInstanceByKey("failingServiceTaskProcessId");

        // execute the job to trigger the incident
        var jobs = managementService.createJobQuery().list();
        assertThat(jobs).hasSize(1);

        // Try executing the job multiple times to ensure incident is created
        for (int i = 0; i < 3; i++) {
            try {
                managementService.executeJob(jobs.getFirst().getId());
            } catch (Exception e) {
                // expected - job will fail due to empty delegate expression
            }
        }

        // and manually mark the incident as skipped
        String incidentId = historyService.createHistoricIncidentQuery().singleResult().getId();
        dbClient.insert(incidentId, null, IdKeyMapper.TYPE.HISTORY_INCIDENT);

        // when history is migrated
        historyMigrator.migrate();

        // then process instance was migrated but incident was not
        var historicProcesses = searchHistoricProcessInstances(procInstFilter().processDefinitionIds("failingServiceTaskProcessId"));
        assertThat(historicProcesses.size()).isEqualTo(1);
        ProcessInstanceEntity c8ProcessInstance = historicProcesses.getFirst();
        assertThat(searchHistoricIncidents(incFilter().processDefinitionIds(c8ProcessInstance.processDefinitionId()))).isEmpty();

        // verify the incident was skipped exactly once
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_INCIDENT)).isEqualTo(1);

        // and verify logs don't contain any additional skip operations for this incident
        logs.assertDoesNotContain("Skipping historic incident " + incidentId);
    }

    @Disabled("TODO: https://github.com/camunda/camunda-bpm-platform/issues/5331")
    @Test
    public void shouldNotMigrateIncidentsWhenJobIsSkipped() {
        // given state in c7 with a failing service task
        deployer.deployCamunda7Process("failingServiceTaskProcess.bpmn");
        var processInstance = runtimeService.startProcessInstanceByKey("failingServiceTaskProcessId");

        // execute the job to trigger the incident
        var jobs = managementService.createJobQuery().list();
        assertThat(jobs).hasSize(1);
        var job = jobs.getFirst();

        try {
            managementService.executeJob(job.getId());
        } catch (Exception e) {
            // expected - job will fail
        }

        // and manually mark the job as skipped
        dbClient.insert(job.getId(), null, IdKeyMapper.TYPE.HISTORY_FLOW_NODE);

        // when history is migrated
        historyMigrator.migrate();

        // then process instance was migrated but incident was skipped due to skipped job
        var historicProcesses = searchHistoricProcessInstances(procInstFilter().processDefinitionIds("failingServiceTaskProcessId"));
        assertThat(historicProcesses.size()).isEqualTo(1);
        ProcessInstanceEntity c8processInstance = historicProcesses.getFirst();
        assertThat(searchHistoricIncidents(incFilter().processDefinitionIds(c8processInstance.processDefinitionId()))).isEmpty();

        // verify the incident was skipped
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_INCIDENT)).isEqualTo(1);
    }

    @Test
    public void shouldSkipVariablesWhenProcessInstanceIsSkipped() {
        // given state in c7
        deployer.deployCamunda7Process("userTaskProcess.bpmn");
        var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId",
            Map.of("testVar", "testValue"));
        var task = taskService.createTaskQuery().singleResult();
        taskService.complete(task.getId());

        // and the process instance is manually set as skipped
        dbClient.insert(processInstance.getId(), null, IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE);

        // when history is migrated
        historyMigrator.migrate();

        // then process instance and variables were skipped
        assertThat(searchHistoricProcessInstances(procInstFilter().processDefinitionIds("userTaskProcessId")).size()).isEqualTo(0);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_VARIABLE)).isEqualTo(1);
    }

    @Test
    public void shouldNotMigrateAlreadySkippedVariable() {
        // given state in c7
        deployer.deployCamunda7Process("userTaskProcess.bpmn");
        var processInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId",
            Map.of("testVar", "testValue", "anotherVar", "anotherValue"));
        var task = taskService.createTaskQuery().singleResult();
        taskService.complete(task.getId());

        // Find a variable to mark as skipped
        var historicVariables = historyService.createHistoricVariableInstanceQuery().list();
        assertThat(historicVariables).hasSize(2);
        var variableToSkip = historicVariables.getFirst();

        // and the variable is manually set as skipped
        dbClient.insert(variableToSkip.getId(), null, IdKeyMapper.TYPE.HISTORY_VARIABLE);

        // when history is migrated
        historyMigrator.migrate();

        // then process instance was migrated but the variable was not
        var historicProcesses = searchHistoricProcessInstances(procInstFilter().processDefinitionIds("userTaskProcessId"));
        assertThat(historicProcesses.size()).isEqualTo(1);

        // verify the variable was skipped exactly once
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_VARIABLE)).isEqualTo(2);

        // and verify logs don't contain any additional skip operations for this variable
        logs.assertDoesNotContain("Migration of historic variable with legacyId [" + variableToSkip.getId() + "] skipped");
        logs.assertContains("Migration of historic variable with legacyId [" + historicVariables.getLast().getId() + "] skipped");
    }

    @Test
    public void shouldSkipTaskVariablesWhenTaskIsSkipped() {
        // given state in c7
        deployer.deployCamunda7Process("userTaskProcess.bpmn");
        runtimeService.startProcessInstanceByKey("userTaskProcessId");

        // Create a task-level variable
        var task = taskService.createTaskQuery().singleResult();
        taskService.setVariableLocal(task.getId(), "taskLocalVar", "taskValue");
        taskService.complete(task.getId());

        // and the task is manually set as skipped
        String taskId = historyService.createHistoricTaskInstanceQuery().singleResult().getId();
        dbClient.insert(taskId, null, IdKeyMapper.TYPE.HISTORY_USER_TASK);

        // when history is migrated
        historyMigrator.migrate();

        // then process instance was migrated but task and its variables were not
        var historicProcesses = searchHistoricProcessInstances(procInstFilter().processDefinitionIds("userTaskProcessId"));
        assertThat(historicProcesses.size()).isEqualTo(1);
        var migratedProcessInstance = historicProcesses.getFirst();

        // Verify task was skipped
        assertThat(searchHistoricUserTasks(userTasksFilter().processInstanceKeys(migratedProcessInstance.processInstanceKey()))).isEmpty();

        // Verify task variable was also skipped
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_VARIABLE)).isEqualTo(1);

        // Find the variable that should have been skipped
        var taskVariable = historyService.createHistoricVariableInstanceQuery()
            .taskIdIn(taskId)
            .singleResult();

        logs.assertContains("Migration of historic variable with legacyId [" + taskVariable.getId() + "] skipped");
    }

    @Test
    public void shouldSkipServiceTaskVariablesWhenServiceTaskIsSkipped() {
        // given state in c7 with a service task using JUEL expression
        deployer.deployCamunda7Process("serviceTaskWithInputMappingProcess.bpmn");
        var processInstance = runtimeService.startProcessInstanceByKey("serviceTaskWithInputMappingProcessId");

        // Find the service task in history
        var historicActivities = historyService.createHistoricActivityInstanceQuery()
            .processInstanceId(processInstance.getId())
            .activityId("serviceTaskId")
            .list();

        assertThat(historicActivities).isNotEmpty()
            .as("Expected service task to be in history");

        String serviceTaskActivityInstanceId = historicActivities.getFirst().getId();

        // Find the service task local variable in history
        var serviceTaskVariable = historyService.createHistoricVariableInstanceQuery()
            .processInstanceId(processInstance.getId())
            .variableName("serviceTaskVar")
            .activityInstanceIdIn(serviceTaskActivityInstanceId)
            .singleResult();

        assertThat(serviceTaskVariable).isNotNull()
            .as("Expected to find local variable on service task");

        // Mark the service task as skipped
        dbClient.insert(serviceTaskActivityInstanceId, null, IdKeyMapper.TYPE.HISTORY_FLOW_NODE);

        // when history is migrated
        historyMigrator.migrate();

        // then process instance was migrated but service task was skipped
        var historicProcesses = searchHistoricProcessInstances(procInstFilter().processDefinitionIds("serviceTaskWithInputMappingProcessId"));
        assertThat(historicProcesses.size()).isEqualTo(1);

        // Verify service task variable was skipped
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_VARIABLE)).isEqualTo(1);

        // Verify appropriate logging
        logs.assertContains("Migration of historic variable with legacyId [" + serviceTaskVariable.getId() + "] skipped");
    }
}
