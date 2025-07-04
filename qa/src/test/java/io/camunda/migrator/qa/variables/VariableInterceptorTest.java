/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.variables;

import static io.camunda.migrator.MigratorMode.RETRY_SKIPPED;
import static io.camunda.process.test.api.assertions.ElementSelectors.byId;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.qa.RuntimeMigrationAbstractTest;
import io.camunda.process.test.api.CamundaAssert;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
public class VariableInterceptorTest extends RuntimeMigrationAbstractTest {

  @Autowired
  TestVariableInterceptor interceptor;

  @Test
  public void shouldInvokeTestInterceptor(CapturedOutput output) {
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");
    deployProcessInC7AndC8("userTaskProcess.bpmn");

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

    assertThat(output.getOut()).contains("Hello from interceptor");
    Matcher matcher = Pattern.compile("Hello from interceptor").matcher(output.getOut());
    assertThat(matcher.results().count()).isEqualTo(2);
  }

  @Test
  public void shouldSkipProcessInstanceDueToExceptionFromInterceptor(CapturedOutput output) {
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");

    // given processes state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "exFlag", true);

    // run migration first time
    runtimeMigrator.start();

    assertThat(output.getOut()).contains("Skipping process instance with legacyId:");
    assertThat(output.getOut()).contains("due to: An error occurred during variable transformation");

    runtimeService.setVariable(simpleProcessInstance.getId(), "exFlag", false);
    // when run runtime migration again with RETRY_SKIPPED mode
    runtimeMigrator.setMode(RETRY_SKIPPED);
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasActiveElements(byId("userTask1"))
        .hasVariable("exFlag", false);

    assertThat(output.getOut()).contains("Bye from interceptor");
  }
}
