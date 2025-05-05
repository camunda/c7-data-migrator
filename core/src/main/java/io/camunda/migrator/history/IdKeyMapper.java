/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.history;

import java.util.List;

public interface IdKeyMapper {

  String findLatestIdByType(String type);

  Long findKeyById(String id);

  void insert(IdKeyDbModel idKeyDbModel);

  List<String> findProcessInstanceIds(int limit, int offset);

  void updateKeyById(IdKeyDbModel idKeyDbModel);

}
