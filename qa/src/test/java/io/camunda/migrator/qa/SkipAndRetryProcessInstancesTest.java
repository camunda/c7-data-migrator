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
import io.camunda.migrator.history.IdKeyMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class SkipAndRetryProcessInstancesTest extends RuntimeMigrationAbstractTest {

  @Autowired
  private RuntimeService runtimeService;

  @Autowired
  private TaskService taskService;

  @Autowired
  private IdKeyMapper idKeyMapper;

  @Test
  public void shouldSkipMultiInstanceProcessMigration() {
    // given process state in c7
    deployCamunda7Process("io/camunda/migrator/bpmn/c7/multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    ensureTrue("Unexpected process state: one task and three parallel tasks should be created", taskCount == 4);

    // when running runtime migration
    runtimeMigrator.start();

    // then the instance was not migrated and marked as skipped
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    assertThat(processInstances.size()).isEqualTo(0);
    List<String> skippedProcessInstanceIds = idKeyMapper.findSkippedProcessInstanceIds().stream().toList();
    assertThat(skippedProcessInstanceIds.size()).isEqualTo(1);
    assertThat(skippedProcessInstanceIds.getFirst()).isEqualTo(process.getId());
  }

  @Test
  public void shouldSkipMultiLevelMultiInstanceProcessMigration() {
    // given process state in c7
    deployCamunda7Process("io/camunda/migrator/bpmn/c7/multiInstanceProcess.bpmn");
    deployCamunda7Process("io/camunda/migrator/bpmn/c7/callMultiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("callMultiInstanceProcess");
    long taskCount = taskService.createTaskQuery().count();
    ensureTrue("Unexpected process state: one task and three parallel tasks should be created", taskCount == 4);

    // when running runtime migration
    runtimeMigrator.start();

    // then the instance was not migrated and marked as skipped
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    assertThat(processInstances.size()).isEqualTo(0);
    List<String> skippedProcessInstanceIds = idKeyMapper.findSkippedProcessInstanceIds().stream().toList();
    assertThat(skippedProcessInstanceIds.size()).isEqualTo(1);
    assertThat(skippedProcessInstanceIds.getFirst()).isEqualTo(process.getId());
  }

  @Test
  public void shouldSkipAgainAProcessInstanceThatWasSkipped() {
    // given skipped process instance
    deployCamunda7Process("io/camunda/migrator/bpmn/c7/multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    runtimeMigrator.start();
    ensureTrue("Unexpected state: one process instance should be skipped", idKeyMapper.findSkippedProcessInstanceIds().size() == 1);

    // when running retrying runtime migration
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then the instance was not migrated and still marked as skipped
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    assertThat(processInstances.size()).isEqualTo(0);
    List<String> skippedProcessInstanceIds = idKeyMapper.findSkippedProcessInstanceIds().stream().toList();
    assertThat(skippedProcessInstanceIds.size()).isEqualTo(1);
    assertThat(skippedProcessInstanceIds.getFirst()).isEqualTo(process.getId());
  }

  @Test
  public void shouldMigrateFixedProcessInstanceThatWasSkipped() {
    // given skipped process instance
    deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    runtimeMigrator.start();
    ensureTrue("Unexpected state: one process instance should be skipped", idKeyMapper.findSkippedProcessInstanceIds().size() == 1);

    // and given the process state changed after skipping and is now eligible for migration
    for(Task task : taskService.createTaskQuery().taskDefinitionKey("multiUserTask").list()) {
      taskService.complete(task.getId());
    }
    ensureTrue("Unexpected process state: only one task should be active", taskService.createTaskQuery().count() == 1);

    // when running retrying runtime migration
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then the instance was migrated
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    assertThat(processInstances.size()).isEqualTo(1);
    ProcessInstance processInstance = processInstances.getFirst();
    assertThat(processInstance.getProcessDefinitionId()).isEqualTo(process.getProcessDefinitionKey());

    // and the key updated
    assertThat(idKeyMapper.findKeyById(process.getId())).isNotNull();
  }

  @Test
  public void shouldLogWarningWhenProcessInstanceHasBeenCompleted(CapturedOutput output) {
    // given skipped process instance
    deployProcessInC7AndC8("multiInstanceProcess.bpmn");
    var process = runtimeService.startProcessInstanceByKey("multiInstanceProcess");
    runtimeMigrator.start();
    ensureTrue("Unexpected state: one process instance should be skipped", idKeyMapper.findSkippedProcessInstanceIds().size() == 1);

    runtimeService.deleteProcessInstance(process.getId(), "State cannot be fixed!");

    // when running retrying runtime migration
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then
    assertThat(output.getOut())
        .containsPattern("WARN(.*)Process instance with legacyId [a-f0-9-]+ doesn't exist anymore. Has it been completed or cancelled in the meantime\\?");
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
    ensureTrue("Unexpected state: one process instance should be skipped", idKeyMapper.findSkippedProcessInstanceIds().size() == 10);

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
    assertThat(idKeyMapper.findSkippedProcessInstanceIds().size()).isEqualTo(10);
  }

  @Test
  public void shouldDisplayNoSkippedInstances(CapturedOutput output) {
    // given no skipped instances

    // when running migration with list skipped mode
    runtimeMigrator.setMode(LIST_SKIPPED);
    runtimeMigrator.start();

    // then expected message is printed
    assertThat(output.getOut()).endsWith(NO_SKIPPED_INSTANCES_MESSAGE + "\n");

    // and no migration was done
    assertThat(idKeyMapper.findAllProcessInstanceIds().size()).isEqualTo(0);
  }

}