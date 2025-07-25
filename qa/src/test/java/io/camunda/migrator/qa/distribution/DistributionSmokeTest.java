/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migrator.qa.distribution;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Smoke test for the ZIP distribution that validates the start.sh script functionality.
 * This test extracts the ZIP distribution and executes the start.sh script to ensure
 * the basic functionality works as expected.
 */
class DistributionSmokeTest {

  @TempDir
  protected Path tempDir;

  protected Path extractedDistributionPath;
  protected Path startScriptPath;
  protected Process process;

  @BeforeEach
  void setUp() throws IOException {
    extractZipDistribution();
    makeScriptExecutable();
  }

  @AfterEach
  public  void tearDown() {
    if (process != null) {
      process.destroyForcibly();
    }
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void shouldShowUsageWhenInvalidFlagProvided() throws Exception {
    // given
    ProcessBuilder processBuilder = new ProcessBuilder("./start.sh", "--invalid-flag");
    processBuilder.directory(extractedDistributionPath.toFile());
    processBuilder.redirectErrorStream(true);

    // when
    Process process = processBuilder.start();

    // then
    String output = readProcessOutput(process);
    int exitCode = process.waitFor();

    assertThat(exitCode).isEqualTo(1);
    assertThat(output).contains("Invalid flag: --invalid-flag");
    assertThat(output).contains("Usage: start.sh");
    assertThat(output).contains("--runtime");
    assertThat(output).contains("--history");
    assertThat(output).contains("--list-skipped");
    assertThat(output).contains("--retry-skipped");
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  void shouldShowUsageWhenTooManyArgumentsProvided() throws Exception {
    // given
    ProcessBuilder processBuilder = new ProcessBuilder("./start.sh", "--runtime", "--history", "--list-skipped", "--retry-skipped");
    processBuilder.directory(extractedDistributionPath.toFile());
    processBuilder.redirectErrorStream(true);

    // when
    Process process = processBuilder.start();

    // then
    String output = readProcessOutput(process);
    int exitCode = process.waitFor();

    assertThat(exitCode).isEqualTo(1);
    assertThat(output).contains("Error: Too many arguments.");
    assertThat(output).contains("Usage: start.sh");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldAcceptValidFlags() throws Exception {
    // given
    String[] validFlags = {"--runtime", "--history", "--list-skipped", "--retry-skipped"};

    for (String flag : validFlags) {
      ProcessBuilder processBuilder = new ProcessBuilder("./start.sh", flag);
      processBuilder.directory(extractedDistributionPath.toFile());
      processBuilder.redirectErrorStream(true);

      // when
      process = processBuilder.start();

      // then
      String output = readProcessOutput(process);

      assertThat(output).contains("Starting migration with flags: " + flag);
    }
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldStartWithoutArgumentsAndShowExpectedMessage() throws Exception {
    // given
    ProcessBuilder processBuilder = new ProcessBuilder("./start.sh");
    processBuilder.directory(extractedDistributionPath.toFile());
    processBuilder.redirectErrorStream(true);

    // when
    process = processBuilder.start();

    // then
    String output = readProcessOutput(process);
    assertThat(output).contains("Starting application without migration flags");
  }

  @Test
  void shouldHaveRequiredFilesInDistribution() {
    assertThat(extractedDistributionPath.resolve("start.sh")).exists();
    assertThat(extractedDistributionPath.resolve("start.bat")).exists();
    assertThat(extractedDistributionPath.resolve("configuration")).exists();
    assertThat(extractedDistributionPath.resolve("internal")).exists();
    assertThat(extractedDistributionPath.resolve("LICENSE.TXT")).exists();
    assertThat(extractedDistributionPath.resolve("NOTICE.txt")).exists();
    assertThat(extractedDistributionPath.resolve("README.txt")).exists();
  }

  @Test
  void shouldHaveExecutableStartScript() {
    assertThat(startScriptPath.toFile().canExecute()).isTrue();
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldDeployBpmnModelFromResourcesFolder() throws Exception {
    // given
    Path resourcesDir = extractedDistributionPath.resolve("configuration/resources");
    Files.createDirectories(resourcesDir);

    String simpleBpmnModel = "<bpmn:definitions />";
    Path bpmnFile = resourcesDir.resolve("test-process.bpmn");
    Files.write(bpmnFile, simpleBpmnModel.getBytes());

    ProcessBuilder processBuilder = new ProcessBuilder("./start.sh", "--runtime");
    processBuilder.directory(extractedDistributionPath.toFile());
    processBuilder.redirectErrorStream(true);

    // when
    process = processBuilder.start();

    // then
    String output = readProcessOutput(process);
    assertThat(output).contains("Failed to deploy resources: [./configuration/resources/test-process.bpmn]");
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldApplyConfigurationChanges() throws Exception {
    // given
    Path configFile = extractedDistributionPath.resolve("configuration/application.yml");
    assertThat(configFile).exists();

    // Read the existing configuration file
    String originalConfig = Files.readString(configFile);

    // Modify the configuration by uncommenting and changing the page-size property
    String modifiedConfig = originalConfig
        .replace("auto-ddl: true", "auto-ddl: false");

    Files.write(configFile, modifiedConfig.getBytes());

    ProcessBuilder processBuilder = new ProcessBuilder("./start.sh", "--runtime");
    processBuilder.directory(extractedDistributionPath.toFile());
    processBuilder.redirectErrorStream(true);

    // when
    process = processBuilder.start();

    // then
    String output = readProcessOutput(process);

    // Verify the application started with our modified configuration
    assertThat(output).contains("ENGINE-03057 There are no Camunda tables in the database.");
  }

  protected void extractZipDistribution() throws IOException {
    Path zipFile = findZipDistribution();
    assertThat(zipFile).exists();

    extractedDistributionPath = tempDir.resolve("extracted");
    Files.createDirectories(extractedDistributionPath);

    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        Path outputPath = extractedDistributionPath.resolve(entry.getName());

        if (entry.isDirectory()) {
          Files.createDirectories(outputPath);
        } else {
          Files.createDirectories(outputPath.getParent());
          Files.copy(zis, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
        zis.closeEntry();
      }
    }

    // Find the actual distribution directory (it should be nested)
    try (var stream = Files.list(extractedDistributionPath)) {
      extractedDistributionPath = stream
          .filter(Files::isDirectory)
          .filter(path -> path.getFileName().toString().startsWith("c7-data-migrator-assembly"))
          .findFirst()
          .orElse(extractedDistributionPath);
    }

    startScriptPath = extractedDistributionPath.resolve("start.sh");
  }

  protected Path findZipDistribution() {
    // Look for the ZIP file in the assembly target directory
    Path assemblyTarget = Paths.get(System.getProperty("user.dir"))
        .resolve("../assembly/target");

    try (var stream = Files.list(assemblyTarget)) {
      return stream
          .filter(path -> path.getFileName().toString().endsWith(".zip"))
          .filter(path -> path.getFileName().toString().contains("c7-data-migrator-assembly"))
          .findFirst()
          .orElseThrow(() -> new RuntimeException("ZIP distribution not found in " + assemblyTarget));
    } catch (IOException e) {
      throw new RuntimeException("Failed to find ZIP distribution", e);
    }
  }

  protected void makeScriptExecutable() {
    if (startScriptPath.toFile().exists()) {
      boolean success = startScriptPath.toFile().setExecutable(true);
      if (!success) {
        throw new RuntimeException("Failed to make start.sh executable");
      }
    }
  }

  protected String readProcessOutput(Process process) throws IOException {
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append(System.lineSeparator());
      }
    }
    return output.toString();
  }
}
