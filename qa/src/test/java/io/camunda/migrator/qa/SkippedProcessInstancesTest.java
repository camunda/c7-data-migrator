/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.client.api.search.response.SearchResponsePage;
import java.util.function.Supplier;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SkippedProcessInstancesTest extends RuntimeMigrationAbstractTest {

  @Autowired
  private RuntimeService runtimeService;

  @Autowired
  private TaskService taskService;

  @Test
  public void shouldMigrateSkippedProcessInstances() {
    // given
    deployProcessInC7AndC8("simpleProcess.bpmn");
    deployProcessInC7AndC8("miProcess.bpmn");

    for (int i = 0; i < 22; i++) {
      runtimeService.startProcessInstanceByKey("simpleProcess");
      runtimeService.startProcessInstanceByKey("miProcess");
    }

    runtimeMigrator.setBatchSize(4);
    runtimeMigrator.migrate();

    Supplier<SearchResponsePage> response = () -> camundaClient.newProcessInstanceSearchRequest().send().join().page();

    // assume
    assertThat(response.get().totalItems()).isEqualTo(22);

    runtimeService.createProcessInstanceQuery()
        .processDefinitionKey("miProcess")
        .list()
        .forEach(processInstance -> {
          taskService.createTaskQuery()
              .processInstanceId(processInstance.getId())
              .list()
              .stream()
              .map(Task::getId).forEach(taskService::complete);
        });

    runtimeMigrator.setRetryMode(true);

    // when
    runtimeMigrator.migrate();

    // then
    assertThat(response.get().totalItems()).isEqualTo(22*2);
  }

}