/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.migrator.RuntimeMigrator;

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

}