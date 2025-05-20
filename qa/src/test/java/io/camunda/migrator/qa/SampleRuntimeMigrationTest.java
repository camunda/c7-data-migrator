/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName;
import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureNotNull;
import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.process.test.api.CamundaAssert;
import java.util.List;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SampleRuntimeMigrationTest extends RuntimeMigrationAbstractTest {

  @Autowired
  private RuntimeService runtimeService;

  @Autowired
  private RepositoryService repositoryService;

  @Autowired
  private TaskService taskService;



  @AfterEach
  public void cleanup() {
    // repositoryService.createDeploymentQuery().list().forEach(d ->
    // repositoryService.deleteDeployment(d.getId(), true));
    // TODO how to cleanup c8 data?
    List<ProcessInstance> items = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    items.forEach(i -> camundaClient.newCancelInstanceCommand(i.getProcessInstanceKey()));
    items.forEach(i -> camundaClient.newDeleteResourceCommand(i.getProcessInstanceKey()));
  }

  @Test
  public void simpleProcessMigrationTest() {
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    var simpleProcess = runtimeService.startProcessInstanceByKey("simpleProcess");
    Task task1 = taskService.createTaskQuery().taskDefinitionKey("userTask1").singleResult();
    taskService.complete(task1.getId());
    Task task2 = taskService.createTaskQuery().taskDefinitionKey("userTask2").singleResult();
    ensureNotNull("Unexpected process state: userTask2 should exist", task2);
    ensureTrue("Unexpected process state: userTask2 should be 'created'", "created".equalsIgnoreCase(task2.getTaskState()));

    // when running runtime migration
    runtimeMigrator.migrate();

    // then there is one expected process instance
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    assertEquals(1, processInstances.size());
    ProcessInstance processInstance = processInstances.getFirst();
    assertEquals(simpleProcess.getProcessDefinitionKey(), processInstance.getProcessDefinitionId());

    // and the process instance has expected state
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .isActive()
        .hasActiveElements(byId("userTask2"))
        .hasVariable("legacyId", simpleProcess.getProcessInstanceId());

    // and the user task has expected state
    CamundaAssert.assertThat(byTaskName("User Task 2"))
        .isCreated()
        .hasElementId("userTask2")
        .hasAssignee(null);
  }

}