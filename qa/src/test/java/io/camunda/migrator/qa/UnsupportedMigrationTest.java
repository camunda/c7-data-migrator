/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.qa;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.migrator.MigratorException;
import org.junit.jupiter.api.Test;

public class UnsupportedMigrationTest extends RuntimeMigrationAbstractTest {

  @Test
  public void migrateProcessWithUnsupportedStartEvent() {
    // given
    deployProcessInC7AndC8("messageStartEventProcess.bpmn");
    runtimeService.correlateMessage("msgRef");

    // when/then
    assertThatThrownBy(() -> runtimeMigrator.migrate()).isInstanceOf(MigratorException.class)
        .hasMessageContaining(
            "Error occurred: shutting down Data Migrator gracefully.")
        .hasRootCauseMessage("FAILED_PRECONDITION: Command 'CREATE' rejected with code 'INVALID_STATE': Expected to "
            + "create instance of process with none start event, but there is no such event");
  }
}
