/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.converter;

import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static io.camunda.migrator.impl.util.ConverterUtil.getNextKey;

public class ProcessDefinitionConverter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessDefinitionConverter.class);

  @Autowired
  private RepositoryService repositoryService;

  public ProcessDefinitionDbModel apply(ProcessDefinition legacyProcessDefinition) {
    String bpmnXml = getBpmnXmlAsString(legacyProcessDefinition);

    return new ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder()
        .processDefinitionKey(getNextKey())
        .processDefinitionId(legacyProcessDefinition.getId())
        .resourceName(legacyProcessDefinition.getResourceName())
        .name(legacyProcessDefinition.getName())
        .tenantId(legacyProcessDefinition.getTenantId())
        .versionTag(legacyProcessDefinition.getVersionTag())
        .version(legacyProcessDefinition.getVersion())
        .bpmnXml(bpmnXml)
        .formId(null) // TODO
        .build();
  }

  private String getBpmnXmlAsString(ProcessDefinition processDefinition) {
    try {
      var resourceStream = repositoryService.getResourceAsStream(processDefinition.getDeploymentId(),
          processDefinition.getResourceName());

      return readInputStreamToString(resourceStream);
    } catch (IOException e) {
      LOGGER.error("Error while fetching resource stream for process definition with id={} due to: {}", processDefinition.getId(), e.getMessage());
      return null;
    }
  }

  private String readInputStreamToString(InputStream inputStream) throws IOException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];

    for (int length; (length = inputStream.read(buffer)) != -1; ) {
      result.write(buffer, 0, length);
    }

    return result.toString(StandardCharsets.UTF_8);
  }
}
