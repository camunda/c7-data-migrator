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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.query.Query;
import org.camunda.bpm.engine.variable.type.ValueType;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.camunda.spin.plugin.variable.type.SpinValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

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


  /**
   * Heads-up: this implementation needs to be null safe for the variable value.
   * Using streams might lead to undesired {@link NullPointerException}s.
   */
  public Map<String, Object> toVariableMap() {
    Map<String, Object> result = new HashMap<>();
    List<VariableInterceptor> interceptors = (List<VariableInterceptor>) context.getBeansOfType(
        VariableInterceptor.class).values().stream().toList();
    toList().forEach(e -> {
      VariableInvocation variableInvocation = new VariableInvocation((VariableInstanceEntity) e);
      if (interceptors != null && interceptors.size() > 0) {
        interceptors.stream().forEach(i -> {
          try {
            i.execute(variableInvocation);
          } catch (Exception ex) {
            throw new MigratorException("An error occurred during variable transformation.", ex);
          }
        });
      }

      var variable = variableInvocation.getVariable();
      TypedValue typedValue = variable.getTypedValue(false);
      if (typedValue.getType().equals(ValueType.OBJECT)) {
        // skip the value deserialization
        result.put(variable.getName(), typedValue.getValue());
      } else if (typedValue.getType().equals(SpinValueType.JSON) || typedValue.getType().equals(SpinValueType.XML)) {
        // For Spin JSON/XML, explicitly set the string value
        result.put(variable.getName(), typedValue.getValue().toString());
      } else {
        result.put(variable.getName(), variable.getValue());
      }
    });
    return result;
  }
}
