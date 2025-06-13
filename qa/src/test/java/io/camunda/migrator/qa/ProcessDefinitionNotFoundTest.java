/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.api.command.ClientStatusException;
import io.camunda.migrator.RuntimeMigratorException;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.jupiter.api.Test;

public class ProcessDefinitionNotFoundTest extends RuntimeMigrationAbstractTest {

  @Test
  public void shouldSkipNotExistingProcess() {
    // given
    deployCamunda7Process("io/camunda/migrator/bpmn/c7/simpleProcess.bpmn");
    deployProcessInC7AndC8("userTaskProcess.bpmn");

    runtimeService.startProcessInstanceByKey("simpleProcess");
    ClockUtil.offset(50_000L);
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // assume
    assertThatExceptionOfType(RuntimeMigratorException.class)
        .isThrownBy(() -> runtimeMigrator.migrate())
        .withCauseInstanceOf(ClientStatusException.class)
        .satisfies(exception ->
            assertThat(exception.getCause())
                .hasMessage("Command 'CREATE' rejected with code 'NOT_FOUND': "
                    + "Expected to find process definition with process ID 'simpleProcess', but none found"));

    // when
    assertThatExceptionOfType(RuntimeMigratorException.class)
        .isThrownBy(() -> runtimeMigrator.migrate())
        .withCauseInstanceOf(ClientStatusException.class)
        .satisfies(exception ->
            assertThat(exception.getCause())
                .hasMessage("Command 'CREATE' rejected with code 'NOT_FOUND': "
                    + "Expected to find process definition with process ID 'simpleProcess', but none found"));
  }

}
