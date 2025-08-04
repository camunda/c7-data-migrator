/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.runtime;

import static io.camunda.migrator.MigratorMode.MIGRATE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import io.camunda.migrator.RuntimeMigrator;
import io.camunda.migrator.qa.AbstractMigratorTest;
import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.process.test.api.CamundaSpringProcessTest;

import java.util.List;

import java.util.Optional;
import org.awaitility.Awaitility;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

@CamundaSpringProcessTest
public abstract class RuntimeMigrationAbstractTest extends AbstractMigratorTest {

  // Migrator ---------------------------------------

  @Autowired
  protected RuntimeMigrator runtimeMigrator;

  @Autowired
  private IdKeyMapper idKeyMapper;

  @Autowired
  protected DbClient dbClient;

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
    ClockUtil.reset();
    repositoryService.createDeploymentQuery().list().forEach(d -> repositoryService.deleteDeployment(d.getId(), true));

    // C8
    List<ProcessInstance> items = camundaClient.newProcessInstanceSearchRequest().execute().items();
    for (ProcessInstance i : items) {
      try {
        camundaClient.newDeleteResourceCommand(i.getProcessInstanceKey()).execute();
      } catch (io.camunda.client.api.command.ClientStatusException e) {
        if (!e.getMessage().contains("NOT_FOUND")) {
          throw e;
        }
        // Ignore NOT_FOUND errors as the instance might have been deleted already
      }
    }

    // Migrator table
    dbClient.deleteAllMappings();

    // reset runtime migrator
    runtimeMigrator.setMode(MIGRATE);
  }

  protected Optional<Variable> getVariableByScope(Long processInstanceKey, Long scopeKey, String variableName) {
    List<Variable> variables = camundaClient.newVariableSearchRequest().execute().items();

    return variables.stream()
        .filter(v -> v.getProcessInstanceKey().equals(processInstanceKey))
        .filter(v -> v.getScopeKey().equals(scopeKey))
        .filter(v -> v.getName().equals(variableName))
        .findFirst();
  }

  protected void assertThatProcessInstanceCountIsEqualTo(int expected) {
    Awaitility.await().ignoreException(ClientException.class).untilAsserted(() -> {
      assertThat(camundaClient.newProcessInstanceSearchRequest().execute().items().size()).isEqualTo(expected);
    });
  }

  public List<IdKeyDbModel> findSkippedRuntimeProcessInstances() {
    return idKeyMapper.findSkippedByType(IdKeyMapper.TYPE.RUNTIME_PROCESS_INSTANCE, 0, Integer.MAX_VALUE);
  }

}