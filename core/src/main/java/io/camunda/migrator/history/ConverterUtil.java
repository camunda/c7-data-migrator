/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.history;

import io.camunda.zeebe.protocol.Protocol;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import static io.camunda.zeebe.protocol.Protocol.KEY_BITS;

public class ConverterUtil {

  /**
   * Partition ID used for history data migration from Camunda 7 to Camunda 8.
   * Set to 4095 (maximum possible partition value) to ensure generated keys don't
   * collide with actual Zeebe partition keys during migration.
   */
  public static int C7_HISTORY_PARTITION_ID = 4095;

  public static Long getNextKey() {
    SecureRandom secureRandom = new SecureRandom();
    return Protocol.encodePartitionId(C7_HISTORY_PARTITION_ID, secureRandom.nextLong(getUpperBound() + 1));
  }

  protected static long getUpperBound() {
    return (1L << KEY_BITS) - 1;
  }

  public static OffsetDateTime convertDate(Date date) {
    if (date == null) return null;
    return date.toInstant().atOffset(ZoneOffset.UTC);
  }

}
