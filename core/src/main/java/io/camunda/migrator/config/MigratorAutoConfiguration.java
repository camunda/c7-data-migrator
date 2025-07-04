/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.config;

import static org.camunda.bpm.engine.ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE;
import static org.camunda.bpm.engine.ProcessEngineConfiguration.HISTORY_AUTO;

import io.camunda.migrator.AutoDeployer;
import io.camunda.migrator.HistoryMigrator;
import io.camunda.migrator.RuntimeMigrator;
import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.converter.ConverterConfiguration;

import io.camunda.migrator.impl.DateVariableInterceptor;
import io.camunda.migrator.impl.DefaultVariableInterceptor;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.persistence.StrongUuidGenerator;
import org.camunda.bpm.engine.spring.ProcessEngineFactoryBean;
import org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.camunda.bpm.engine.spring.SpringProcessEngineServicesConfiguration;
import org.camunda.spin.plugin.impl.SpinProcessEnginePlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Import({
    SpringProcessEngineServicesConfiguration.class,
    JacksonConfiguration.class,
    MyBatisConfiguration.class,
    RdbmsConfiguration.class,
    ConverterConfiguration.class,
    AutoDeployer.class,
    HistoryMigrator.class,
    RuntimeMigrator.class
})
@AutoConfigureAfter({
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
@EnableConfigurationProperties({ MigratorProperties.class })
@Configuration
public class MigratorAutoConfiguration {

  @Configuration
  static class DataSourcesConfiguration {

    protected DataSourceBuilder<?> defaultDataSource = DataSourceBuilder.create()
        .url("jdbc:h2:mem:migrator")
        .username("sa")
        .password("sa")
        .driverClassName("org.h2.Driver");

    @Bean
    @ConfigurationProperties("migrator.source")
    @Primary
    public DataSource sourceDataSource() {
      return defaultDataSource.build();
    }

    @Bean
    public PlatformTransactionManager sourceTransactionManager(@Qualifier("sourceDataSource") DataSource sourceDataSource) {
      return new DataSourceTransactionManager(sourceDataSource);
    }

    @Bean
    @ConfigurationProperties("migrator.target")
    public DataSource targetDataSource() {
      return defaultDataSource.build();
    }

    @Bean
    public PlatformTransactionManager targetTransactionManager(@Qualifier("targetDataSource") DataSource targetDataSource) {
      return new DataSourceTransactionManager(targetDataSource);
    }
  }

  @Autowired
  protected DataSource sourceDataSource;

  @Autowired
  protected PlatformTransactionManager sourceTransactionManager;

  @Bean
  public DefaultVariableInterceptor defaultVariableInterceptor() {
    return new DefaultVariableInterceptor();
  }

  @Bean
  public DateVariableInterceptor dateVariableInterceptor() {
    return new DateVariableInterceptor();
  }

  @Bean
  public ProcessEngineConfigurationImpl processEngineConfiguration(@Value("${migrator.c7.auto-ddl:false}") final boolean autoDdl) {
    var config = new SpringProcessEngineConfiguration();
    config.setDataSource(sourceDataSource);
    config.setTransactionManager(sourceTransactionManager);
    config.setHistory(HISTORY_AUTO);
    config.setJobExecutorActivate(false);
    config.setIdGenerator(new StrongUuidGenerator());
    config.getProcessEnginePlugins().add(new SpinProcessEnginePlugin());

    if (autoDdl) {
      config.setDatabaseSchemaUpdate(DB_SCHEMA_UPDATE_TRUE);
    }

    return config;
  }

  @Configuration
  static class PecConfiguration {

    @Autowired
    protected ProcessEngineConfigurationImpl processEngineConfiguration;

    @Bean
    public ProcessEngineFactoryBean processEngineFactoryBean() {
      final ProcessEngineFactoryBean factoryBean = new ProcessEngineFactoryBean();
      factoryBean.setProcessEngineConfiguration(processEngineConfiguration);

      return factoryBean;
    }
  }

}
