/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.qa.element;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.migrator.exception.MigratorException;
import io.camunda.migrator.qa.RuntimeMigrationAbstractTest;
import org.junit.jupiter.api.Test;

public class UnsupportedStartEventMigrationTest extends RuntimeMigrationAbstractTest {

  // To be addressed with https://github.com/camunda/camunda-bpm-platform/issues/5195
  @Test
  public void migrateProcessWithUnsupportedStartEvent() {
    // given
    deployProcessInC7AndC8("messageStartEventProcess.bpmn");
    runtimeService.correlateMessage("msgRef");

    // when/then
    assertThatThrownBy(() -> runtimeMigrator.start()).isInstanceOf(MigratorException.class)
        .hasMessageContaining(
            "Creating process instance failed for legacyId: ")
        .hasRootCauseMessage("FAILED_PRECONDITION: Command 'CREATE' rejected with code 'INVALID_STATE': Expected to "
            + "create instance of process with none start event, but there is no such event");
  }
}
