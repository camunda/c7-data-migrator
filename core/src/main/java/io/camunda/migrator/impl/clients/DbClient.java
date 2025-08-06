/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.clients;

import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_CHECK_EXISTENCE;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_CHECK_KEY;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_DELETE;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_ALL;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_ALL_SKIPPED;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_KEY_BY_ID;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_LATEST_ID;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_LATEST_START_DATE;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_FIND_SKIPPED_COUNT;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_INSERT_RECORD;
import static io.camunda.migrator.impl.logging.DbClientLogs.FAILED_TO_UPDATE_KEY;
import static io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE;
import static io.camunda.migrator.impl.util.ExceptionUtils.callApi;

import io.camunda.migrator.config.property.MigratorProperties;
import io.camunda.migrator.impl.Pagination;
import io.camunda.migrator.impl.logging.DbClientLogs;
import io.camunda.migrator.impl.persistence.IdKeyDbModel;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import io.camunda.migrator.impl.util.PrintUtils;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Wrapper class for IdKeyMapper database operations with exception handling.
 * Maintains the same exception wrapping behavior as ExceptionUtils.callApi.
 */
@Component
public class DbClient {

  @Autowired
  protected MigratorProperties properties;

  @Autowired
  protected IdKeyMapper idKeyMapper;

  /**
   * Checks if a process instance exists in the mapping table.
   */
  public boolean checkExists(String legacyEntityId) {
    return callApi(() -> idKeyMapper.checkExists(legacyEntityId), FAILED_TO_CHECK_EXISTENCE + legacyEntityId);
  }

  /**
   * Checks if a process instance exists in the mapping table.
   */
  public boolean checkHasKey(String legacyId) {
    return callApi(() -> idKeyMapper.checkHasKey(legacyId), FAILED_TO_CHECK_KEY + legacyId);
  }

  /**
   * Finds the latest start date by type.
   */
  public Date findLatestStartDateByType(TYPE type) {
    Date latestStartDate = callApi(() -> idKeyMapper.findLatestStartDateByType(type),
        FAILED_TO_FIND_LATEST_START_DATE + type);
    DbClientLogs.foundLatestStartDate(latestStartDate, type);
    return latestStartDate;
  }

  /**
   * Finds the latest legacy ID by type.
   */
  public String findLatestIdByType(TYPE type) {
    return callApi(() -> idKeyMapper.findLatestIdByType(type), FAILED_TO_FIND_LATEST_ID + type);
  }

  /**
   * Finds the key by legacy ID.
   */
  public Long findKeyById(String legacyId) {
    return callApi(() -> idKeyMapper.findKeyById(legacyId), FAILED_TO_FIND_KEY_BY_ID + legacyId);
  }

  /**
   * Finds all legacy IDs.
   */
  public List<String> findAllIds() {
    return callApi(() -> idKeyMapper.findAllIds(), FAILED_TO_FIND_ALL);
  }

  /**
   * Updates a record by setting the key for an existing ID.
   */
  public void updateKeyById(String legacyId, Long entityKey, TYPE type) {
    DbClientLogs.updatingKeyForLegacyId(legacyId, entityKey);
    var model = createIdKeyDbModel(legacyId, null, entityKey, type);
    callApi(() -> idKeyMapper.updateKeyById(model), FAILED_TO_UPDATE_KEY + entityKey);
  }

  /**
   * Updates a record by setting the key for an existing ID.
   */
  public void updateKeyById(String legacyId, Date startDate, Long entityKey, TYPE type) {
    DbClientLogs.updatingKeyForLegacyId(legacyId, entityKey);
    var model = createIdKeyDbModel(legacyId, startDate, entityKey, type);
    callApi(() -> idKeyMapper.updateKeyById(model), FAILED_TO_UPDATE_KEY + entityKey);
  }

  /**
   * Inserts a new process instance record into the mapping table.
   */
  public void insert(String legacyId, Date startDate, Long entityKey, TYPE type) {
    DbClientLogs.insertingRecord(legacyId, startDate, entityKey);
    var model = createIdKeyDbModel(legacyId, startDate, entityKey, type);
    callApi(() -> idKeyMapper.insert(model), FAILED_TO_INSERT_RECORD + legacyId);
  }

  /**
   * Inserts a new record into the mapping table.
   */
  public void insert(String legacyId, Long key, TYPE type) {
    DbClientLogs.insertingRecord(legacyId, null, key);
    var model = createIdKeyDbModel(legacyId, null, key, type);
    callApi(() -> idKeyMapper.insert(model), FAILED_TO_INSERT_RECORD + legacyId);
  }

  /**
   * Lists skipped entities by type with pagination and prints them.
   */
  public void listSkippedEntitiesByType(TYPE type) {
    new Pagination<String>().pageSize(properties.getPageSize())
        .maxCount(() -> idKeyMapper.countSkippedByType(type))
        .page(offset -> idKeyMapper.findSkippedByType(type, offset, properties.getPageSize())
            .stream()
            .map(IdKeyDbModel::id)
            .collect(Collectors.toList()))
        .callback(PrintUtils::print);
  }

  /**
   * Processes skipped entities with pagination.
   */
  public void fetchAndHandleSkipped(TYPE type, Consumer<IdKeyDbModel> callback) {
    new Pagination<IdKeyDbModel>().pageSize(properties.getPageSize())
        .maxCount(() -> idKeyMapper.countSkippedByType(type))
        // Hardcode offset to 0 since each callback updates the database and leads to fresh results.
        .page(offset -> idKeyMapper.findSkippedByType(type, 0, properties.getPageSize()))
        .callback(callback);
  }

  /**
   * Finds the count of skipped entities for the given type
   */
  public Long countSkippedByType(TYPE type) {
    return callApi(() -> idKeyMapper.countSkippedByType(type), FAILED_TO_FIND_SKIPPED_COUNT);
  }

  /**
   * Finds the Ids of all skipped process instances.
   */
  public List<IdKeyDbModel> findSkippedProcessInstances() {
    return callApi(() -> idKeyMapper.findSkippedByType(TYPE.RUNTIME_PROCESS_INSTANCE, 0, properties.getPageSize()),
        FAILED_TO_FIND_ALL_SKIPPED);
  }

  /**
   * Deletes all mappings from the database.
   */
  public void deleteAllMappings() {
    findAllIds().forEach(this::delete);
  }

  /**
   * Deletes a mapping by legacy ID.
   */
  protected void delete(String legacyId) {
    callApi(() -> idKeyMapper.delete(legacyId), FAILED_TO_DELETE + legacyId);
  }

  /**
   * Creates a new IdKeyDbModel instance with the provided parameters.
   */
  protected IdKeyDbModel createIdKeyDbModel(String id, Date startDate, Long key, TYPE type) {
    var keyIdDbModel = new IdKeyDbModel();
    keyIdDbModel.setId(id);
    keyIdDbModel.setStartDate(startDate);
    keyIdDbModel.setInstanceKey(key);
    keyIdDbModel.setType(type);
    return keyIdDbModel;
  }

}
