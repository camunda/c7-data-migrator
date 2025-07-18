/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl;

import static io.camunda.migrator.impl.util.ExceptionUtils.callApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.camunda.bpm.engine.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pagination<T> {

  protected static final Logger LOGGER = LoggerFactory.getLogger(Pagination.class);

  protected int pageSize;
  protected Supplier<Long> maxCount;
  protected Function<Integer, List<T>> page;
  protected Query<?, T> query;


  public Pagination<T> pageSize(int pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  public Pagination<T> maxCount(Supplier<Long> maxCount) {
    this.maxCount = maxCount;
    return this;
  }

  public Pagination<T> page(Function<Integer, List<T>> page) {
    this.page = page;
    return this;
  }

  public Pagination<T> query(Query<?, T> query) {
    this.query = query;
    return this;
  }

  public void callback(Consumer<T> callback) {
    Long maxCount = null;
    Function<Integer, List<T>> result;

    if (query != null) {
      maxCount = query.count();
      result = offset -> query.listPage(offset, pageSize);

    } else if (page != null) {
      maxCount = callApi(this.maxCount);
      result = (offset) -> page.apply(offset).stream().toList();

    } else {
      throw new IllegalStateException("Query and page cannot be null");
    }

    for (int i = 0; i < maxCount; i = i + pageSize) {
      StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      int offset = i;
      String methodName = stackTrace[2].getMethodName();
      LOGGER.debug("Method: #{}, max count: {}, offset: {}, page size: {}", methodName, maxCount, offset, pageSize);

      callApi(() -> result.apply(offset)).forEach(callback);
    }
  }

  public List<T> toList() {
    List<T> list = new ArrayList<>();
    callback(list::add);
    return list;
  }

}
