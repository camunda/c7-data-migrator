/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static io.camunda.migrator.MigratorMode.LIST_SKIPPED;
import static io.camunda.migrator.MigratorMode.RETRY_SKIPPED;
import static io.camunda.migrator.PrintUtils.NO_SKIPPED_INSTANCES_MESSAGE;
import static io.camunda.migrator.PrintUtils.PREVIOUSLY_SKIPPED_INSTANCES_MESSAGE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.camunda.bpm.engine.impl.util.EnsureUtil.ensureTrue;

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.migrator.RuntimeMigrator;
import io.camunda.migrator.persistence.IdKeyDbModel;
import io.camunda.migrator.persistence.IdKeyMapper;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class SkipAndRetryProcessInstancesTest extends RuntimeMigrationAbstractTest {
  private static final String SKIP_INSTANCE_MESSAGE = "Skipping process instance with legacyId [%s]: ";
  private static final String LOOP_CHARACTERISTICS_MESSAGE =
      SKIP_INSTANCE_MESSAGE + "Found multi-instance loop characteristics for flow node"
          + " with id [%s] in C7 process instance.";

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Autowired
  private RuntimeService runtimeService;

  @Autowired
  private TaskService taskService;

  @Autowired
  private IdKeyMapper idKeyMapper;

  @Test
  public void shouldSkipMultiInstanceProcessMigration() {
    // given process state in c7
    deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    ensureTrue("Unexpected process state: one task and three parallel tasks should be created", taskCount == 4);

    // when running runtime migration
    runtimeMigrator.start();

    // then the instance was not migrated and marked as skipped
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().execute().items();
    assertThat(processInstances.size()).isEqualTo(0);
    List<IdKeyDbModel> skippedProcessInstanceIds = idKeyMapper.findSkipped().stream().toList();
    assertThat(skippedProcessInstanceIds.size()).isEqualTo(1);
    assertThat(skippedProcessInstanceIds.getFirst().id()).isEqualTo(process.getId());

    var events = logs.getEvents();
    Assertions.assertThat(events.stream()
        .filter(event -> event.getMessage()
            .contains(String.format(LOOP_CHARACTERISTICS_MESSAGE, process.getId(), "multiUserTask")))).hasSize(1);
  }

  @Test
  public void shouldSkipMultiLevelMultiInstanceProcessMigration() {
    // given process state in c7
    deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    deployProcessInC7AndC8("callMultiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("callMultiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    ensureTrue("Unexpected process state: one task and three parallel tasks should be created", taskCount == 4);

    // when running runtime migration
    runtimeMigrator.start();

    // then the instance was not migrated and marked as skipped
    assertThatProcessInstanceCountIsEqualTo(0);
    List<IdKeyDbModel> skippedProcessInstanceIds = idKeyMapper.findSkipped().stream().toList();
    assertThat(skippedProcessInstanceIds.size()).isEqualTo(1);
    assertThat(skippedProcessInstanceIds.getFirst().id()).isEqualTo(process.getId());

    var events = logs.getEvents();
    Assertions.assertThat(events.stream()
        .filter(event -> event.getMessage()
            .contains(String.format(LOOP_CHARACTERISTICS_MESSAGE, process.getId(), "multiUserTask")))).hasSize(1);
  }

  @Test
  public void shouldSkipAgainAProcessInstanceThatWasSkipped() {
    // given skipped process instance
    deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    runtimeMigrator.start();
    ensureTrue("Unexpected state: one process instance should be skipped", idKeyMapper.findSkipped().size() == 1);

    // when running retrying runtime migration
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then the instance was not migrated and still marked as skipped
    assertThatProcessInstanceCountIsEqualTo(0);
    List<IdKeyDbModel> skippedProcessInstanceIds = idKeyMapper.findSkipped().stream().toList();
    assertThat(skippedProcessInstanceIds.size()).isEqualTo(1);
    assertThat(skippedProcessInstanceIds.getFirst().id()).isEqualTo(process.getId());

    var events = logs.getEvents();
    Assertions.assertThat(events.stream()
        .filter(event -> event.getMessage()
            .contains(String.format(SKIP_INSTANCE_MESSAGE, process.getId())))).hasSize(2);
  }

  @Test
  public void shouldMigrateFixedProcessInstanceThatWasSkipped() {
    // given skipped process instance
    deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    runtimeMigrator.start();

    var events = logs.getEvents();
    Assertions.assertThat(events.stream()
        .filter(event -> event.getMessage()
            .contains(String.format(SKIP_INSTANCE_MESSAGE, process.getId())))).hasSize(1);

    // and given the process state changed after skipping and is now eligible for migration
    for (Task task : taskService.createTaskQuery().taskDefinitionKey("multiUserTask").list()) {
      taskService.complete(task.getId());
    }
    ensureTrue("Unexpected process state: only one task should be active", taskService.createTaskQuery().count() == 1);

    // when running retrying runtime migration
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then the instance was migrated
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().execute().items();
    assertThat(processInstances.size()).isEqualTo(1);
    ProcessInstance processInstance = processInstances.getFirst();
    assertThat(processInstance.getProcessDefinitionId()).isEqualTo(process.getProcessDefinitionKey());

    // and the key updated
    assertThat(idKeyMapper.findKeyById(process.getId())).isNotNull();

    // and no additional skipping logs (still 1, not 2 matches)
    Assertions.assertThat(events.stream()
        .filter(event -> event.getMessage()
            .contains(String.format(SKIP_INSTANCE_MESSAGE, process.getId())))).hasSize(1);
  }

  @Test
  public void shouldLogWarningWhenProcessInstanceHasBeenCompleted(CapturedOutput output) {
    // given skipped process instance
    deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    runtimeMigrator.migrate();
    ensureTrue("Unexpected state: one process instance should be skipped", idKeyMapper.findSkipped().size() == 1);

    runtimeService.deleteProcessInstance(process.getId(), "State cannot be fixed!");

    // when running retrying runtime migration
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then
    assertThat(output.getOut()).containsPattern(
        "WARN(.*)Process instance with legacyId [a-f0-9-]+ doesn't exist anymore. Has it been completed or cancelled in the meantime\\?");
  }

  @Test
  public void shouldListSkippedProcessInstances(CapturedOutput output) {
    // given skipped process instance
    deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    List<String> processInstancesIds = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      processInstancesIds.add(runtimeService.startProcessInstanceByKey("multiInstanceProcess").getProcessInstanceId());
    }
    runtimeMigrator.start();
    ensureTrue("Unexpected state: 10 process instances should be skipped", idKeyMapper.findSkippedCount() == 10);
    // when running migration with list skipped mode
    runtimeMigrator.setMode(LIST_SKIPPED);
    runtimeMigrator.start();

    // then all skipped process instances were listed
    String regex = PREVIOUSLY_SKIPPED_INSTANCES_MESSAGE + "\\R((?:.+\\R){9}.+)";
    assertThat(output.getOut()).containsPattern(regex);
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(output.getOut());

    final String capturedIds = matcher.find() ? matcher.group(1) : "";
    processInstancesIds.forEach(processInstanceId -> assertThat(capturedIds).contains(processInstanceId));

    // and skipped instances were not migrated
    assertThat(idKeyMapper.findSkippedCount()).isEqualTo(10);
  }

  @Test
  public void shouldDisplayNoSkippedInstances(CapturedOutput output) {
    // given no skipped instances

    // when running migration with list skipped mode
    runtimeMigrator.setMode(LIST_SKIPPED);
    runtimeMigrator.start();

    // then expected message is printed
    assertThat(output.getOut().trim()).endsWith(NO_SKIPPED_INSTANCES_MESSAGE);

    // and no migration was done
    assertThat(idKeyMapper.findAllIds().size()).isEqualTo(0);
  }

}