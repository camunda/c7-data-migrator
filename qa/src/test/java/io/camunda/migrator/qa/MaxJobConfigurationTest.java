/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.Ignore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.migrator.RuntimeMigrator;
import io.camunda.zeebe.model.bpmn.Bpmn;


@ExtendWith(OutputCaptureExtension.class)
class MaxJobConfigurationTest extends RuntimeMigrationAbstractTest {


  @AfterEach
  public void reset() {
    configureMaxJobsToActivate(RuntimeMigrator.DEFAULT_MAX_JOB_COUNT);
  }

  @Test
  public void shouldPerformPaginationForMigrationJobs(CapturedOutput output) {
    configureMaxJobsToActivate(2);
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");

    // given
    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("simpleProcess");
    }

    // when running runtime migration
    runtimeMigrator.migrate();

    // then
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    assertEquals(5, processInstances.size());

    Matcher matcher = Pattern.compile("Migrator jobs found: 2").matcher(output.getOut());
    assertEquals(2, matcher.results().count());
    assertTrue(output.getOut().contains("Migrator jobs found: 1"));
  }

  @Test
  @Disabled // https://github.com/camunda/camunda-bpm-platform/issues/4998
  public void shouldMigrateMultiLevel(CapturedOutput output) {
    // deploy processes
    deployModels();

    // given
    runtimeService.startProcessInstanceByKey("root");

    // when running runtime migration
    runtimeMigrator.migrate();

    // then
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    assertEquals(3, processInstances.size());

//    Matcher matcher = Pattern.compile("Migrator jobs found: 2").matcher(output.getOut());
//    assertEquals(2, matcher.results().count());
//    assertTrue(output.getOut().contains("Migrator jobs found: 1"));
  }

  private void deployModels() {
    String rootId = "root";
    String level1Id = "level1";
    String level2Id = "level2";
    // C7
    org.camunda.bpm.model.bpmn.BpmnModelInstance c7rootModel = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(rootId)
        .startEvent("start_1")
        .callActivity("ca_level_1")
        .camundaIn(level1Id, level2Id)
          .camundaAsyncBefore()
          .calledElement(level1Id)
        .endEvent("end_1").done();
    org.camunda.bpm.model.bpmn.BpmnModelInstance c7level1Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(level1Id)
        .startEvent("start_2")
        .callActivity("ca_level_2")
          .camundaAsyncBefore()
          .calledElement(level2Id)
        .endEvent("end_2").done();
    org.camunda.bpm.model.bpmn.BpmnModelInstance c7level2Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(level2Id)
        .startEvent("start_3")
        .userTask("userTask_1")
        .endEvent("end_3").done();

    // C8
    io.camunda.zeebe.model.bpmn.BpmnModelInstance c8rootModel = io.camunda.zeebe.model.bpmn.Bpmn.createExecutableProcess(rootId)
        .startEvent("start_1")
        .zeebeEndExecutionListener("migrator")
        .callActivity("ca_level_1", c -> c.zeebeProcessId(level1Id))
        .endEvent("end_1").done();
    io.camunda.zeebe.model.bpmn.BpmnModelInstance c8level1Model = io.camunda.zeebe.model.bpmn.Bpmn.createExecutableProcess(level1Id)
        .startEvent("start_2")
        .zeebeEndExecutionListener("migrator")
        .callActivity("ca_level_2", c -> c.zeebeProcessId(level2Id))
        .endEvent("end_2").done();
    io.camunda.zeebe.model.bpmn.BpmnModelInstance c8level2Model = io.camunda.zeebe.model.bpmn.Bpmn.createExecutableProcess(level2Id)
        .startEvent("start_3")
        .zeebeEndExecutionListener("migrator")
        .userTask("userTask_1")
        .endEvent("end_3").done();

    System.out.println(org.camunda.bpm.model.bpmn.Bpmn.convertToString(c7rootModel));
    System.out.println(Bpmn.convertToString(c8rootModel));
    System.out.println(org.camunda.bpm.model.bpmn.Bpmn.convertToString(c7level1Model));
    System.out.println(Bpmn.convertToString(c8level1Model));
    System.out.println(org.camunda.bpm.model.bpmn.Bpmn.convertToString(c7level2Model));
    System.out.println(Bpmn.convertToString(c8level2Model));
    repositoryService.createDeployment().addModelInstance(rootId+".bpmn", c7rootModel).deploy();
    repositoryService.createDeployment().addModelInstance(level1Id+".bpmn", c7level1Model).deploy();
    repositoryService.createDeployment().addModelInstance(level2Id+".bpmn", c7level2Model).deploy();

    camundaClient.newDeployResourceCommand().addProcessModel(c8rootModel, rootId+".bpmn").send().join();
    camundaClient.newDeployResourceCommand().addProcessModel(c8level1Model, level1Id+".bpmn").send().join();
    camundaClient.newDeployResourceCommand().addProcessModel(c8level2Model, level2Id+".bpmn").send().join();

  }
}
