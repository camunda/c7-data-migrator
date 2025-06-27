/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.element;

import static io.camunda.migrator.qa.MigrationTestConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

import io.camunda.migrator.qa.RuntimeMigrationAbstractTest;
import java.util.Map;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SignalStartEventTest extends RuntimeMigrationAbstractTest {

  @Autowired
  private RuntimeService runtimeService;

  @Test
  public void migrateEventBasedActivityInstance() {
    // given
    deployProcessInC7AndC8("signalStartEventProcess.bpmn");
    ProcessInstance instance = runtimeService.startProcessInstanceByKey("SignalProcessId");
    Task task1 = taskService.createTaskQuery().taskDefinitionKey("userTaskId").singleResult();
    taskService.complete(task1.getId());
    runtimeService.signalEventReceived("SignalEventName");


    // when
    runtimeMigrator.start();

    // then
    assertThat(byProcessId("SignalProcessId")).isActive()
        .hasActiveElements(byId("userTask2Id"))
        .hasVariables(Map.of(
            LEGACY_ID_VAR_NAME, instance.getProcessInstanceId()
        ));
  }
}
