/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class HistoryMigrationSkippingTest extends HistoryMigrationAbstractTest {

  @Autowired
  protected DbClient dbClient;

  @Test
  public void shouldSkipElementsWhenProcessDefinitionIsSkipped() {
    // given state in c7
    deployer.deployCamunda7Process("userTaskProcess.bpmn");

    for(int i = 0; i < 5; i++) {
      runtimeService.startProcessInstanceByKey("userTaskProcessId");
    }
    for (Task task : taskService.createTaskQuery().list()) {
      taskService.complete(task.getId());
    }

    // and the process definitions is manually set as skipped
    String legacyId = repositoryService.createProcessDefinitionQuery().singleResult().getId();
    dbClient.insert(legacyId, null, IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);

    // when history is migrated
    historyMigrator.migrate();

    // then nothing was migrated
    assertThat(searchHistoricProcessInstances("userTaskProcessId").size()).isEqualTo(0);

    // and all elements for the definition were skipped skipped
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE)).isEqualTo(5);
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_FLOW_NODE)).isEqualTo(15);
    assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_USER_TASK)).isEqualTo(5);
  }

}
