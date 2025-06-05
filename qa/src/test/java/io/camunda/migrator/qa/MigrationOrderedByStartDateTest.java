/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.client.api.search.response.SearchResponsePage;
import java.util.Date;
import java.util.function.Supplier;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.jupiter.api.Test;

class MigrationOrderedByStartDateTest extends RuntimeMigrationAbstractTest {

  @Test
  public void shouldMigrateStartedBetweenRuns() {
    // given
    deployProcessInC7AndC8("simpleProcess.bpmn");

    runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.startProcessInstanceByKey("simpleProcess");

    runtimeMigrator.migrate();

    Supplier<SearchResponsePage> response = () -> camundaClient.newProcessInstanceSearchRequest().send().join().page();

    // assume
    assertThat(response.get().totalItems()).isEqualTo(2);

    runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.startProcessInstanceByKey("simpleProcess");

    // when
    runtimeMigrator.migrate();

    // then
    assertThat(response.get().totalItems()).isEqualTo(5);
  }

  @Test
  public void shouldMigrateWithSameStartDate() {
    // given
    deployProcessInC7AndC8("simpleProcess.bpmn");

    runtimeService.startProcessInstanceByKey("simpleProcess");

    ClockUtil.setCurrentTime(new Date());
    runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.startProcessInstanceByKey("simpleProcess");

    ClockUtil.offset(1_000 * 4L);
    runtimeService.startProcessInstanceByKey("simpleProcess");

    // when
    runtimeMigrator.migrate();

    Supplier<SearchResponsePage> response = () -> camundaClient.newProcessInstanceSearchRequest().send().join().page();

    // then
    assertThat(response.get().totalItems()).isEqualTo(5);
  }

}
