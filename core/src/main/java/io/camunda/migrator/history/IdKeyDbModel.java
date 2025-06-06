/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.history;

import java.util.Objects;

public class IdKeyDbModel {

  protected Long itemKey;
  protected String id;
  protected IdKeyMapper.TYPE type;

  public Long itemKey() {
    return itemKey;
  }

  public String id() {
    return id;
  }

  public IdKeyMapper.TYPE type() {
    return type;
  }

  public void setItemKey(Long itemKey) {
    this.itemKey = itemKey;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setType(IdKeyMapper.TYPE type) {
    this.type = type;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this)
      return true;
    if (obj == null || obj.getClass() != this.getClass())
      return false;
    var that = (IdKeyDbModel) obj;
    return Objects.equals(this.itemKey, that.itemKey) && Objects.equals(this.id, that.id) && Objects.equals(this.type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(itemKey, id, type);
  }

  @Override
  public String toString() {
    return "IdKey[" + "itemKey=" + itemKey + ", " + "id=" + id + ", " + "type=" + type + ']';
  }

}
