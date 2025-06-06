/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import io.camunda.client.api.search.response.ProcessInstance;

@ExtendWith(OutputCaptureExtension.class)
class BatchConfigurationTest extends RuntimeMigrationAbstractTest {

  @Test
  public void shouldPerformPaginationForProcessInstances(CapturedOutput output) {
    runtimeMigrator.setBatchSize(2);
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");

    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("simpleProcess");
    }
    assertThat(runtimeService.createProcessInstanceQuery().list().size()).isEqualTo(5);

    // when running runtime migration
    runtimeMigrator.start();

    // then
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    assertThat(processInstances.size()).isEqualTo(5);
    assertThat(output.getOut()).contains("Method: #fetchProcessInstancesToMigrate, max count: 5, offset: 0, batch size: 2");
    assertThat(output.getOut()).contains("Method: #fetchProcessInstancesToMigrate, max count: 5, offset: 2, batch size: 2");
    assertThat(output.getOut()).contains("Method: #fetchProcessInstancesToMigrate, max count: 5, offset: 4, batch size: 2");
    Matcher matcher = Pattern.compile("Method: #validateProcessInstanceState, max count: 1, offset: 0, batch size: 2").matcher(output.getOut());
    assertThat(matcher.results().count()).isEqualTo(5);
  }

  @Test
  public void shouldPerformPaginationForMigrationJobs(CapturedOutput output) {
    runtimeMigrator.setBatchSize(2);
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");

    // given
    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("simpleProcess");
    }

    // when running runtime migration
    runtimeMigrator.start();

    // then
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    assertThat(processInstances.size()).isEqualTo(5);

    Matcher matcher = Pattern.compile("Migrator jobs found: 2").matcher(output.getOut());
    assertThat(matcher.results().count()).isEqualTo(2);
    assertThat(output.getOut()).contains("Migrator jobs found: 1");
    assertThat(output.getOut()).contains("Migrator jobs found: 0");
  }

  @Test
  public void shouldPaginateMultiLevelProcessModel(CapturedOutput output) {
    // deploy processes
    deployModels();

    // given
    runtimeService.startProcessInstanceByKey("root");

    // when running runtime migration
    runtimeMigrator.start();

    // then
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    assertEquals(3, processInstances.size());

    Matcher matcher = Pattern.compile("Migrator jobs found: 1").matcher(output.getOut());
    assertThat(matcher.results().count()).isEqualTo(3);
    assertThat(output.getOut()).contains("Method: #fetchProcessInstancesToMigrate, max count: 1, offset: 0, batch size: 500");
    assertThat(output.getOut()).contains("Method: #validateProcessInstanceState, max count: 3, offset: 0, batch size: 500");
  }

  private void deployModels() {
    String rootId = "root";
    String level1Id = "level1";
    String level2Id = "level2";
    // C7
    var c7rootModel = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(rootId)
        .startEvent("start_1")
        .callActivity("ca_level_1")
        .camundaIn(level1Id, level2Id)
          .calledElement(level1Id)
        .endEvent("end_1").done();
    var c7level1Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(level1Id)
        .startEvent("start_2")
        .callActivity("ca_level_2")
          .calledElement(level2Id)
        .endEvent("end_2").done();
    var c7level2Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(level2Id)
        .startEvent("start_3")
        .userTask("userTask_1")
        .endEvent("end_3").done();

    // C8
    var c8rootModel = io.camunda.zeebe.model.bpmn.Bpmn.createExecutableProcess(rootId)
        .startEvent("start_1")
        .zeebeEndExecutionListener("migrator")
        .callActivity("ca_level_1", c -> c.zeebeProcessId(level1Id))
        .endEvent("end_1").done();
    var c8level1Model = io.camunda.zeebe.model.bpmn.Bpmn.createExecutableProcess(level1Id)
        .startEvent("start_2")
        .zeebeEndExecutionListener("migrator")
        .callActivity("ca_level_2", c -> c.zeebeProcessId(level2Id))
        .endEvent("end_2").done();
    var c8level2Model = io.camunda.zeebe.model.bpmn.Bpmn.createExecutableProcess(level2Id)
        .startEvent("start_3")
        .zeebeEndExecutionListener("migrator")
        .userTask("userTask_1")
        .endEvent("end_3").done();

    repositoryService.createDeployment().addModelInstance(rootId+".bpmn", c7rootModel).deploy();
    repositoryService.createDeployment().addModelInstance(level1Id+".bpmn", c7level1Model).deploy();
    repositoryService.createDeployment().addModelInstance(level2Id+".bpmn", c7level2Model).deploy();

    camundaClient.newDeployResourceCommand().addProcessModel(c8rootModel, rootId+".bpmn").send().join();
    camundaClient.newDeployResourceCommand().addProcessModel(c8level1Model, level1Id+".bpmn").send().join();
    camundaClient.newDeployResourceCommand().addProcessModel(c8level2Model, level2Id+".bpmn").send().join();
  }

}
