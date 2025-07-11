/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.qa.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.RuntimeMigrator;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class MigratorListenerNotFoundTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Test
  public void shouldSkipOnMissingListener() {
    // given
    deployer.deployProcessInC7AndC8("noMigratorListener.bpmn");

    String id = runtimeService.startProcessInstanceByKey("noMigratorListener").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);

    var events = logs.getEvents();
    assertThat(events.stream().filter(event -> event.getMessage()
        .contains(String.format("Skipping process instance with legacyId [%s]: "
            + "Couldn't find execution listener of type 'migrator' "
            + "on start event [Event_1px2j50] in C8 process with key", id))))
        .hasSize(1);
  }

  @Test
  public void shouldSkipOnListenerWithWrongType() {
    // given
    deployer.deployProcessInC7AndC8("migratorListenerWrongType.bpmn");

    String id = runtimeService.startProcessInstanceByKey("migratorListenerWrongType").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);

    var events = logs.getEvents();
    assertThat(events.stream().filter(event -> event.getMessage()
        .matches(String.format(".*Skipping process instance with legacyId \\[%s\\]: "
            + "No execution listener of type 'migrator' found on start event \\[Event_1px2j50\\] "
            + "in C8 process with id \\[\\d+\\]\\. At least one migrator listener is required\\.", id))))
        .hasSize(1);
  }

  @Test
  public void shouldNotSkipOnMissingListenerWithEmbeddedSubprocess() {
    // given
    deployer.deployProcessInC7AndC8("embeddedSubprocessWithoutMigratorListener.bpmn");

    String id = runtimeService.startProcessInstanceByKey("embeddedSubprocessWithoutMigratorListener").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // assume
    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(1);
  }

}
