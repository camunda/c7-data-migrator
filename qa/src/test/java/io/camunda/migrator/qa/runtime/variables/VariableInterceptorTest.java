/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.runtime.variables;

import static io.camunda.migrator.MigratorMode.RETRY_SKIPPED;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;

import io.camunda.migrator.qa.runtime.RuntimeMigrationAbstractTest;
import io.camunda.process.test.api.CamundaAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class VariableInterceptorTest extends RuntimeMigrationAbstractTest {

  @Autowired
  TestVariableInterceptor interceptor;

  @Test
  public void shouldInvokeTestInterceptor() {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
    deployer.deployProcessInC7AndC8("userTaskProcess.bpmn");

    // given processes state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "varIntercept", "value");
    simpleProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    runtimeService.setVariable(simpleProcessInstance.getId(), "varIntercept", "value");

    // when running runtime migration
    runtimeMigrator.start();

    // then two instances and two interceptor invocations
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("varIntercept", "Hello");
    CamundaAssert.assertThat(byProcessId("userTaskProcessId"))
        .hasVariable("varIntercept", "Hello");
  }

  @Test
  public void shouldSkipProcessInstanceDueToExceptionFromInterceptor() {
    // deploy processes
    deployer.deployProcessInC7AndC8("simpleProcess.bpmn");

    // given processes state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "exFlag", true);

    // run migration first time
    runtimeMigrator.start();

    runtimeService.setVariable(simpleProcessInstance.getId(), "exFlag", false);
    // when run runtime migration again with RETRY_SKIPPED mode
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasActiveElements(byId("userTask1"))
        .hasVariable("exFlag", false);
  }
}
