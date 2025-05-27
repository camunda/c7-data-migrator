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
    String CATCH_EVENT_1_VARIABLE_NAME = "catchEvent1CorrelationVariable";
    String CATCH_EVENT_2_VARIABLE_NAME = "catchEvent2CorrelationVariable";
    String CATCH_EVENT_1_VARIABLE_VALUE = "12345";
    double CATCH_EVENT_2_VARIABLE_VALUE = 99.9;

    // For C8 correlation variables are required
    Map<String, Object> variables = Variables.createVariables()
        .putValue(CATCH_EVENT_1_VARIABLE_NAME, CATCH_EVENT_1_VARIABLE_VALUE)
        .putValue(CATCH_EVENT_2_VARIABLE_NAME, CATCH_EVENT_2_VARIABLE_VALUE);

    ProcessInstance instance = runtimeService.startProcessInstanceByKey("eventGatewayProcessId", variables);

    // when
    runtimeMigrator.migrate();

    // then
    assertThat(byProcessId("eventGatewayProcessId"))
        .hasVariable(CATCH_EVENT_1_VARIABLE_NAME, CATCH_EVENT_1_VARIABLE_VALUE);
    assertThat(byProcessId("eventGatewayProcessId"))
        .hasVariable(CATCH_EVENT_2_VARIABLE_NAME, CATCH_EVENT_2_VARIABLE_VALUE);
    assertThat(byProcessId("eventGatewayProcessId")).isActive()
        .hasActiveElements(byId("eventGatewayElementId"))
        .hasVariable(LEGACY_ID_VAR_NAME, instance.getProcessInstanceId());
  }
}
