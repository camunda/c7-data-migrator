/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.history;

import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface IdKeyMapper {

  String findLatestIdByType(String type);

  Long findKeyById(String id);

  void insert(IdKeyDbModel idKeyDbModel);

  List<String> findNonProcessInstanceIds(@Param("limit") int limit, @Param("offset") int offset);

  List<String> findAllNonMigratedProcessInstanceIds();

  List<String> findAllProcessInstanceIds();

  void updateKeyById(IdKeyDbModel idKeyDbModel);

  void delete(String id);

}
