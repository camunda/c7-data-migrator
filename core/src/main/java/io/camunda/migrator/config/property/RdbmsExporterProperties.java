/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(RdbmsExporterProperties.PREFIX)
public class RdbmsExporterProperties {

  public static final String PREFIX = "rdbms-exporter";

  public Boolean getAutoDdl() {
    return autoDdl;
  }

  public void setAutoDdl(Boolean autoDdl) {
    this.autoDdl = autoDdl;
  }

  protected Boolean autoDdl;


}
