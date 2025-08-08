/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.impl.persistence;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.camunda.migrator.impl.persistence.IdKeyMapper.TYPE;
import java.util.Date;
import java.util.Objects;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class IdKeyDbModel {

  protected Long instanceKey;
  protected String id;
  protected TYPE type;
  protected Date startDate;
  protected String skipReason;

  public IdKeyDbModel() {
  }

  public IdKeyDbModel(String id, Date startDate) {
    this.id = id;
    this.startDate = startDate;
  }

  public Long instanceKey() {
    return instanceKey;
  }

  public String id() {
    return id;
  }

  public TYPE type() {
    return type;
  }

  public Date startDate() {
    return startDate;
  }

  public String skipReason() {
    return skipReason;
  }

  public void setInstanceKey(Long instanceKey) {
    this.instanceKey = instanceKey;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setType(TYPE type) {
    this.type = type;
  }

  public void setStartDate(Date startDate) {
    this.startDate = startDate;
  }

  public void setSkipReason(String skipReason) {
    this.skipReason = skipReason;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this)
      return true;
    if (obj == null || obj.getClass() != this.getClass())
      return false;
    var that = (IdKeyDbModel) obj;
    return Objects.equals(this.instanceKey, that.instanceKey) && Objects.equals(this.id, that.id) && Objects.equals(this.type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(instanceKey, id, type);
  }

  @Override
  public String toString() {
    return "IdKey[" + "instanceKey=" + instanceKey + ", " + "id=" + id + ", " + "type=" + type + ']';
  }

}
