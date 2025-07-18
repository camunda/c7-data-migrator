/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.logging;

import io.camunda.migrator.impl.RuntimeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs debug messages for RuntimeValidator operations
 */
public class RuntimeValidatorLogs {

  protected static final Logger LOGGER = LoggerFactory.getLogger(RuntimeValidator.class);

  // RuntimeValidator Messages
  public static final String VALIDATE_LEGACY_PROCESS_INSTANCE = "Validate legacy process instance by ID: {}";
  public static final String COLLECTING_ACTIVE_DESCENDANT_ACTIVITIES_VALIDATION = "Collecting active descendant activity instances for legacyId [{}]";
  public static final String FOUND_ACTIVE_ACTIVITIES_TO_VALIDATE = "Found {} active activity instances to validate";
  public static final String JOB_TYPE_VALIDATION_DISABLED = "Job type validation is disabled, skipping execution listener validation";

  // RuntimeValidator Error Messages
  public static final String MULTI_INSTANCE_LOOP_CHARACTERISTICS_ERROR = "Found multi-instance loop characteristics for flow node with id [%s] in C7 process instance.";
  public static final String NO_NONE_START_EVENT_ERROR = "Couldn't find process None Start Event in C8 process with key [%s].";
  public static final String NO_EXECUTION_LISTENER_OF_TYPE_ERROR = "No execution listener of type '%s' found on start event [%s] in C8 process with id [%s]. At least one '%s' listener is required.";
  public static final String FLOW_NODE_NOT_EXISTS_ERROR = "Flow node with id [%s] doesn't exist in the equivalent deployed C8 model.";
  public static final String NO_C8_DEPLOYMENT_ERROR = "No C8 deployment found for process ID [%s] required for instance with legacyID [%s].";
  public static final String FAILED_TO_PARSE_BPMN_MODEL = "Failed to parse BPMN model from XML";

  public static void validateLegacyProcessInstance(String legacyProcessInstanceId) {
    LOGGER.debug(VALIDATE_LEGACY_PROCESS_INSTANCE, legacyProcessInstanceId);
  }

  public static void collectingActiveDescendantActivitiesValidation(String processInstanceId) {
    LOGGER.debug(COLLECTING_ACTIVE_DESCENDANT_ACTIVITIES_VALIDATION, processInstanceId);
  }

  public static void foundActiveActivitiesToValidate(int size) {
    LOGGER.debug(FOUND_ACTIVE_ACTIVITIES_TO_VALIDATE, size);
  }

  public static void jobTypeValidationDisabled() {
    LOGGER.debug(JOB_TYPE_VALIDATION_DISABLED);
  }
}
