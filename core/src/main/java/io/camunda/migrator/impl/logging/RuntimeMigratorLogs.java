/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.logging;

import io.camunda.migrator.RuntimeMigrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs debug messages for RuntimeMigrator operations
 */
public class RuntimeMigratorLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(RuntimeMigrator.class);

  // RuntimeMigrator Messages
  public static final String STARTING_NEW_C8_PROCESS_INSTANCE = "Starting new C8 process instance with legacyId: [{}]";
  public static final String STARTED_C8_PROCESS_INSTANCE = "Started C8 process instance with processInstanceKey: [{}]";
  public static final String SKIPPING_PROCESS_INSTANCE_VARIABLE_ERROR = "Skipping process instance with legacyId: {}; due to: {} Enable DEBUG level to print the stacktrace.";
  public static final String STACKTRACE = "Stacktrace:";
  public static final String SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR = "Skipping process instance with legacyId [{}]: {}";
  public static final String FETCHING_PROCESS_INSTANCES = "Fetching process instances to migrate";
  public static final String FETCHING_LATEST_START_DATE = "Fetching latest start date of process instances";
  public static final String LATEST_START_DATE = "Latest start date: {}";
  public static final String PROCESS_INSTANCE_NOT_EXISTS = "Process instance with legacyId {} doesn't exist anymore. Has it been completed or cancelled in the meantime?";
  public static final String ACTIVATING_MIGRATOR_JOBS = "Activating migrator jobs";
  public static final String MIGRATOR_JOBS_FOUND = "Migrator jobs found: {}";
  public static final String COLLECTING_ACTIVE_DESCENDANT_ACTIVITIES = "Collecting active descendant activity instances for activityId [{}]";
  public static final String FOUND_ACTIVE_ACTIVITIES_TO_ACTIVATE = "Found {} active activity instances to activate";
  public static final String EXTERNALLY_STARTED_PROCESS_INSTANCE = "Process instance with key [{}] was externally started, skipping migrator job activation.";

  // RuntimeMigrator Error Messages
  public static final String PROCESS_INSTANCE_FETCHING_FAILED = "Process instance fetching failed for legacyId: ";
  public static final String CREATING_PROCESS_INSTANCE_FAILED = "Creating process instance failed for bpmnProcessId: ";
  public static final String FAILED_TO_FETCH_ACTIVITY_INSTANCE = "Failed to fetch activity instance for processInstanceId: ";
  public static final String PROCESS_DEFINITION_SEARCH_FAILED = "Process definition search failed for processDefinitionId: ";
  public static final String FAILED_TO_FETCH_PROCESS_DEFINITION_XML = "Failed to fetch process definition XML for key: ";
  public static final String FAILED_TO_ACTIVATE_JOBS = "Failed to activate jobs for type: ";
  public static final String FAILED_TO_FETCH_VARIABLE = "Failed to fetch variable '%s' from job: %s";
  public static final String FAILED_TO_MODIFY_PROCESS_INSTANCE = "Failed to modify process instance with activation for key: ";

  public static void startingNewC8ProcessInstance(String legacyProcessInstanceId) {
    LOGGER.debug(STARTING_NEW_C8_PROCESS_INSTANCE, legacyProcessInstanceId);
  }

  public static void startedC8ProcessInstance(Long processInstanceKey) {
    LOGGER.debug(STARTED_C8_PROCESS_INSTANCE, processInstanceKey);
  }

  public static void skippingProcessInstanceVariableError(String legacyProcessInstanceId, String message) {
    LOGGER.info(SKIPPING_PROCESS_INSTANCE_VARIABLE_ERROR, legacyProcessInstanceId, message);
  }

  public static void stacktrace(Exception e) {
    LOGGER.debug(STACKTRACE, e);
  }

  public static void skippingProcessInstanceValidationError(String legacyProcessInstanceId, String message) {
    LOGGER.warn(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, legacyProcessInstanceId, message);
  }

  public static void fetchingProcessInstances() {
    LOGGER.info(FETCHING_PROCESS_INSTANCES);
  }

  public static void fetchingLatestStartDate() {
    LOGGER.debug(FETCHING_LATEST_START_DATE);
  }

  public static void latestStartDate(Object startDate) {
    LOGGER.debug(LATEST_START_DATE, startDate);
  }

  public static void processInstanceNotExists(String legacyProcessInstanceId) {
    LOGGER.warn(PROCESS_INSTANCE_NOT_EXISTS, legacyProcessInstanceId);
  }

  public static void activatingMigratorJobs() {
    LOGGER.info(ACTIVATING_MIGRATOR_JOBS);
  }

  public static void migratorJobsFound(int size) {
    LOGGER.debug(MIGRATOR_JOBS_FOUND, size);
  }

  public static void collectingActiveDescendantActivities(String activityId) {
    LOGGER.debug(COLLECTING_ACTIVE_DESCENDANT_ACTIVITIES, activityId);
  }

  public static void foundActiveActivitiesToActivate(int size) {
    LOGGER.debug(FOUND_ACTIVE_ACTIVITIES_TO_ACTIVATE, size);
  }

  public static void externallyStartedProcessInstance(long processInstanceKey) {
    LOGGER.info(EXTERNALLY_STARTED_PROCESS_INSTANCE, processInstanceKey);
  }
}
