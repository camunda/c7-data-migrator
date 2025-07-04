/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.interceptor;

/**
 * Interceptor interface for handling variable invocations.
 * <p>
 * Implement this interface to define custom logic that should be executed
 * when a variable is accessed or modified during migration.
 * Add <code>@Component</code> annotation to register your
 * interceptor.
 * </p>
 *
 * <h2>Usage Example:</h2>
 * <pre>
 * &#64;Component
 * public class MyVariableInterceptor implements VariableInterceptor {
 *   &#64;Override
 *   public void execute(VariableInvocation invocation) throws Exception {
 *     // Custom logic before or after variable access
 *     Object value = invocation.getC7Variable().getValue();
 *     // Modify or log the value as needed
 *     invocation.setVariableValue(modifiedValue);
 *   }
 * }
 * </pre>
 */
public interface VariableInterceptor {

  void execute(VariableInvocation invocation) throws Exception;
}
