/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.qa.runtime.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.qa.runtime.RuntimeMigrationAbstractTest;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "camunda.migrator.table-prefix=MY_PREFIX_",
    "logging.level.io.camunda.migrator.persistence.IdKeyMapper=DEBUG"
})
public class MigratorTablePrefixTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected LogCapturer logs = LogCapturer.create()
      .captureForLogger("io.camunda.migrator.persistence.IdKeyMapper", Level.DEBUG);

  @Test
  public void shouldMigrateWithMigratorTablePrefix() {
    // given
    deployer.deployProcessInC7AndC8("messageStartEventProcess.bpmn");
    runtimeService.correlateMessage("msgRef");

    // when
    runtimeMigrator.start();

    // then
    assertThatProcessInstanceCountIsEqualTo(0);

    var events = logs.getEvents();
    assertThat(events.stream().filter(event -> event.getMessage()
        .matches(".*INSERT INTO MY_PREFIX_MIGRATION_MAPPING.*")))
        .hasSize(1);
  }

}
