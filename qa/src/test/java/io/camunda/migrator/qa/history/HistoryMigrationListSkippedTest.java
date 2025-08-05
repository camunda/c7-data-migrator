/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.history;

import static io.camunda.migrator.MigratorMode.LIST_SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.migrator.impl.clients.DbClient;
import io.camunda.migrator.impl.persistence.IdKeyMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(locations = "classpath:application-warn.properties")
@ExtendWith({OutputCaptureExtension.class})
public class HistoryMigrationListSkippedTest extends HistoryMigrationAbstractTest {

    @Autowired
    protected DbClient dbClient;

    @Autowired
    private ManagementService managementService;

    @Autowired
    protected HistoryService historyService;

    @Test
    public void shouldListAllSkippedHistoryEntities(CapturedOutput output) {
        // given multiple process instances with comprehensive entity generation
        deployer.deployCamunda7Process("comprehensiveSkippingTestProcess.bpmn");

        List<String> processInstanceIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            var processInstance = runtimeService.startProcessInstanceByKey("comprehensiveSkippingTestProcessId",
                Map.of("testVar", "testValue" + i, "anotherVar", "anotherValue" + i));
            processInstanceIds.add(processInstance.getId());

            // Complete user tasks to generate user task history
            var tasks = taskService.createTaskQuery().processInstanceId(processInstance.getId()).list();
            for (Task task : tasks) {
                taskService.setVariableLocal(task.getId(), "taskLocalVar", "taskValue" + i);
                taskService.complete(task.getId());
            }

            // Execute failing service task jobs to generate incidents
            var jobs = managementService.createJobQuery().processInstanceId(processInstance.getId()).list();
            for (var job : jobs) {
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        managementService.executeJob(job.getId());
                    } catch (Exception e) {
                        // Expected - job will fail and create incident
                    }
                }
            }
        }

        // Verify we have the expected entities in C7
        assertThat(historyService.createHistoricProcessInstanceQuery().count()).isEqualTo(3);
        assertThat(historyService.createHistoricTaskInstanceQuery().count()).isEqualTo(3);
        assertThat(historyService.createHistoricVariableInstanceQuery().count()).isGreaterThan(6); // process vars + task vars
        assertThat(historyService.createHistoricIncidentQuery().count()).isEqualTo(3);
        assertThat(historyService.createHistoricActivityInstanceQuery().count()).isEqualTo(12); // multiple flow nodes per instance

        // Mark the process definition as skipped
        String processDefinitionId = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey("comprehensiveSkippingTestProcessId")
            .latestVersion()
            .singleResult()
            .getId();
        dbClient.insert(processDefinitionId, null, IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION);

        // Run history migration to skip all entities
        historyMigrator.migrate();

        // Verify all entities were marked as skipped
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_PROCESS_DEFINITION)).isEqualTo(1);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_PROCESS_INSTANCE)).isEqualTo(3);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_USER_TASK)).isEqualTo(3);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_INCIDENT)).isEqualTo(3);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_VARIABLE)).isGreaterThan(6);
        assertThat(dbClient.countSkippedByType(IdKeyMapper.TYPE.HISTORY_FLOW_NODE)).isEqualTo(12);

        // when running history migration with list skipped mode
        historyMigrator.setMode(LIST_SKIPPED);
        historyMigrator.start();

        // Parse the output into a structured map
        Map<String, List<String>> skippedEntitiesByType = parseSkippedEntitiesOutput(output.getOut());

        // then all skipped history entities were listed with proper structure
        assertThat(skippedEntitiesByType).containsKeys(
            "historic process definitions",
            "historic process instances",
            "historic flow nodes",
            "historic user tasks",
            "historic variables",
            "historic incidents",
            "historic decision definitions"
        );

        // Assert process definitions - verify the actual process definition ID from C7
        List<String> expectedProcessDefinitionIds = List.of(processDefinitionId);
        assertThat(skippedEntitiesByType.get("historic process definitions"))
            .hasSize(1)
            .containsExactlyInAnyOrderElementsOf(expectedProcessDefinitionIds);

        // Assert process instances - verify the actual process instance IDs from C7
        assertThat(skippedEntitiesByType.get("historic process instances"))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(processInstanceIds);

        // Assert user tasks - verify the actual user task IDs from C7
        List<String> expectedUserTaskIds = historyService.createHistoricTaskInstanceQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(task -> task.getId())
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get("historic user tasks"))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(expectedUserTaskIds);

        // Assert incidents - verify the actual incident IDs from C7
        List<String> expectedIncidentIds = historyService.createHistoricIncidentQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(incident -> incident.getId())
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get("historic incidents"))
            .hasSize(3)
            .containsExactlyInAnyOrderElementsOf(expectedIncidentIds);

        // Assert variables - verify the actual variable IDs from C7
        List<String> expectedVariableIds = historyService.createHistoricVariableInstanceQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(variable -> variable.getId())
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get("historic variables"))
            .hasSize(9)
            .containsAll(expectedVariableIds);

        // Assert flow nodes - verify the actual flow node (activity instance) IDs from C7
        List<String> expectedFlowNodeIds = historyService.createHistoricActivityInstanceQuery()
            .processDefinitionId(processDefinitionId)
            .list()
            .stream()
            .map(activity -> activity.getId())
            .collect(Collectors.toList());
        assertThat(skippedEntitiesByType.get("historic flow nodes"))
            .hasSize(12)
            .containsExactlyInAnyOrderElementsOf(expectedFlowNodeIds);

        // Assert decision definitions - verify that none were skipped (should be empty list)
        assertThat(skippedEntitiesByType.get("historic decision definitions"))
            .isEmpty();
    }

    /**
     * Parses the migrator output and extracts skipped entities.
     *
     * @param output The full output from the migrator
     * @return Map where key is entity type (e.g., "historic process instances") and value is list of entity IDs
     */
    private Map<String, List<String>> parseSkippedEntitiesOutput(String output) {
        Map<String, List<String>> result = new HashMap<>();

        // Step 1: Find first occurrence and drop everything before it
        Pattern firstMatchPattern = Pattern.compile("(Previously skipped [^:]+:|No [^:]+: were skipped during previous migration)");
        Matcher firstMatcher = firstMatchPattern.matcher(output);
        if (!firstMatcher.find()) {
            return result; // No skipped entities section found
        }
        String relevantOutput = output.substring(firstMatcher.start());

        // Step 2: Split on all header patterns to get sections
        Pattern sectionPattern = Pattern.compile("(Previously skipped ([^:]+):|No ([^\\s]+[^:]*) were skipped during previous migration)");
        String[] sections = sectionPattern.split(relevantOutput);

        // Get all header matches to extract entity types
        Matcher headerMatcher = sectionPattern.matcher(relevantOutput);
        List<String> entityTypes = new ArrayList<>();

        while (headerMatcher.find()) {
            String entityType;
            if (headerMatcher.group(2) != null) {
                // "Previously skipped X:" format
                entityType = headerMatcher.group(2);
            } else {
                // "No X were skipped" format
                entityType = headerMatcher.group(3);
            }
            entityTypes.add(entityType);
        }

        // Step 3: Process each section with its corresponding entity type
        for (int i = 0; i < entityTypes.size() && i + 1 < sections.length; i++) {
            String entityType = entityTypes.get(i);
            String sectionContent = sections[i + 1].trim();

            List<String> entityIds = new ArrayList<>();
            if (!sectionContent.isEmpty()) {
                // Split by lines and filter out empty lines
                entityIds = Arrays.stream(sectionContent.split("\\R"))
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
            }

            result.put(entityType, entityIds);
        }

        return result;
    }
}
