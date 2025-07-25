/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.logging;

import io.camunda.migrator.impl.clients.DbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs debug messages for DbClient operations
 */
public class DbClientLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(DbClient.class);

  // DbClient Messages
  public static final String UPDATING_KEY_FOR_LEGACY_ID = "Updating key for legacyId [{}] with value [{}]";
  public static final String INSERTING_RECORD = "Inserting record [{}], [{}], [{}]";

  // DbClient Error Messages
  public static final String FAILED_TO_CHECK_EXISTENCE = "Failed to check existence for legacyId: ";
  public static final String FAILED_TO_CHECK_KEY = "Failed to check key for legacyId: ";
  public static final String FAILED_TO_FIND_LATEST_START_DATE = "Failed to find latest start date for type: ";
  public static final String FAILED_TO_UPDATE_KEY = "Failed to update key for legacyId: ";
  public static final String FAILED_TO_INSERT_RECORD = "Failed to insert record for legacyId: ";
  public static final String FAILED_TO_FIND_SKIPPED_COUNT = "Failed to find skipped count";

  public static void updatingKeyForLegacyId(String legacyProcessInstanceId, Long processInstanceKey) {
    LOGGER.debug(UPDATING_KEY_FOR_LEGACY_ID, legacyProcessInstanceId, processInstanceKey);
  }

  public static void insertingRecord(String legacyProcessInstanceId, Object startDate, Long processInstanceKey) {
    LOGGER.debug(INSERTING_RECORD, legacyProcessInstanceId, startDate, processInstanceKey);
  }
}
