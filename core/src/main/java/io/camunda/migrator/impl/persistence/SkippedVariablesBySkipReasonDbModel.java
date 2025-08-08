/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.persistence;

/**
 * Model representing skipped historic variables grouped by skip reason.
 */
public class SkippedVariablesBySkipReasonDbModel {

  private String skipReason;
  private Long skippedVariableCount;
  private String skippedVariableIds;
  private String skippedVariableNames;
  private String processInstanceIds;

  public SkippedVariablesBySkipReasonDbModel() {}

  public SkippedVariablesBySkipReasonDbModel(String skipReason, Long skippedVariableCount,
                                             String skippedVariableIds, String skippedVariableNames,
                                             String processInstanceIds) {
    this.skipReason = skipReason;
    this.skippedVariableCount = skippedVariableCount;
    this.skippedVariableIds = skippedVariableIds;
    this.skippedVariableNames = skippedVariableNames;
    this.processInstanceIds = processInstanceIds;
  }

  public String getSkipReason() {
    return skipReason;
  }

  public void setSkipReason(String skipReason) {
    this.skipReason = skipReason;
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

  public String getProcessInstanceIds() {
    return processInstanceIds;
  }

  public void setProcessInstanceIds(String processInstanceIds) {
    this.processInstanceIds = processInstanceIds;
  }

  @Override
  public String toString() {
    return "SkippedVariablesBySkipReasonDbModel{" +
           "skipReason='" + skipReason + '\'' +
           ", skippedVariableCount=" + skippedVariableCount +
           ", skippedVariableIds='" + skippedVariableIds + '\'' +
           ", skippedVariableNames='" + skippedVariableNames + '\'' +
           ", processInstanceIds='" + processInstanceIds + '\'' +
           '}';
  }
}
