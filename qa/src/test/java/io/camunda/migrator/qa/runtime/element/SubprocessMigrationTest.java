/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.qa.runtime.element;

import static io.camunda.migrator.qa.util.MigrationTestConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byTaskName;

import io.camunda.migrator.RuntimeMigrator;
import io.camunda.migrator.qa.runtime.RuntimeMigrationAbstractTest;
import io.camunda.client.api.search.response.Variable;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class SubprocessMigrationTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Test
  public void migrateCallActivityAndSubprocess() {
    // given
    deployer.deployProcessInC7AndC8("calledActivitySubprocess.bpmn");
    deployer.deployProcessInC7AndC8("callActivityProcess.bpmn");
    ProcessInstance parentInstance = runtimeService.startProcessInstanceByKey("callingProcessId");
    ProcessInstance subProcessInstance = runtimeService
        .createProcessInstanceQuery()
        .superProcessInstanceId(parentInstance.getProcessInstanceId())
        .singleResult();
    // when
    runtimeMigrator.start();

    // then
    io.camunda.client.api.search.response.ProcessInstance c8ParentInstance =
        camundaClient.newProcessInstanceSearchRequest().filter(processInstanceFilter -> {
          processInstanceFilter.processDefinitionId("callingProcessId");
        }).execute().items().getFirst();

    Long c8ParentInstanceKey = c8ParentInstance.getProcessInstanceKey();
    Optional<Variable> variable = getVariableByScope(c8ParentInstanceKey, c8ParentInstanceKey, LEGACY_ID_VAR_NAME);
    assert variable.isPresent();
    assert variable.get().getValue().equals("\""+parentInstance.getProcessInstanceId()+"\"");

    assertThat(byProcessId("calledProcessInstanceId")).isActive()
        .hasActiveElements(byId("userTaskId"))
        .hasVariable(LEGACY_ID_VAR_NAME, subProcessInstance.getProcessInstanceId());
    assertThat(byTaskName("userTaskName")).isCreated().hasElementId("userTaskId");
  }

  @Test
  public void shouldSkipMigrationWhenPropagateAllParentVariablesIsFalse() {
    // given
    deployer.deployProcessInC7AndC8("calledActivitySubprocess.bpmn");
    deployer.deployProcessInC7AndC8("callActivityProcessNoPropagation.bpmn");
    ProcessInstance parentInstance = runtimeService.startProcessInstanceByKey("callingProcessIdNoPropagation");

    // when
    runtimeMigrator.start();

    // then
    // verify no C8 instance was created
    assertThatProcessInstanceCountIsEqualTo(0);

    // verify the correct error message was logged
    var events = logs.getEvents();
    Assertions.assertThat(events.stream()
        .filter(event -> event.getMessage()
            .contains(String.format("Skipping process instance with legacyId [%s]: Found call activity with propagateAllParentVariables=false " +
                "for flow node with id [callActivityId] in C8 process. This is not supported by the migrator unless there is an " +
                "explicit mapping for the legacyId variable, as it would lead to orphaned sub-process instances.", parentInstance.getId()))))
        .hasSize(1);
  }

  @Test
  public void shouldNotSkipMigrationWhenPropagateAllParentVariablesIsFalseButMappingIsPresent() {
    // given
    deployer.deployProcessInC7AndC8("calledActivitySubprocess.bpmn");
    deployer.deployProcessInC7AndC8("callActivityProcessNoPropagationWithMapping.bpmn");
    ProcessInstance parentInstance = runtimeService.startProcessInstanceByKey("callingProcessIdNoPropagationWithMapping");
    ProcessInstance subProcessInstance = runtimeService
        .createProcessInstanceQuery()
        .superProcessInstanceId(parentInstance.getProcessInstanceId())
        .singleResult();

    // when
    runtimeMigrator.start();

    // then
    io.camunda.client.api.search.response.ProcessInstance c8ParentInstance =
        camundaClient.newProcessInstanceSearchRequest().filter(processInstanceFilter -> {
          processInstanceFilter.processDefinitionId("callingProcessIdNoPropagationWithMapping");
        }).execute().items().getFirst();

    Long c8ParentInstanceKey = c8ParentInstance.getProcessInstanceKey();
    Optional<Variable> variable = getVariableByScope(c8ParentInstanceKey, c8ParentInstanceKey, LEGACY_ID_VAR_NAME);
    assert variable.isPresent();
    assert variable.get().getValue().equals("\""+parentInstance.getProcessInstanceId()+"\"");

    assertThat(byProcessId("calledProcessInstanceId")).isActive()
        .hasActiveElements(byId("userTaskId"))
        .hasVariable(LEGACY_ID_VAR_NAME, subProcessInstance.getProcessInstanceId());
    assertThat(byTaskName("userTaskName")).isCreated().hasElementId("userTaskId");
  }

}
