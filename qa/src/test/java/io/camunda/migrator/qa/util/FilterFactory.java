/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.util;

import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableFilter;

public class FilterFactory {

  public static ProcessDefinitionFilter.Builder procDefFilter() {
    return new ProcessDefinitionFilter.Builder();
  }

  public static ProcessInstanceFilter.Builder procInstFilter() {
    return new ProcessInstanceFilter.Builder();
  }

  public static UserTaskFilter.Builder userTasksFilter() {
    return new UserTaskFilter.Builder();
  }

  public static FlowNodeInstanceFilter.Builder flowNodesFilter() {
    return new FlowNodeInstanceFilter.Builder();
  }

  public static IncidentFilter.Builder incFilter() {
    return new IncidentFilter.Builder();
  }

  public static VariableFilter.Builder varFilter() {
    return new VariableFilter.Builder();
  }
}