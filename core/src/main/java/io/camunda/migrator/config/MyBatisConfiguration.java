/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.config;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.AuthorizationMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.FormMapper;
import io.camunda.db.rdbms.sql.GroupMapper;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.JobMapper;
import io.camunda.db.rdbms.sql.MappingMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.PurgeMapper;
import io.camunda.db.rdbms.sql.RoleMapper;
import io.camunda.db.rdbms.sql.TenantMapper;
import io.camunda.db.rdbms.sql.UserMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.VariableMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;

import io.camunda.migrator.history.IdKeyMapper;
import liquibase.integration.spring.MultiTenantSpringLiquibase;
import liquibase.integration.spring.SpringLiquibase;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.OffsetDateTimeTypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * This is a copy from https://github.com/camunda/camunda/blob/main/dist/src/main/java/io/camunda/application/commons/rdbms/MyBatisConfiguration.java
 */
@Configuration
public class MyBatisConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(MyBatisConfiguration.class);

  @Bean
  public SpringLiquibase liquibase() {
    SpringLiquibase liquibase = new SpringLiquibase();
    liquibase.setShouldRun(false);
    return liquibase;
  }

  @Bean
  public MultiTenantSpringLiquibase createMigratorSchema(@Qualifier("targetDataSource") DataSource dataSource, @Value("${migrator.target.table-prefix:}") String tablePrefix) {
    return createSchema(dataSource, tablePrefix, "db/changelog/migrator/db.changelog-master.yaml");
  }

  @Bean
  @ConditionalOnProperty(prefix = "migrator.rdbms-exporter", name = "auto-ddl", havingValue = "true")
  public MultiTenantSpringLiquibase createRdbmsExporterSchema(@Qualifier("targetDataSource") DataSource dataSource, @Value("${migrator.target.table-prefix:}") String tablePrefix) {
    return createSchema(dataSource, tablePrefix, "db/changelog/rdbms-exporter/changelog-master.xml");
  }

  protected MultiTenantSpringLiquibase createSchema(DataSource dataSource, String tablePrefix, String changeLogFile) {
    String prefix = StringUtils.trimToEmpty(tablePrefix);
    LOGGER.info("Creating table schema with Liquibase change log file '{}' with table prefix '{}'.", changeLogFile , prefix);

    var moduleConfig = new MultiTenantSpringLiquibase();
    moduleConfig.setDataSource(dataSource);
    moduleConfig.setDatabaseChangeLogTable(prefix + "DATABASECHANGELOG");
    moduleConfig.setDatabaseChangeLogLockTable(prefix + "DATABASECHANGELOGLOCK");
    moduleConfig.setParameters(Map.of("prefix", prefix));
    moduleConfig.setChangeLog(changeLogFile);

    return moduleConfig;
  }

  @Bean
  public RdbmsDatabaseIdProvider databaseIdProvider(
      @Value("${camunda.database.database-vendor-id:}") final String vendorId) {
    return new RdbmsDatabaseIdProvider(vendorId);
  }

  @Bean
  public VendorDatabaseProperties databaseProperties(
      @Qualifier("targetDataSource") DataSource dataSource, final RdbmsDatabaseIdProvider databaseIdProvider)
      throws IOException {
    final var databaseId = databaseIdProvider.getDatabaseId(dataSource);
    LOGGER.info("Detected databaseId: {}", databaseId);

    final Properties properties = new Properties();
    final var migratorFile = "db/properties/" + databaseId + ".properties";
    final var c8File = "db/vendor-properties/" + databaseId + ".properties";
    loadPropertiesFile(databaseId, properties, c8File);
    loadPropertiesFile(databaseId, properties, migratorFile);

    return new VendorDatabaseProperties(properties);
  }

  private void loadPropertiesFile(String databaseId, Properties properties, String file) throws IOException {
    try (final var propertiesInputStream = getClass().getClassLoader().getResourceAsStream(file)) {
      if (propertiesInputStream != null) {
        properties.load(propertiesInputStream);
      } else {
        throw new IllegalArgumentException(
            "No vendor properties found for databaseId " + databaseId);
      }
    }
  }

  @Bean
  public SqlSessionFactory sqlSessionFactory(
      @Qualifier("targetDataSource") DataSource targetDataSource,
      final DatabaseIdProvider databaseIdProvider,
      final VendorDatabaseProperties databaseProperties,
      @Value("${camunda.database.index-prefix:}") final String indexPrefix)
      throws Exception {

    final var configuration = new org.apache.ibatis.session.Configuration();
    configuration.setJdbcTypeForNull(JdbcType.NULL);
    configuration.getTypeHandlerRegistry().register(OffsetDateTimeTypeHandler.class);

    final SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setConfiguration(configuration);
    factoryBean.setDataSource(targetDataSource);
    factoryBean.setDatabaseIdProvider(databaseIdProvider);
    factoryBean.addMapperLocations(
        new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));

    final Properties p = new Properties();
    p.put("prefix", StringUtils.trimToEmpty(indexPrefix));
    p.putAll(databaseProperties.properties());
    factoryBean.setConfigurationProperties(p);
    return factoryBean.getObject();
  }

  @Bean
  public MapperFactoryBean<JobMapper> jobMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, JobMapper.class);
  }

  @Bean
  public MapperFactoryBean<BatchOperationMapper> batchOperationMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, BatchOperationMapper.class);
  }

  @Bean
  public MapperFactoryBean<AuthorizationMapper> authorizationMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, AuthorizationMapper.class);
  }

  @Bean
  public MapperFactoryBean<DecisionDefinitionMapper> decisionDefinitionMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, DecisionDefinitionMapper.class);
  }

  @Bean
  public MapperFactoryBean<DecisionInstanceMapper> decisionInstanceMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, DecisionInstanceMapper.class);
  }

  @Bean
  public MapperFactoryBean<DecisionRequirementsMapper> decisionRequirementsMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, DecisionRequirementsMapper.class);
  }

  @Bean
  public MapperFactoryBean<FlowNodeInstanceMapper> flowNodeInstanceMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, FlowNodeInstanceMapper.class);
  }

  @Bean
  public MapperFactoryBean<GroupMapper> groupInstanceMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, GroupMapper.class);
  }

  @Bean
  public MapperFactoryBean<IncidentMapper> incidentMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, IncidentMapper.class);
  }

  @Bean
  public MapperFactoryBean<ProcessInstanceMapper> processInstanceMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, ProcessInstanceMapper.class);
  }

  @Bean
  public MapperFactoryBean<ProcessDefinitionMapper> processDeploymentMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, ProcessDefinitionMapper.class);
  }

  @Bean
  public MapperFactoryBean<TenantMapper> tenantMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, TenantMapper.class);
  }

  @Bean
  public MapperFactoryBean<VariableMapper> variableMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, VariableMapper.class);
  }

  @Bean
  public MapperFactoryBean<RoleMapper> roleMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, RoleMapper.class);
  }

  @Bean
  public MapperFactoryBean<UserMapper> userMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, UserMapper.class);
  }

  @Bean
  public MapperFactoryBean<UserTaskMapper> userTaskMapper(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, UserTaskMapper.class);
  }

  @Bean
  public MapperFactoryBean<FormMapper> formMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, FormMapper.class);
  }

  @Bean
  public MapperFactoryBean<MappingMapper> mappingMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, MappingMapper.class);
  }

  @Bean
  public MapperFactoryBean<ExporterPositionMapper> exporterPosition(
      final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, ExporterPositionMapper.class);
  }

  @Bean
  public MapperFactoryBean<PurgeMapper> purgeMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, PurgeMapper.class);
  }

  @Bean
  public MapperFactoryBean<IdKeyMapper> idKeyMapper(final SqlSessionFactory sqlSessionFactory) {
    return createMapperFactoryBean(sqlSessionFactory, IdKeyMapper.class);
  }

  private <T> MapperFactoryBean<T> createMapperFactoryBean(
      final SqlSessionFactory sqlSessionFactory, final Class<T> clazz) {
    final MapperFactoryBean<T> factoryBean = new MapperFactoryBean<>(clazz);
    factoryBean.setSqlSessionFactory(sqlSessionFactory);
    return factoryBean;
  }
}
