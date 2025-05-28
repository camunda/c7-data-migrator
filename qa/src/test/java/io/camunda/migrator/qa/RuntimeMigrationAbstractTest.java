/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.migrator.RuntimeMigrator;
import io.camunda.migrator.history.IdKeyMapper;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import jakarta.annotation.PostConstruct;

import java.util.List;

import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.repository.Deployment;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@CamundaSpringProcessTest
public abstract class RuntimeMigrationAbstractTest {

  // Migrator ---------------------------------------

  @Autowired
  protected RuntimeMigrator runtimeMigrator;

  @Autowired
  private IdKeyMapper idKeyMapper;

  // C7 ---------------------------------------

  @Autowired
  protected RepositoryService repositoryService;

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected TaskService taskService;

  // C8 ---------------------------------------

  @Autowired
  protected CamundaClient camundaClient;

  @AfterEach
  public void cleanup() {
    // C7
    repositoryService.createDeploymentQuery().list().forEach(d -> repositoryService.deleteDeployment(d.getId(), true));

    // C8
    List<ProcessInstance> items = camundaClient.newProcessInstanceSearchRequest().send().join().items();
    items.forEach(i -> camundaClient.newDeleteResourceCommand(i.getProcessInstanceKey()));

    // Migrator table
    idKeyMapper.findAllProcessInstanceIds().forEach(id -> idKeyMapper.delete(id));

    // reset runtime migrator
    runtimeMigrator.setRetryMode(false);

  }

  protected void deployCamunda7Process(String resourcePath) {
    Deployment deployment = repositoryService.createDeployment().addClasspathResource(resourcePath).deploy();
    if (deployment == null) {
      throw new IllegalStateException("Could not deploy process");
    }
  }

  protected void deployCamunda8Process(String resourcePath) {
    DeploymentEvent deployment = camundaClient.newDeployResourceCommand().addResourceFromClasspath(resourcePath).send()
        .join();

    if (deployment == null) {
      throw new IllegalStateException("Could not deploy process");
    }
  }

  protected void deployProcessInC7AndC8(String fileName) {
    deployCamunda7Process("io/camunda/migrator/bpmn/c7/" + fileName);
    deployCamunda8Process("io/camunda/migrator/bpmn/c8/" + fileName);
  }
}