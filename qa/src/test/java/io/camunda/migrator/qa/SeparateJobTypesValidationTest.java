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
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.job-type=custom-activation-type",
    "camunda.migrator.validation-job-type==if legacyId then \"migrator\" else \"noop\""
})
public class SeparateJobTypesValidationTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Test
  public void shouldUseValidationJobTypeInValidationMessage() {
    // given
    deployProcessInC7AndC8("noMigratorListener.bpmn");

    String id = runtimeService.startProcessInstanceByKey("noMigratorListener").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);

    var events = logs.getEvents();
    assertThat(events.stream()
        .filter(event -> event.getMessage()
            .matches(String.format(".*Skipping process instance with legacyId \\[%s\\]: "
                + "Couldn't find execution listener of type '=if legacyId then \"migrator\" else \"noop\"' on start event "
                + "\\[Event_1px2j50\\] in C8 process with key \\[(\\d+)\\]\\.", id))))
        .hasSize(1);
  }

  @Test
  public void shouldUseValidationJobTypeInListenerNotFoundMessage() {
    // given
    deployProcessInC7AndC8("migratorListenerCustomType.bpmn");

    String id = runtimeService.startProcessInstanceByKey("migratorListenerCustomType").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);

    var events = logs.getEvents();
    assertThat(events.stream().filter(event -> event.getMessage()
        .matches(String.format(".*Skipping process instance with legacyId \\[%s\\]: "
            + "No execution listener of type '=if legacyId then \"migrator\" else \"noop\"' found on "
            + "start event \\[Event_1px2j50\\] in C8 process with id \\[(\\d+)\\]\\. "
            + "At least one '=if legacyId then \"migrator\" else \"noop\"' listener is required\\.", id))))
        .hasSize(1);
  }

  @Test
  public void shouldUseCustomValidationJobTypeInListenerSucceed() {
    // given
    deployProcessInC7AndC8("migratorListenerFeel.bpmn");

    String id = runtimeService.startProcessInstanceByKey("migratorListenerFeel").getId();

    // assume
    assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()).isNotNull();

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(1);
  }

}
