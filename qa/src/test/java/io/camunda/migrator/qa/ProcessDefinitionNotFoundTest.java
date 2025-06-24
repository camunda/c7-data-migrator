/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.api.command.ClientStatusException;
import io.camunda.migrator.RuntimeMigrator;
import io.camunda.migrator.RuntimeMigratorException;
import io.camunda.migrator.persistence.IdKeyDbModel;
import io.camunda.migrator.persistence.IdKeyMapper;
import io.github.netmikey.logunit.api.LogCapturer;
import java.util.List;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

class ProcessDefinitionNotFoundTest extends RuntimeMigrationAbstractTest {

  @RegisterExtension
  protected final LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

  @Autowired
  private IdKeyMapper idKeyMapper;

  @Test
  public void shouldSkipOnMissingC8Deployment() {
    // given
    deployCamunda7Process("io/camunda/migrator/bpmn/c7/simpleProcess.bpmn");
    var c7Instance = runtimeService.startProcessInstanceByKey("simpleProcess");

    // when
    runtimeMigrator.start();

    // then
    logs.assertContains(String.format(
        "Process instance with legacyId [%s] can't be migrated: "
            + "No C8 deployment found for process ID [%s] required for instance with "
            + "legacyID [%s].",  c7Instance.getId(), "simpleProcess", c7Instance.getId()));
    assertThat(camundaClient.newProcessInstanceSearchRequest().send().join().items().size()).isEqualTo(0);
    List<IdKeyDbModel> skippedProcessInstanceIds = idKeyMapper.findSkipped().stream().toList();
    assertThat(skippedProcessInstanceIds.size()).isEqualTo(1);
    assertThat(skippedProcessInstanceIds.getFirst().id()).isEqualTo(c7Instance.getId());
  }

  @Test
  public void shouldSkipNotExistingProcessIdempotently() {
    // given
    deployCamunda7Process("io/camunda/migrator/bpmn/c7/simpleProcess.bpmn");
   deployProcessInC7AndC8("userTaskProcess.bpmn");

    var c7Instance = runtimeService.startProcessInstanceByKey("simpleProcess");
    ClockUtil.offset(50_000L);
    runtimeService.startProcessInstanceByKey("userTaskProcessId");

    // when
    runtimeMigrator.start();

    // then
    logs.assertContains(String.format(
        "Process instance with legacyId [%s] can't be migrated: "
            + "No C8 deployment found for process ID [%s] required for instance with "
            + "legacyID [%s].",  c7Instance.getId(), "simpleProcess", c7Instance.getId()));
    logs.getEvents().clear();

    // when
    runtimeMigrator.start();

    // then
    logs.assertContains(String.format(
        "Process instance with legacyId [%s] can't be migrated: "
            + "No C8 deployment found for process ID [%s] required for instance with "
            + "legacyID [%s].",  c7Instance.getId(), "simpleProcess", c7Instance.getId()));
  }

}
