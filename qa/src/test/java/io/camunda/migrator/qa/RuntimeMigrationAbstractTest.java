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
import io.camunda.migrator.RuntimeMigrator;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import jakarta.annotation.PostConstruct;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.Deployment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@CamundaSpringProcessTest
public abstract class RuntimeMigrationAbstractTest {

  @Autowired
  protected RuntimeMigrator runtimeMigrator;

  @Autowired
  protected RepositoryService repositoryService;


  @Autowired
  protected CamundaClient camundaClient;

  @PostConstruct
  public void init() {
    runtimeMigrator.setAutoDeployment(false);
  }

  protected void deployCamunda7Process(String resourcePath) {
    Deployment deployment = repositoryService.createDeployment().addClasspathResource(resourcePath).deploy();
    if (deployment == null) {
      throw new IllegalStateException("Could not deploy process");
    }
  }

  protected void deployCamunda8Process(String resourcePath) {
    DeploymentEvent deployment = camundaClient.newDeployResourceCommand().addResourceFromClasspath(resourcePath).send().join();
    if (deployment == null) {
      throw new IllegalStateException("Could not deploy process");
    }
  }

  protected void deployProcessInC7AndC8(String fileName) {
    deployCamunda7Process("io/camunda/migrator/bpmn/c7/" + fileName);
    deployCamunda8Process("io/camunda/migrator/bpmn/c8/" + fileName);
  }

  protected void configureMaxJobsToActivate(int maxJobsCount){
    runtimeMigrator.setMaxJobsToActivate(maxJobsCount);
  }
  protected void configureMaxProcessInstances(int maxPICount){
    runtimeMigrator.setMaxProcessInstance(maxPICount);
  }
}
