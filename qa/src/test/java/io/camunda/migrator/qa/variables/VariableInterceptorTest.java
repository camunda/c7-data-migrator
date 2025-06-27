/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.variables;

import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.qa.RuntimeMigrationAbstractTest;
import io.camunda.process.test.api.CamundaAssert;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Disabled;
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
    runtimeService.setVariable(simpleProcessInstance.getId(), "var", "value");
    simpleProcessInstance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    runtimeService.setVariable(simpleProcessInstance.getId(), "var", "value");

    // when running runtime migration
    runtimeMigrator.start();

    // then two instances and two interceptor invocations
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("var", "value");
    CamundaAssert.assertThat(byProcessId("userTaskProcessId"))
        .hasVariable("var", "value");

    assertThat(output.getOut()).contains("Hello from interceptor");
    Matcher matcher = Pattern.compile("Hello from interceptor").matcher(output.getOut());
    assertThat(matcher.results().count()).isEqualTo(2);
  }

  @Test
  @Disabled
  public void shouldThrowExceptionFromInterceptor(CapturedOutput output) {
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");

    // given processes state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "exFlag", true);

    // when running runtime migration
    runtimeMigrator.start();

    // then two instances and two interceptor invocations
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("exFlag", true);

    assertThat(output.getOut()).contains("Hello from interceptor");
    // check instance is skipped?
  }
}
