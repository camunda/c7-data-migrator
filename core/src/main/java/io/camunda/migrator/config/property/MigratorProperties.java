/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(MigratorProperties.PREFIX)
public class MigratorProperties {

  public static final int DEFAULT_BATCH_SIZE = 500;
  public static final String PREFIX = "camunda.migrator";

  public enum DataSource {
    C7, C8
  }

  protected Integer batchSize = DEFAULT_BATCH_SIZE;
  protected DataSource dataSource = DataSource.C7;

  protected Boolean autoDdl;
  protected String tablePrefix;

  protected C7Properties c7;
  protected C8Properties c8;

  protected List<InterceptorProperty> interceptors;

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public C7Properties getC7() {
    return c7;
  }

  public void setC7(C7Properties c7) {
    this.c7 = c7;
  }

  public C8Properties getC8() {
    return c8;
  }

  public void setC8(C8Properties c8) {
    this.c8 = c8;
  }

  public Boolean getAutoDdl() {
    return autoDdl;
  }

  public void setAutoDdl(Boolean autoDdl) {
    this.autoDdl = autoDdl;
  }

  public String getTablePrefix() {
    return tablePrefix;
  }

  public void setTablePrefix(String tablePrefix) {
    this.tablePrefix = tablePrefix;
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  public void setDataSource(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  public List<InterceptorProperty> getInterceptors() {
    return interceptors;
  }

  public void setInterceptors(List<InterceptorProperty> interceptors) {
    this.interceptors = interceptors;
  }
}
