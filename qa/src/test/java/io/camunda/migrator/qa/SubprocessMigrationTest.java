/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.qa;

import static io.camunda.migrator.qa.MigrationTestConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName;

import java.util.stream.Stream;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

public class SubprocessMigrationTest extends RuntimeMigrationAbstractTest{

  @Test
  public void migrateCallActivityAndSubprocess() {
    // given
    deployProcessInC7AndC8("calledActivitySubprocess.bpmn");
    deployProcessInC7AndC8("callActivityProcess.bpmn");
    ProcessInstance instance = runtimeService.startProcessInstanceByKey("callingProcessId");
    ProcessInstance calledInstance = runtimeService
        .createProcessInstanceQuery()
        .superProcessInstanceId(instance.getProcessInstanceId())
        .singleResult();
    // when
    runtimeMigrator.migrate();

    // then
    assertThat(byProcessId("callingProcessId")).isActive()
        .hasActiveElements(byId("callActivityId"))
        .hasVariable(LEGACY_ID_VAR_NAME, instance.getProcessInstanceId());
    assertThat(byProcessId("calledProcessInstanceId")).isActive()
        .hasActiveElements(byId("userTaskId"))
        .hasVariable(LEGACY_ID_VAR_NAME, calledInstance.getProcessInstanceId());
    assertThat(byTaskName("userTaskName")).isCreated().hasElementId("userTaskId");
  }
}
