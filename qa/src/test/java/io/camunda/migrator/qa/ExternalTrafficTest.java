/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.qa;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.RuntimeMigrator;
import io.camunda.migrator.qa.util.RuntimeMigrationAbstractTest;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ExternalTrafficTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Test
  public void shouldHandleExternallyStartedMigratorJobsGracefully() {
    // given
    deployProcessInC7AndC8("simpleProcess.bpmn");

    String id = runtimeService.startProcessInstanceByKey("simpleProcess").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    camundaClient.newCreateInstanceCommand().bpmnProcessId("simpleProcess").latestVersion().execute();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(2);

    var events = logs.getEvents();
    assertThat(events.stream().filter(event -> event.getMessage()
        .matches(".*Process instance with key \\[(\\d+)\\] was externally started, skipping migrator job activation\\.")))
        .hasSize(1);
  }

}
