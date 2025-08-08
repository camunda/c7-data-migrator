/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.persistence;

/**
 * Model representing skipped historic variables grouped by process instance ID.
 */
public class SkippedVariablesByProcessInstanceDbModel {

  private String processInstanceId;
  private Long skippedVariableCount;
  private String skippedVariableIds;
  private String skippedVariableNames;
  private String skipReasons;

  public SkippedVariablesByProcessInstanceDbModel() {}

  public SkippedVariablesByProcessInstanceDbModel(String processInstanceId, Long skippedVariableCount,
                                                  String skippedVariableIds, String skippedVariableNames,
                                                  String skipReasons) {
    this.processInstanceId = processInstanceId;
    this.skippedVariableCount = skippedVariableCount;
    this.skippedVariableIds = skippedVariableIds;
    this.skippedVariableNames = skippedVariableNames;
    this.skipReasons = skipReasons;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public void setProcessInstanceId(String processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  public Long getSkippedVariableCount() {
    return skippedVariableCount;
  }

  public void setSkippedVariableCount(Long skippedVariableCount) {
    this.skippedVariableCount = skippedVariableCount;
  }

  public String getSkippedVariableIds() {
    return skippedVariableIds;
  }

  public void setSkippedVariableIds(String skippedVariableIds) {
    this.skippedVariableIds = skippedVariableIds;
  }

  public String getSkippedVariableNames() {
    return skippedVariableNames;
  }

  public void setSkippedVariableNames(String skippedVariableNames) {
    this.skippedVariableNames = skippedVariableNames;
  }

  public String getSkipReasons() {
    return skipReasons;
  }

  public void setSkipReasons(String skipReasons) {
    this.skipReasons = skipReasons;
  }

  @Override
  public String toString() {
    return "SkippedVariablesByProcessInstanceDbModel{" +
           "processInstanceId='" + processInstanceId + '\'' +
           ", skippedVariableCount=" + skippedVariableCount +
           ", skippedVariableIds='" + skippedVariableIds + '\'' +
           ", skippedVariableNames='" + skippedVariableNames + '\'' +
           ", skipReasons='" + skipReasons + '\'' +
           '}';
  }
}
