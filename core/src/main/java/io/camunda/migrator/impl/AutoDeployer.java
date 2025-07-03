/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AutoDeployer {

  protected static final Logger LOGGER = LoggerFactory.getLogger(AutoDeployer.class);

  @Autowired
  protected CamundaClient camundaClient;

  @Value("${migrator.deployment-dir:#{null}}")
  protected String deploymentDir;

  public void deploy() {
    // Deploy process
    var deployResource = camundaClient.newDeployResourceCommand();

    Set<Path> models = getDeploymentResources();

    DeployResourceCommandStep1.DeployResourceCommandStep2 deployResourceCommandStep2 = null;
    for (Path model : models) {
      deployResourceCommandStep2 = deployResource.addResourceFile(model.toString());
    }

    if (deployResourceCommandStep2 != null) {
      deployResourceCommandStep2.execute();
    }
  }

  public Set<Path> getDeploymentResources() {
    if (StringUtils.hasText(deploymentDir)) {
      Path resourceDir = Paths.get(deploymentDir);

      try (Stream<Path> stream = Files.walk(resourceDir)) {
        return stream.filter(file -> !Files.isDirectory(file)).collect(Collectors.toSet());
      } catch (IOException e) {
        LOGGER.error("An error occurred during auto deployment: {}", e.getMessage());
      }
    }
    return Collections.emptySet();
  }
}
