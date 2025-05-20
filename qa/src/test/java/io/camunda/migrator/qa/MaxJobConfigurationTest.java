/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.junit.After;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.migrator.RuntimeMigrator;

@ExtendWith(OutputCaptureExtension.class)
class MaxJobConfigurationTest extends RuntimeMigrationAbstractTest {

  @Autowired
  private RuntimeService runtimeService;

  @Autowired
  private RepositoryService repositoryService;

  @Autowired
  private TaskService taskService;


  @After
  public void reset() {
//    configureMaxProcessInstances(RuntimeMigrator.DEFAULT_MAX_PROCESS_INSTANCE);
    configureMaxJobsToActivate(RuntimeMigrator.DEFAULT_MAX_JOB_COUNT);

  }

  @AfterEach
  public void cleanup() {
//    repositoryService.createDeploymentQuery().list().forEach(d -> repositoryService.deleteDeployment(d.getId(), true));
    // TODO how to cleanup c8 data?
    List<ProcessInstance> items = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    items.forEach(i -> camundaClient.newCancelInstanceCommand(i.getProcessInstanceKey()));
    items.forEach(i -> camundaClient.newDeleteResourceCommand(i.getProcessInstanceKey()));
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
    assertThat(matcher.results().count()).isEqualTo(2);
    assertTrue(output.getOut().contains("Migrator jobs found: 1"));
  }

}