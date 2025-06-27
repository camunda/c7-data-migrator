/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides centralized logging for the C7 Data Migrator.
 * Uses parameterized logging for better performance and consistent formatting.
 */
public class MigratorLogger {

  protected static final Logger log = LoggerFactory.getLogger(MigratorLogger.class);

  // Error message templates
  protected static final String MSG_ERROR_PROCESS_FETCH = "Failed to fetch process instance with legacyId: %s";
  protected static final String MSG_ERROR_PROCESS_CREATE = "Failed to create process instance for legacyId: %s";
  protected static final String MSG_ERROR_JOBS_FETCH = "Failed to fetch migrator jobs";
  protected static final String MSG_ERROR_LEGACY_ID_FETCH = "Failed to fetch legacyId for job with key: %s";
  protected static final String MSG_ERROR_ACTIVITY_FETCH = "Failed to fetch activity for job with legacyId: %s";
  protected static final String MSG_ERROR_JOBS_ACTIVATION = "Failed to activate jobs";
  protected static final String MSG_ERROR_NO_C8_PROCESS = "No C8 process found for process ID [%s] required for instance with legacyID [%s]";
  protected static final String MSG_ERROR_MULTI_INSTANCE = "Found multi-instance loop characteristics for %s in C7 process instance %s";
  protected static final String MSG_ERROR_ELEMENT_NOT_IN_C8 = "C7 instance detected in a flow node which does not exist in C8 model. Instance legacyId: [%s], Model legacyId: [%s], Element Id: [%s]";

  // Info and debug message templates
  protected static final String MSG_INFO_FETCHING = "Starting to fetch process instances for migration";
  protected static final String MSG_DEBUG_LATEST_START_DATE = "Latest start date for process instances: {}";
  protected static final String MSG_INFO_SKIPPING = "Skipping process instance with legacyId: {}";
  protected static final String MSG_DEBUG_STARTING = "Starting new C8 process instance with legacyId: [{}]";
  protected static final String MSG_DEBUG_STARTED = "Successfully started C8 process instance with key: [{}]";
  protected static final String MSG_DEBUG_UPDATING = "Updating key mapping - legacyId: [{}], newKey: [{}]";
  protected static final String MSG_DEBUG_INSERTING = "Inserting new mapping record: {}";
  protected static final String MSG_DEBUG_VALIDATING = "Validating legacy process instance: {}";
  protected static final String MSG_DEBUG_COLLECTING = "Collecting active descendant activities for legacyId: [{}]";
  protected static final String MSG_DEBUG_FOUND_ACTIVITIES = "Found {} active activity instances to validate";
  protected static final String MSG_INFO_ACTIVATING = "Starting activation of migrator jobs";
  protected static final String MSG_DEBUG_JOBS_FOUND = "Found {} migrator jobs to process";
  protected static final String MSG_WARN_INSTANCE_GONE = "Process instance {} no longer exists. May have been completed or cancelled";

  public static String formatProcessInstanceFetchError(String legacyId) {
    return String.format(MSG_ERROR_PROCESS_FETCH, legacyId);
  }

  public static String formatProcessInstanceCreateError(String legacyId) {
    return String.format(MSG_ERROR_PROCESS_CREATE, legacyId);
  }

  public static String getMigratorJobsFetchError() {
    return MSG_ERROR_JOBS_FETCH;
  }

  public static String formatLegacyIdFetchError(long processInstanceKey) {
    return String.format(MSG_ERROR_LEGACY_ID_FETCH, processInstanceKey);
  }

  public static String formatActivityFetchError(String legacyId) {
    return String.format(MSG_ERROR_ACTIVITY_FETCH, legacyId);
  }

  public static String getJobsActivationError() {
    return MSG_ERROR_JOBS_ACTIVATION;
  }

  public static String formatNoC8ProcessError(String c8DefinitionId, String legacyProcessInstanceId) {
    return String.format(MSG_ERROR_NO_C8_PROCESS, c8DefinitionId, legacyProcessInstanceId);
  }

  public static String formatMultiInstanceError(String elementName, String processInstanceId) {
    return String.format(MSG_ERROR_MULTI_INSTANCE, elementName, processInstanceId);
  }

  public static String formatElementNotInC8Error(String legacyProcessInstanceId,
                                                 String c8DefinitionId,
                                                 String activityId) {
    return String.format(MSG_ERROR_ELEMENT_NOT_IN_C8, legacyProcessInstanceId, c8DefinitionId, activityId);
  }

  public static void infoFetchingProcessInstances() {
    if (log.isInfoEnabled()) {
      log.info(MSG_INFO_FETCHING);
    }
  }

  public static void debugFetchingLatestStartDate() {
    if (log.isDebugEnabled()) {
      log.debug("Fetching latest start date of process instances");
    }
  }

  public static void debugLatestStartDate(Object startDate) {
    if (log.isDebugEnabled()) {
      log.debug(MSG_DEBUG_LATEST_START_DATE, startDate);
    }
  }

  public static void infoSkippingProcessInstance(String legacyProcessInstanceId) {
    if (log.isInfoEnabled()) {
      log.info(MSG_INFO_SKIPPING, legacyProcessInstanceId);
    }
  }

  public static void warnProcessInstanceCannotBeMigrated(String legacyProcessInstanceId, String reason) {
    if (log.isWarnEnabled()) {
      log.warn("Process instance [{}] can't be migrated: {}", legacyProcessInstanceId, reason);
    }
  }

  public static void debugStartingNewProcessInstance(String legacyProcessInstanceId) {
    if (log.isDebugEnabled()) {
      log.debug(MSG_DEBUG_STARTING, legacyProcessInstanceId);
    }
  }

  public static void debugStartedProcessInstance(Long processInstanceKey) {
    if (log.isDebugEnabled()) {
      log.debug(MSG_DEBUG_STARTED, processInstanceKey);
    }
  }

  public static void debugUpdatingKey(String legacyProcessInstanceId, Long processInstanceKey) {
    if (log.isDebugEnabled()) {
      log.debug(MSG_DEBUG_UPDATING, legacyProcessInstanceId, processInstanceKey);
    }
  }

  public static void debugInsertingRecord(Object record) {
    if (log.isDebugEnabled()) {
      log.debug(MSG_DEBUG_INSERTING, record);
    }
  }

  public static void debugValidatingProcessInstance(String legacyProcessInstanceId) {
    if (log.isDebugEnabled()) {
      log.debug(MSG_DEBUG_VALIDATING, legacyProcessInstanceId);
    }
  }

  public static void debugCollectingActivityInstances(String processInstanceId) {
    if (log.isDebugEnabled()) {
      log.debug(MSG_DEBUG_COLLECTING, processInstanceId);
    }
  }

  public static void debugFoundActivityInstances(int size) {
    if (log.isDebugEnabled()) {
      log.debug(MSG_DEBUG_FOUND_ACTIVITIES, size);
    }
  }

  public static void infoActivatingMigratorJobs() {
    if (log.isInfoEnabled()) {
      log.info(MSG_INFO_ACTIVATING);
    }
  }

  public static void debugMigratorJobsFound(int size) {
    if (log.isDebugEnabled()) {
      log.debug(MSG_DEBUG_JOBS_FOUND, size);
    }
  }

  public static void warnProcessInstanceNoLongerExists(String legacyProcessInstanceId) {
    if (log.isWarnEnabled()) {
      log.warn(MSG_WARN_INSTANCE_GONE, legacyProcessInstanceId);
    }
  }
}
