/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import static io.camunda.migrator.ExceptionUtils.callApi;

import io.camunda.migrator.interceptor.VariableInterceptor;
import io.camunda.migrator.interceptor.VariableInvocation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

public class Pagination<T> {

  protected static final Logger LOGGER = LoggerFactory.getLogger(Pagination.class);

  protected int batchSize;
  protected Supplier<Long> maxCount;
  protected Function<Integer, List<T>> page;
  private Query<?, T> query;
  private ApplicationContext context;


  public Pagination<T> batchSize(int batchSize) {
    this.batchSize = batchSize;
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

  public Pagination<T> context(ApplicationContext context) {
    this.context = context;
    return this;
  }

  public void callback(Consumer<T> callback) {
    Long maxCount = null;
    Function<Integer, List<T>> result;

    if (query != null) {
      maxCount = query.count();
      result = offset -> query.listPage(offset, batchSize);

    } else if (page != null) {
      maxCount = callApi(this.maxCount);
      result = (offset) -> page.apply(offset).stream().toList();

    } else {
      throw new IllegalStateException("Query and page cannot be null");
    }

    for (int i = 0; i < maxCount; i = i + batchSize) {
      StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      int offset = i;
      String methodName = stackTrace[2].getMethodName();
      LOGGER.debug("Method: #{}, max count: {}, offset: {}, batch size: {}", methodName, maxCount, offset, batchSize);

      callApi(() -> result.apply(offset)).forEach(callback);
    }
  }

  public List<T> toList() {
    List<T> list = new ArrayList<>();
    callback(list::add);
    return list;
  }

  public Map<String, Map<String, Object>> toVariableMapAll() {
    Map<String, Map<String, Object>> result = new HashMap<>();
    processVariables((variable, variableInvocation) -> {
      Map<String, Object> variableResult = new HashMap<>();
      variableResult.put(variableInvocation.getMigrationVariable().getName(), variableInvocation.getMigrationVariable().getValue());
      String activityInstanceId = variable.getActivityInstanceId();
      Map<String, Object> variableMap = result.getOrDefault(activityInstanceId, new HashMap<>());
      variableMap.putAll(variableResult);
      result.put(activityInstanceId, variableMap);
    });
    return result;
  }


  public Map<String, Object> toVariableMapSingleActivity() {
    Map<String, Object> variableResult = new HashMap<>();
    processVariables((variable, variableInvocation) -> {
      variableResult.put(variableInvocation.getMigrationVariable().getName(), variableInvocation.getMigrationVariable().getValue());
    });
    return variableResult;
  }

  /**
   * Heads-up: this implementation needs to be null safe for the variable value.
   * Using streams might lead to undesired {@link NullPointerException}s.
   */
  protected void processVariables(BiConsumer<VariableInstanceEntity, VariableInvocation> consumer) {
    List<VariableInterceptor> interceptors = getVariableInterceptorsOrdered();
    toList().forEach(e -> {
      var variable = (VariableInstanceEntity) e;
      VariableInvocation variableInvocation = new VariableInvocation((VariableInstanceEntity) e);
      if (interceptors != null && !interceptors.isEmpty()) {
        interceptors.forEach(i -> {
          try {
            i.execute(variableInvocation);
          } catch (Exception ex) {
            throw new VariableInterceptorException("An error occurred during variable transformation.", ex);
          }
        });
      }
      consumer.accept(variable, variableInvocation);
    });
  }

  protected List<VariableInterceptor> getVariableInterceptorsOrdered() {
    List<VariableInterceptor> interceptors = new ArrayList<>(
        context.getBeansOfType(VariableInterceptor.class).values());
    AnnotationAwareOrderComparator.sort(interceptors);
    return interceptors;
  }

}
