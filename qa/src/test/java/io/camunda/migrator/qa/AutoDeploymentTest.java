/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;

import io.camunda.migrator.impl.AutoDeployer;
import java.nio.file.FileSystems;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class AutoDeploymentTest extends RuntimeMigrationAbstractTest {

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("migrator.deployment-dir",
        () -> FileSystems.getDefault().getPath("src/test/resources/my-resources").toAbsolutePath().toString());
  }

  @Autowired
  protected AutoDeployer autoDeployer;

  @Test
  public void shouldAutoDeploy() {
    // given: a BPMN process is placed under ./my-resources

    // when: starting up the Migrator, the process is deployed automatically to C8.
    autoDeployer.deploy();

    // then: we can start a new process instance
    assertThatCode(() -> camundaClient.newCreateInstanceCommand()
        .bpmnProcessId("simpleProcess")
        .latestVersion()
        .send()
        .join()).doesNotThrowAnyException();
  }
}