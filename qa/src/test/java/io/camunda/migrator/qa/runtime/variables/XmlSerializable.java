/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.runtime.variables;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class XmlSerializable {
  private String stringProperty;

  private int intProperty;

  private boolean booleanProperty;

  public XmlSerializable() {

  }

  public XmlSerializable(String stringProperty, int intProperty, boolean booleanProperty) {
    this.stringProperty = stringProperty;
    this.intProperty = intProperty;
    this.booleanProperty = booleanProperty;
  }

  public String getStringProperty() {
    return stringProperty;
  }

  public void setStringProperty(String stringProperty) {
    this.stringProperty = stringProperty;
  }

  public int getIntProperty() {
    return intProperty;
  }

  public void setIntProperty(int intProperty) {
    this.intProperty = intProperty;
  }

  public boolean getBooleanProperty() {
    return booleanProperty;
  }

  public void setBooleanProperty(boolean booleanProperty) {
    this.booleanProperty = booleanProperty;
  }

  public String toExpectedXmlString() {
    StringBuilder jsonBuilder = new StringBuilder();

    jsonBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><xmlSerializable><booleanProperty>");
    jsonBuilder.append(booleanProperty);
    jsonBuilder.append("</booleanProperty><intProperty>");
    jsonBuilder.append(intProperty);
    jsonBuilder.append("</intProperty><stringProperty>");
    jsonBuilder.append(stringProperty);
    jsonBuilder.append("</stringProperty></xmlSerializable>");

    return jsonBuilder.toString();
  }

  public String toString() {
    return toExpectedXmlString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (booleanProperty ? 1231 : 1237);
    result = prime * result + intProperty;
    result = prime * result + ((stringProperty == null) ? 0 : stringProperty.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    XmlSerializable other = (XmlSerializable) obj;
    if (booleanProperty != other.booleanProperty)
      return false;
    if (intProperty != other.intProperty)
      return false;
    if (stringProperty == null) {
      if (other.stringProperty != null)
        return false;
    } else if (!stringProperty.equals(other.stringProperty))
      return false;
    return true;
  }
}
