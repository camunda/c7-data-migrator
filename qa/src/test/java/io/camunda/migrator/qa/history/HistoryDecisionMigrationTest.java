/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migrator.qa.history;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HistoryDecisionMigrationTest extends HistoryMigrationAbstractTest {

  @Test
  public void migrateSingleHistoricDecision() {
    // given
    deployer.deployCamunda7Decision("simpleDmn.dmn");

    // when
    historyMigrator.migrate();

    // then
    List<DecisionDefinitionEntity> migratedDecisions = searchHistoricDecisionDefinitions("simpleDecisionId");
    assertThat(migratedDecisions).singleElement()
        .satisfies(decision -> {
          assertThat(decision.decisionDefinitionId()).isEqualTo("simpleDecisionId");
          assertThat(decision.decisionDefinitionKey()).isNotNull();
          assertThat(decision.version()).isEqualTo(1);
          assertThat(decision.name()).isEqualTo("simpleDecisionName");
          assertThat(decision.tenantId()).isEqualTo("<default>");
          assertThat(decision.decisionRequirementsKey()).isNull();
          assertThat(decision.decisionRequirementsId()).isNull();
        });
  }

  @Test
  public void migrateHistoricDecisionWithRequirements() {
    // given
    deployer.deployCamunda7Decision("simpleDmnWithReqs.dmn");

    // when
    historyMigrator.migrate();
    List<DecisionDefinitionEntity> firstDecision = searchHistoricDecisionDefinitions("simpleDmnWithReqs1Id");
    List<DecisionDefinitionEntity> secondDecision = searchHistoricDecisionDefinitions("simpleDmnWithReqs2Id");
    List<DecisionRequirementsEntity> decisionReqs = searchHistoricDecisionRequirements("simpleDmnWithReqsId");

    // then
    assertThat(decisionReqs).singleElement()
        .satisfies(decisionRequirements -> {
          assertThat(decisionRequirements.decisionRequirementsId()).isEqualTo("simpleDmnWithReqsId");
          assertThat(decisionRequirements.decisionRequirementsKey()).isNotNull();
          assertThat(decisionRequirements.version()).isEqualTo(1);
          assertThat(decisionRequirements.name()).isEqualTo("simpleDmnWithReqsName");
          assertThat(decisionRequirements.tenantId()).isEqualTo("<default>");
          assertThat(decisionRequirements.xml()).isNull();
          assertThat(decisionRequirements.resourceName()).isEqualTo("io/camunda/migrator/dmn/c7/simpleDmnWithReqs.dmn");
        });
    Long decisionReqsKey = decisionReqs.get(0).decisionRequirementsKey();

    assertThat(firstDecision).singleElement()
        .satisfies(decision -> {
          assertThat(decision.decisionDefinitionId()).isEqualTo("simpleDmnWithReqs1Id");
          assertThat(decision.decisionDefinitionKey()).isNotNull();
          assertThat(decision.version()).isEqualTo(1);
          assertThat(decision.name()).isEqualTo("simpleDmnWithReqs1Name");
          assertThat(decision.tenantId()).isEqualTo("<default>");
          assertThat(decision.decisionRequirementsKey()).isEqualTo(decisionReqsKey);
          assertThat(decision.decisionRequirementsId()).isEqualTo("simpleDmnWithReqsId");
        });

    assertThat(secondDecision).singleElement()
        .satisfies(decision -> {
          assertThat(decision.decisionDefinitionId()).isEqualTo("simpleDmnWithReqs2Id");
          assertThat(decision.decisionDefinitionKey()).isNotNull();
          assertThat(decision.version()).isEqualTo(1);
          assertThat(decision.name()).isEqualTo("simpleDmnWithReqs2Name");
          assertThat(decision.tenantId()).isEqualTo("<default>");
          assertThat(decision.decisionRequirementsKey()).isEqualTo(decisionReqsKey);
          assertThat(decision.decisionRequirementsId()).isEqualTo("simpleDmnWithReqsId");
        });
  }
}
