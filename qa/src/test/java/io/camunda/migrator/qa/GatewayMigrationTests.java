/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static io.camunda.migrator.qa.MigrationTestConstants.LEGACY_ID_VAR_NAME;
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

import java.util.Map;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class GatewayMigrationTests extends RuntimeMigrationAbstractTest {

  @Autowired
  private RuntimeService runtimeService;

  @Test
  public void migrateEventBasedActivityInstance() {
    // given
    deployProcessInC7AndC8("eventGateway.bpmn");

    // For C8 correlation variables are required
    Map<String, Object> variables = Variables.createVariables()
        .putValue("catchEvent1CorrelationVariable", "12345")
        .putValue("catchEvent2CorrelationVariable", 99.9);

    ProcessInstance instance = runtimeService.startProcessInstanceByKey("eventGatewayProcessId", variables);

    // when
    runtimeMigrator.migrate();

    // then
    assertThat(byProcessId("eventGatewayProcessId")).isActive()
        .hasActiveElements(byId("eventGatewayElementId"))
        .hasVariables(Map.of(
            LEGACY_ID_VAR_NAME, instance.getProcessInstanceId(),
            "catchEvent1CorrelationVariable", "12345",
            "catchEvent2CorrelationVariable", 99.9
        ));
    
  }
  
  @Test
  public void migrateParallelGatewayActivityInstance() {
    // while the parallel gateway has no natural wait state, we can test that the tokens are in a consistent state
    // between the Splitting Parallel Gateway & the Merging Parallel Gateway after migrating to C8
    // given
    deployProcessInC7AndC8("parallelGateway.bpmn");

    ProcessInstance instance = runtimeService.startProcessInstanceByKey("ParallelGatewayProcess");

    // when
    runtimeMigrator.migrate();

    // then
    assertThat(byProcessId("ParallelGatewayProcess")).isActive()
        .hasActiveElementsExactly(byId("usertaskActivity"))
        .hasVariable(LEGACY_ID_VAR_NAME, instance.getProcessInstanceId());

    //    if <zeebe:executionListener eventType="end" type="migrator" /> is part of the bpmn, the following assert fails,
    //    otherwise it passes
    //    assertThat(byProcessId("ParallelGatewayProcess")).hasCompletedElement("noOpActivity", 1)
    //        .hasVariable(LEGACY_ID_VAR_NAME, instance.getProcessInstanceId());
  }
}
