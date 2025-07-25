/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.history;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.write.service.RdbmsPurger;
import io.camunda.migrator.HistoryMigrator;
import io.camunda.migrator.config.C8DataSourceConfigured;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.migrator.qa.util.ProcessDefinitionDeployer;
import io.camunda.migrator.qa.util.WithMultiDb;
import io.camunda.migrator.qa.util.WithSpringProfile;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.UserTaskQuery;
import java.util.List;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@SpringBootTest
@WithMultiDb
@Import(HistoryMigrationAbstractTest.HistoryCustomConfiguration.class)
@WithSpringProfile("history")
public abstract class HistoryMigrationAbstractTest {

  @Autowired
  protected ProcessDefinitionDeployer deployer;

  // Migrator ---------------------------------------

  @Autowired
  protected HistoryMigrator historyMigrator;

  @Autowired
  protected IdKeyMapper idKeyMapper;

  // C7 ---------------------------------------

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  protected TaskService taskService;

  @Autowired
  protected RepositoryService repositoryService;

  // C8 ---------------------------------------

  @Autowired
  protected RdbmsPurger rdbmsPurger;

  @Autowired
  protected RdbmsService rdbmsService;

  @AfterEach
  public void cleanup() {
    // C7
    ClockUtil.reset();
    repositoryService.createDeploymentQuery().list().forEach(d -> repositoryService.deleteDeployment(d.getId(), true));

    // Migrator table
    idKeyMapper.findAllIds().forEach(id -> idKeyMapper.delete(id));

    // C8
    rdbmsPurger.purgeRdbms();
  }

  public List<ProcessInstanceEntity> searchHistoricProcessInstances(String processDefinitionId) {
    return rdbmsService.getProcessInstanceReader()
        .search(ProcessInstanceQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processDefinitionIds(processDefinitionId))))
        .items();
  }

  public List<UserTaskEntity> searchHistoricUserTasks(long processInstanceKey) {
    return rdbmsService.getUserTaskReader()
        .search(UserTaskQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processInstanceKeys(processInstanceKey))))
        .items();
  }

  public List<FlowNodeInstanceEntity> searchHistoricFlowNodesForType(long processInstanceKey, FlowNodeInstanceEntity.FlowNodeType type) {
    return rdbmsService.getFlowNodeInstanceReader()
        .search(FlowNodeInstanceQuery.of(queryBuilder ->
            queryBuilder.filter(filterBuilder ->
                filterBuilder.processInstanceKeys(processInstanceKey).types(type))))
        .items();
  }

  @Configuration
  public static class HistoryCustomConfiguration {

    @Bean
    @Conditional(C8DataSourceConfigured.class)
    public RdbmsPurger rdbmsPurger(
        PurgeMapper purgeMapper,
        VendorDatabaseProperties vendorDatabaseProperties) {
      return new RdbmsPurger(purgeMapper, vendorDatabaseProperties);
    }

  }
}