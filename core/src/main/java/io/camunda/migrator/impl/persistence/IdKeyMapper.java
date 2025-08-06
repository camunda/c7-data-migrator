/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.persistence;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface IdKeyMapper {

  enum TYPE {
    HISTORY_PROCESS_DEFINITION("historic process definition"),
    HISTORY_PROCESS_INSTANCE("historic process instance"),
    HISTORY_INCIDENT("historic incident"),
    HISTORY_VARIABLE("historic variable"),
    HISTORY_USER_TASK("historic user task"),
    HISTORY_FLOW_NODE("historic flow node"),
    HISTORY_DECISION_INSTANCE("historic decision instance"),
    HISTORY_DECISION_DEFINITION("historic decision definition"),

    RUNTIME_PROCESS_INSTANCE("process instance");

    private final String displayName;

    TYPE(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }

  String findLatestIdByType(TYPE type);

  boolean checkExists(String id);

  boolean checkHasKey(String id);

  Date findLatestStartDateByType(TYPE type);

  Long findKeyById(String id);

  void insert(IdKeyDbModel idKeyDbModel);

  List<IdKeyDbModel> findSkippedByType(@Param("type") TYPE type, @Param("offset") int offset, @Param("limit") int limit);

  long countSkippedByType(@Param("type") TYPE type);

  List<String> findAllIds();

  void updateKeyById(IdKeyDbModel idKeyDbModel);

  void delete(String id);
}