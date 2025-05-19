/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.migrator.history.IdKeyDbModel;
import io.camunda.migrator.history.IdKeyMapper;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.impl.ProcessInstanceQueryImpl;
import org.camunda.bpm.engine.impl.persistence.entity.ActivityInstanceImpl;
import org.camunda.bpm.engine.impl.persistence.entity.TransitionInstanceImpl;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import static io.camunda.migrator.HistoryMigrator.BATCH_SIZE;

@Component
public class RuntimeMigrator {

  @Autowired
  protected RuntimeService runtimeService;

  @Autowired
  private IdKeyMapper idKeyMapper;

  @Autowired
  protected CamundaClient camundaClient;

  private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeMigrator.class);

  protected boolean autoDeployment = true;

  public void migrate() {
    // TODO: remove deploying resources automatically: we expect them to be already deployed in C8.
    if (autoDeployment) {
      LOGGER.debug("Starting auto deployment");
      // Deploy process
      var deployResource = camundaClient.newDeployResourceCommand();

      List<Path> models = findResourceFilesWithExtension("bpmn"); // TODO: forms, decisions

      DeployResourceCommandStep2 deployResourceCommandStep2 = null;
      for (Path model : models) {
        deployResourceCommandStep2 = deployResource.addResourceFile(model.toFile().getPath());
      }

      if (deployResourceCommandStep2 != null) {
        deployResourceCommandStep2.send().join();
      }
      LOGGER.debug("Completed auto deployment");
    }

    String latestLegacyId = idKeyMapper.findLatestIdByType("runtimeProcessInstance");
    LOGGER.debug("Legacy ID of latest migrated process instance: [{}]", latestLegacyId);
    ProcessInstanceQuery processInstanceQuery = ((ProcessInstanceQueryImpl) runtimeService.createProcessInstanceQuery())
        .idAfter(latestLegacyId)
        .rootProcessInstances();

    long maxLegacyProcessInstanceCount = processInstanceQuery.count();
    LOGGER.debug("{} process instances are scheduled for migration", maxLegacyProcessInstanceCount);

    for (int i = 0; i < maxLegacyProcessInstanceCount; i = i + BATCH_SIZE - 1) {
      processInstanceQuery.listPage(i, BATCH_SIZE).forEach(legacyProcessInstance -> {
        String legacyId = legacyProcessInstance.getId();
        var idKeyDbModel = new IdKeyDbModel();
        idKeyDbModel.setId(legacyId);
        idKeyDbModel.setKey(null);
        idKeyDbModel.setType("runtimeProcessInstance");
        idKeyMapper.insert(idKeyDbModel);
      });
    }

    // TODO: paginate this
    idKeyMapper.findProcessInstanceIds().forEach(legacyProcessInstanceId -> {
      LOGGER.info("Migrating process instance with legacyId [{}]", legacyProcessInstanceId);
      Map<String, Object> globalVariables = new HashMap<>();

      try {
        runtimeService.createVariableInstanceQuery()
            .activityInstanceIdIn(legacyProcessInstanceId)
            .list()
            .forEach(variable -> globalVariables.put(variable.getName(), variable.getValue())); // Collectors#toMap cannot handle null values and throws NPE.
      } catch (Exception e) {
        LOGGER.error("Error while querying legacy global variables {}", e.getMessage());
        throw new RuntimeException(e);
      }

      globalVariables.put("legacyId", legacyProcessInstanceId);
      LOGGER.debug("Global variables for process  instance [{}] set to [{}]", legacyProcessInstanceId, globalVariables);
      String bpmnProcessId = runtimeService.createProcessInstanceQuery()
          .processInstanceId(legacyProcessInstanceId)
          .singleResult()
          .getProcessDefinitionKey();

      LOGGER.debug("Creating new process instance");
      long processInstanceKey = camundaClient.newCreateInstanceCommand()
          .bpmnProcessId(bpmnProcessId)
          .latestVersion()
          .variables(globalVariables) // process instance global variables
          .send()
          .join()
          .getProcessInstanceKey();

      LOGGER.info("Created new process instance with key {} for legacyProcessInstanceId {}", processInstanceKey,
          legacyProcessInstanceId);
      var keyIdDbModel = new IdKeyDbModel();
      keyIdDbModel.setId(legacyProcessInstanceId);
      keyIdDbModel.setKey(processInstanceKey);
      idKeyMapper.updateKeyById(keyIdDbModel);

      LOGGER.debug("Activating jobs");
      List<ActivatedJob> migratorJobs = null;
      do {
        try {
          migratorJobs = camundaClient.newActivateJobsCommand()
              .jobType("migrator")
              // TODO: review #maxJobsToActivate and #timeout
              .maxJobsToActivate(Integer.MAX_VALUE)
              .timeout(Duration.ofMinutes(1))
              .send()
              .join()
              .getJobs();
        } catch (Exception e) {
          LOGGER.error("Error while activating jobs {}", e.getMessage());
          throw new RuntimeException(e);
        }

        LOGGER.debug("Preparing to migrate {} jobs", migratorJobs.size());
        migratorJobs.forEach(activatedJob -> {

              String legacyId = (String) activatedJob.getVariable("legacyId");

              ModifyProcessInstanceCommandStep1 modifyProcessInstance = camundaClient.newModifyProcessInstanceCommand(
                      activatedJob.getProcessInstanceKey())
                  // Cancel start event instance where migrator job sits to avoid executing the activities twice.
                  .terminateElement(activatedJob.getElementInstanceKey()).and();

              ModifyProcessInstanceCommandStep3 modifyInstructions = null;
              ActivityInstance activityInstanceTree = runtimeService.getActivityInstance(legacyId);
              Map<String, ActInstance> activityInstanceMap = getActiveActivityIdsById(activityInstanceTree,
                  new HashMap<>());

              // TODO: remove experiment. We won't support multi-instance at all it in the MVP.
              removeMultiInstances(activityInstanceMap);

              for (String activityInstanceId : activityInstanceMap.keySet()) {
                ActInstance actInstance = activityInstanceMap.get(activityInstanceId);
                String activityId = actInstance.id().split("#multiInstanceBody")[0];

                Map<String, Object> localVariables = new HashMap<>();

                runtimeService.createVariableInstanceQuery()
                    .activityInstanceIdIn(activityInstanceId)
                    .list()
                    .forEach(variable -> localVariables.put(variable.getName(),
                        variable.getValue())); // Collectors#toMap cannot handle null values and throws NPE.

                String subProcessInstanceId = actInstance.subProcessInstanceId();
                if (subProcessInstanceId != null) {
                  localVariables.put("legacyId", subProcessInstanceId);
                }
                modifyInstructions = modifyProcessInstance.activateElement(activityId)
                    .withVariables(localVariables, activityId);
              }

              try {
                modifyInstructions.send().join();
                // no need to complete the job since the modification canceled the migrator job in the start event
              } catch (Exception e) {
                LOGGER.error("Error while modifying jobs {}", e.getMessage());
                throw new RuntimeException(e);
              }
            });
      } while (!migratorJobs.isEmpty());
      LOGGER.info("Migration completed for process instance with migrated key [{}] and legacyId [{}]",
          legacyProcessInstanceId, processInstanceKey);
    });
  }

  /**
   * This removes multi-instances from the active activity instance map and only keeps the #multiInstanceBody activity.
   * Like this, the C8 process model takes care of instantiating X instances.
   * TODO: how would this work for multi-instance call activities?
   */
  protected void removeMultiInstances(Map<String, ActInstance> activityInstanceMap) {
    Set<String> multiInstanceBodies = activityInstanceMap.values()
        .stream()
        .map(ActInstance::id)
        .filter(activityId -> activityId.endsWith("#multiInstanceBody"))
        .map(activityId -> activityId.substring(0, activityId.length() - "#multiInstanceBody".length()))
        .collect(Collectors.toSet());

    activityInstanceMap.entrySet().removeIf(entry -> multiInstanceBodies.contains(entry.getValue().id()));
  }

  public Map<String, ActInstance> getActiveActivityIdsById(ActivityInstance activityInstance, Map<String, ActInstance> activeActivities) {
    Arrays.asList(activityInstance.getChildActivityInstances()).forEach(actInst -> {
      activeActivities.putAll(getActiveActivityIdsById(actInst, activeActivities));

      if (!"subProcess".equals(actInst.getActivityType())) {
        // TODO skip migration of process instance when multi-instance is active
        activeActivities.put(actInst.getId(), new ActInstance(actInst.getActivityId(), ((ActivityInstanceImpl) actInst).getSubProcessInstanceId()));
      }
    });

    /* TODO: Transition instances might map to start before or after.
    When it maps to asyncBefore it should be fine. When it maps to asyncAfter an execution is fired twice in C7 and C8.
     */
    Arrays.asList(activityInstance.getChildTransitionInstances()).forEach(ti -> {
      var transitionInstance = ((TransitionInstanceImpl) ti);
      if (!"subProcess".equals(transitionInstance.getActivityType())) {
        // TODO skip migration of process instance when multi-instance is active
        activeActivities.put(transitionInstance.getId(), new ActInstance(transitionInstance.getActivityId(), transitionInstance.getSubProcessInstanceId()));
      }
    });
    return activeActivities;
  }

  record ActInstance(String id, String subProcessInstanceId) {
  }

  public static List<Path> findResourceFilesWithExtension(String extension)  {
    List<Path> result = new ArrayList<>();
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Enumeration<URL> resources = null;
    try {
      resources = classLoader.getResources("");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    while (resources.hasMoreElements()) {
      URL resource = resources.nextElement();
      if (resource.getProtocol().equals("file")) {
        File directory = new File(URLDecoder.decode(resource.getPath(), StandardCharsets.UTF_8));
        if (directory.isDirectory()) {
          Path resourceFolder = Paths.get(directory.getPath(), "resources");
          if (Files.exists(resourceFolder) && Files.isDirectory(resourceFolder)) {
            result.addAll(findFilesInDirectory(resourceFolder.toFile(), extension));
          }
        }
      }
    }
    return result;
  }

  public static List<Path> findFilesInDirectory(File directory, String extension) {
    List<Path> result = new ArrayList<>();
    try {
      Files.walk(Paths.get(directory.toURI()))
          .filter(path -> path.toString().endsWith("." + extension))
          .forEach(result::add);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return result;
  }

  public void setAutoDeployment(boolean autoDeployment) {
    this.autoDeployment = autoDeployment;
  }

}
