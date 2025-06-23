/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.util;

import io.camunda.zeebe.protocol.Protocol;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import static io.camunda.zeebe.protocol.Protocol.KEY_BITS;

public class ConverterUtil {

  /* Create data on a partition that doesn't collide with Zeebe */
  public static int C7_HISTORY_PARTITION_ID = 99;

  public static Long getNextKey() {
    SecureRandom secureRandom = new SecureRandom();
    return Protocol.encodePartitionId(C7_HISTORY_PARTITION_ID, secureRandom.nextLong(getUpperBound()));
  }

  /**
   * TODO: is this upper bound calculated correctly? A long has 64 bits - 51 bits for Zeebe keys without partition
   */
  protected static long getUpperBound() {
    return Long.MAX_VALUE >> (64-KEY_BITS);
  }

  public static OffsetDateTime convertDate(Date date) {
    if (date == null) return null;
    return date.toInstant().atOffset(ZoneOffset.UTC);
  }

}
