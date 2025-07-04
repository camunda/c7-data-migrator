/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.Variable;
import io.camunda.migrator.qa.variables.JsonSerializable;
import io.camunda.migrator.qa.variables.XmlSerializable;
import io.camunda.process.test.api.CamundaAssert;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import org.awaitility.Awaitility;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Map;

public class VariablesTest extends RuntimeMigrationAbstractTest {

  public static final String SUB_PROCESS = "subProcess";
  public static final String PARALLEL = "parallel";

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  public void shouldSetVariableWithNullValue() {
    // given
    deployProcessInC7AndC8("simpleProcess.bpmn");

    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    runtimeService.setVariable(simpleProcessInstance.getId(), "myVariable", null);

    // when
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("myVariable", null);
  }

  @Test
  public void shouldSetPrimitiveVariables() {
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    VariableMap variables = Variables.createVariables();
    variables.putValue("stringVar", "myStringVar");
    variables.putValue("booleanVar", true);
    variables.putValue("integerVar", 1234);
    variables.putValue("floatVar", 1.5f);
    variables.putValue("doubleVar", 1.5d);
    variables.putValue("shortVar", (short) 1);
    variables.putValue("byteVar", (byte) 1);
    variables.putValue("charVar", (char) 1);

    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    // when running runtime migration
    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("stringVar", "myStringVar")
        .hasVariable("booleanVar", true)
        .hasVariable("integerVar", 1234)
        .hasVariable("floatVar", 1.5)
        .hasVariable("doubleVar", 1.5d)
        .hasVariable("shortVar", (short) 1)
        .hasVariable("byteVar", (byte) 1)
        .hasVariable("charVar", (char) 1);
  }

  @Test
  public void shouldSetArrayVariables() {
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");
    List<String> stringList = Arrays.asList("one", "two", "three");
    HashMap<Integer, String> map = new HashMap<Integer, String>();
    map.put(1, "one");
    map.put(2, "two");

    // given process state in c7
    VariableMap variables = Variables.createVariables();
    variables.putValue("stringList", stringList);
    variables.putValue("map", map);

    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    // when running runtime migration
    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("stringList", stringList)
        .hasVariable("map", map);
  }
  @Test
  public void shouldSetInvalidVariableNameInFeel() {
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    VariableMap variables = Variables.createVariables();
    variables.putValue("1stC", "value");
    variables.putValue("st C", "value");
    variables.putValue("st/C", "value");
    variables.putValue("st-C", "value");
    variables.putValue("null", "value");

    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess", variables);

    // when running runtime migration
    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("1stC", "value")
        .hasVariable("st C", "value")
        .hasVariable("st/C", "value")
        .hasVariable("st-C", "value")
        .hasVariable("null", "value");
  }

  @Test
  @Disabled // https://github.com/camunda/camunda-bpm-platform/issues/5244
  public void shouldSetDateVariable() {
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");
    Date date = new Date();
    runtimeService.setVariable(simpleProcessInstance.getId(), "dateVar", date);

    // when running runtime migration
    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("dateVar", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(date));
  }

  @Test
  public void shouldSetXmlVariable() throws JsonProcessingException {
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    XmlSerializable foo = new XmlSerializable("a String", 42, true);
    String xml = objectMapper.writeValueAsString(foo);
    runtimeService.setVariable(simpleProcessInstance.getId(), "var", xml);

    // when running runtime migration
    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("var", "{\"stringProperty\":\"a String\",\"intProperty\":42,\"booleanProperty\":true}");
  }

  @Test
  public void shouldSetJsonVariable() throws JsonProcessingException {
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess");

    JsonSerializable foo = new JsonSerializable("a String", 42, true);
    String json = objectMapper.writeValueAsString(foo);
    runtimeService.setVariable(simpleProcessInstance.getId(), "var", json);

    // when running runtime migration
    runtimeMigrator.start();

    CamundaAssert.assertThat(byProcessId("simpleProcess"))
        .hasVariable("var", "{\"stringProperty\":\"a String\",\"intProperty\":42,\"booleanProperty\":true}");
  }

  @Test
  @Disabled // To be adjusted or removed with https://github.com/camunda/camunda-bpm-platform/issues/4989
  public void shouldSetFileVariable() throws JsonProcessingException {
    // deploy processes
    deployProcessInC7AndC8("simpleProcess.bpmn");

    // given process state in c7
    String fileName = "text.txt";
    String encoding = "crazy-encoding";
    String mimeType = "martini/dry";
    FileValue fileValue = Variables
        .fileValue(fileName)
        .file("ABC".getBytes())
        .encoding(encoding)
        .mimeType(mimeType)
        .create();

    VariableMap fileVar = Variables.createVariables().putValueTyped("fileVar", fileValue);
    var simpleProcessInstance = runtimeService.startProcessInstanceByKey("simpleProcess",Variables.createVariables().putValueTyped("fileVar", fileValue));

    // when running runtime migration
    runtimeMigrator.start();

    // then
    // assert c8 variable value
  }

  @Test
  public void shouldSetGlobalVariable() {
    // deploy processes
    deploySubprocessModels();
    Map<String, Object> vars = new HashMap<>();
    vars.put("variable1", "value1");
    vars.put("variable2", "value2");

    // given
    runtimeService.startProcessInstanceByKey(SUB_PROCESS, vars);
    Task currentTask = taskService.createTaskQuery().singleResult();
    runtimeService.setVariable(currentTask.getExecutionId(), "variable3", "value3");

    // when running runtime migration
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId(SUB_PROCESS))
        .hasVariableNames("variable1", "variable2", "variable3");
  }

  @Test
  public void shouldSetLocalVariable() {
    // deploy processes
    deployParallelModels();
    Map<String, Object> vars = new HashMap<>();
    vars.put("variable1", "value1");

    // given
    runtimeService.startProcessInstanceByKey(PARALLEL, vars);
    Task currentTask = taskService.createTaskQuery().taskDefinitionKey("userTask_1").singleResult();
    runtimeService.setVariableLocal(currentTask.getExecutionId(), "localVariable", "local value");

    // when running runtime migration
    runtimeMigrator.start();

    // then
    // TODO switch to CPT
    // https://github.com/camunda/camunda-bpm-platform/issues/5251
    Awaitility.await().ignoreException(ClientException.class)
        .timeout(10, TimeUnit.SECONDS)
        .untilAsserted(() -> {
      List<Variable> c8vars = camundaClient.newVariableSearchRequest()
          .filter(f -> f.name("localVariable"))
          .send()
          .join()
          .items();

      List<ElementInstance> elements = camundaClient.newElementInstanceSearchRequest()
          .filter(f -> f.elementId("userTask_1"))
          .send()
          .join()
          .items();

      assertThat(c8vars.size()).isEqualTo(1);
      var c8Var = c8vars.get(0);
      assertThat(c8Var.getValue().contains("local value")).isTrue();
      assertThat(c8Var.getScopeKey()).isNotEqualTo(elements.get(0).getProcessInstanceKey());
      assertThat(c8Var.getScopeKey()).isEqualTo(elements.get(0).getElementInstanceKey());
    });

  }

  @Test
  public void shouldSetTaskVariable() {
    // deploy processes
    deployParallelModels();
    Map<String, Object> vars = new HashMap<>();
    vars.put("variable1", "value1");

    // given
    runtimeService.startProcessInstanceByKey(PARALLEL, vars);
    Task currentTask = taskService.createTaskQuery().taskDefinitionKey("userTask_1").singleResult();
    taskService.setVariableLocal(currentTask.getId(), "localVariable", "local value");

    // when running runtime migration
    runtimeMigrator.start();

    // then
    // TODO assert local variable when this ticket is completed https://github.com/camunda/camunda/issues/32648
    Awaitility.await().ignoreException(ClientException.class)
        .timeout(10, TimeUnit.SECONDS).untilAsserted(() -> {
      List<Variable> c8vars = camundaClient.newVariableSearchRequest()
          .filter(f -> f.name("localVariable"))
          .send()
          .join()
          .items();

      List<ElementInstance> elements = camundaClient.newElementInstanceSearchRequest()
          .filter(f -> f.elementId("userTask_1"))
          .send()
          .join()
          .items();

      assertThat(c8vars.size()).isEqualTo(1);
      var c8Var = c8vars.get(0);
      assertThat(c8Var.getValue().contains("local value")).isTrue();
      assertThat(c8Var.getScopeKey()).isNotEqualTo(elements.get(0).getProcessInstanceKey());
      assertThat(c8Var.getScopeKey()).isEqualTo(elements.get(0).getElementInstanceKey());
    });
  }

  @Test
  @Disabled // https://github.com/camunda/camunda-bpm-platform/issues/5235
  public void shouldSetVariableIntoSubprocess() {
    // deploy processes
    deploySubprocessModels();
    Map<String, Object> vars = new HashMap<>();
    vars.put("variable1", "value1");
    vars.put("variable2", "value2");

    // given
    runtimeService.startProcessInstanceByKey(SUB_PROCESS, vars);
    Task currentTask = taskService.createTaskQuery().singleResult();
    runtimeService.setVariableLocal(currentTask.getExecutionId(), "localVariable", "local value");

    // when running runtime migration
    runtimeMigrator.start();

    // then
    CamundaAssert.assertThat(byProcessId(SUB_PROCESS))
        .hasVariableNames("variable1", "variable2");

    // TODO assert local variable when this ticket is completed https://github.com/camunda/camunda/issues/32648
    Awaitility.await().ignoreException(ClientException.class)
        .timeout(10, TimeUnit.SECONDS).untilAsserted(() -> {
      List<Variable> c8vars = camundaClient.newVariableSearchRequest()
          .filter(f -> f.name("localVariable"))
          .send()
          .join()
          .items();

      assertThat(c8vars.size()).isEqualTo(1);
      var c8Var = c8vars.get(0);
      assertThat(c8Var.getValue()).isEqualTo("local value");
    });
  }

  private void deploySubprocessModels() {
    String process = SUB_PROCESS;
    // C7
    var c7Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent("start_1")
        .subProcess("sub_1")
        .embeddedSubProcess()
        .startEvent("start_2")
        .userTask("userTask_1")
        .endEvent("end_2")
        .subProcessDone()
        .endEvent("end_1")
        .done();

    // C8
    var c8Model = io.camunda.zeebe.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent("start_1")
        .zeebeEndExecutionListener("migrator")
        .subProcess("sub_1")
        .embeddedSubProcess()
        .startEvent("start_2")
        .userTask("userTask_1")
        .endEvent("end_2")
        .subProcessDone()
        .endEvent("end_1")
        .done();

    deployModelInstance(process, c7Model, c8Model);
  }

  private void deployParallelModels() {
    String process = PARALLEL;
    // C7
    var c7Model = org.camunda.bpm.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent("start_1")
        .parallelGateway("fork")
        .userTask("userTask_1")
        .parallelGateway("join")
        .endEvent("end_1")
        .moveToNode("fork")
        .userTask("userTask_2")
        .connectTo("join")
        .done();

    // C8
    var c8Model = io.camunda.zeebe.model.bpmn.Bpmn.createExecutableProcess(process)
        .startEvent("start_1")
        .zeebeEndExecutionListener("migrator")
        .parallelGateway("fork")
        .userTask("userTask_1")
        .parallelGateway("join")
        .endEvent("end_1")
        .moveToNode("fork")
        .userTask("userTask_2")
        .connectTo("join")
        .done();

    deployModelInstance(process, c7Model, c8Model);
  }
}
