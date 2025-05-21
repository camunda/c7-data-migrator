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

import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.migrator.RuntimeMigrator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class MaxProcessInstanceConfigurationTest extends RuntimeMigrationAbstractTest {

  @AfterEach
  public void reset() {
    configureMaxProcessInstances(RuntimeMigrator.DEFAULT_MAX_PROCESS_INSTANCE);
  }

  @Test
  public void shouldPerformPaginationForPI(CapturedOutput output) {
    configureMaxProcessInstances(2);
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");

    for (int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("simpleProcess");
    }
    assertEquals(5, runtimeService.createProcessInstanceQuery().list().size());

    // when running runtime migration
    runtimeMigrator.migrate();

    // then
    List<ProcessInstance> processInstances = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    assertEquals(5, processInstances.size());

    assertTrue(output.getOut().contains("Fetched instances to migrate: 2"));
    assertTrue(output.getOut().contains("Fetched instances to migrate: 1"));
    assertTrue(output.getOut().contains("Fetched instances to migrate: 0"));
  }

}