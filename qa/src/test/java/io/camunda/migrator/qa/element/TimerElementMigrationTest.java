/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.element;

import static io.camunda.migrator.qa.MigrationTestConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

import io.camunda.process.test.api.CamundaProcessTestContext;
import java.time.Duration;
import java.util.Map;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TimerElementMigrationTest extends AbstractElementMigrationTest {

  @Autowired
  private CamundaProcessTestContext processTestContext;

  @Test
  public void migrateTimerInterruptingBoundaryWithDuration() {
    // given a process with a timer boundary event that has a duration of 10 days and leftover duration var of 1 day
    // the timer duration definition uses an expression as described in our workaround suggestions
    deployProcessInC7AndC8("timerDurationBoundaryEventProcess.bpmn");
    ProcessInstance c7instance = runtimeService.startProcessInstanceByKey("timerDurationBoundaryEventProcessId");
    runtimeService.setVariable(c7instance.getId(), "leftoverDuration", "P1D");

    // when
    runtimeMigrator.start();
    processTestContext.increaseTime(Duration.ofDays(2));

    // then
    assertThat(byProcessId("timerDurationBoundaryEventProcessId")).isCompleted()
        .hasTerminatedElements("userTaskId")
        .hasCompletedElements( "timerEndEventId")
        .hasVariables(Map.of(
            LEGACY_ID_VAR_NAME, c7instance.getProcessInstanceId(),
            "leftoverDuration", "P1D")
        );
  }
}
