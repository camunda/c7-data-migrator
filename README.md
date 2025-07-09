# C7 Data Migrator

[![Java Version](https://img.shields.io/badge/Java-21-blue)](https://www.oracle.com/java/technologies/downloads/#java21)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![Status](https://img.shields.io/badge/Status-In%20Development-yellow)](https://github.com/camunda/c7-data-migrator)

A tool for migrating Camunda 7 process instances and related data to Camunda 8. This migrator helps organizations seamlessly transition their process instances while preserving execution state and variables ensuring minimal disruption to ongoing business processes.

> [!WARNING]  
> The C7 Data Migrator is currently in active development and not yet ready for production use. However, we encourage users to try it out in development/testing environments and provide feedback to help us improve the tool.

## Table of Contents
- [Overview](#overview)
- [Key Features](#key-features)
- [Prerequisites](#prerequisites)
- [Installation & Setup](#installation--setup)
- [Quick Start](#quick-start)
- [Supported Databases](#supported-databases)
- [Configuration](#configuration)
- [Migration Limitations](#migration-limitations)
- [Troubleshooting](#troubleshooting)
- [Migration Process](#migration-process)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)

## Overview

The C7 Data Migrator is designed to help organizations migrate from Camunda 7 to Camunda 8 while preserving the state of running process instances. Unlike starting fresh with new process instances, this tool maintains variable states, and current positions within process flows.

**What this tool migrates:**
- Running process instances with their current state
- Process variables and their values

**What this tool does NOT migrate:**
- BPMN process models (use the [Migration Analyzer](https://migration-analyzer.consulting-sandbox.camunda.cloud/) for this)
- Custom code or integrations
- Users, groups and authorizations
- Task assignments and states
- Execution history (we are working on this currently)

## Key Features

- **State-preserving migration**: Maintains exact execution state of running process instances
- **Variable data migration**: Converts and migrates process variables with proper type handling
- **Validation and verification**: Pre-migration validation to ensure successful migration
- **Skip and retry capabilities**: Handle problematic instances gracefully with retry options
- **Detailed logging and reporting**: Comprehensive logging for monitoring migration progress
- **Database flexibility**: Support for multiple database vendors (H2, PostgreSQL, Oracle)

## Prerequisites

Before using the C7 Data Migrator, ensure you have:

- **Java 21 or higher** - Required for running the migrator
- **Maven 3.6+** - For building from source (if not using pre-built releases)
- **Running Camunda 8 instance** - Target platform for migration
- **Access to Camunda 7 database** - Source database with process instances to migrate
- **Migrated BPMN models** - Process definitions already converted from C7 to C8 format
- **Network connectivity** - Between migrator, C7 database, and C8 platform

## Installation & Setup

### Option 1: Download Pre-built Release
1. Download the latest release from the [releases page](https://github.com/camunda/c7-data-migrator/releases)
2. Extract the archive to your preferred directory
3. Navigate to the extracted directory

### Option 2: Build from Source
```bash
# Clone the repository
git clone https://github.com/camunda/c7-data-migrator.git
cd c7-data-migrator

# Build the project
mvn clean install -DskipTests

# Navigate to the distribution
cd assembly/target
# Extract the generated archive (tar.gz or zip)
```

## Quick Start

1. **Make sure Camunda 8 is up and running and all process models that shall be migrated are deployed.** 

   To be used with the Runtime Data Migrator, **every process model requires**:
   - A blank start event (you need to add one if the process model doesn't have one)
   - An execution listener at the end of your blank start event with the job type `migrator` (you have to add it manually, or let it be added by the [Migration Analyzer & Diagram Converter](https://github.com/camunda-community-hub/camunda-7-to-8-migration-analyzer)).
   
   ```xml
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:extensionElements>
        <zeebe:executionListeners>
          <zeebe:executionListener eventType="end" type="migrator" />
        </zeebe:executionListeners>
      </bpmn:extensionElements>
    </bpmn:startEvent>
   ```

2. **Prepare your configuration file** (`application.yml`):
   ```yaml
   camunda.client:
     mode: self-managed
     grpc-address: http://localhost:26500
     rest-address: http://localhost:8088
   
   camunda.migrator.c7.data-source:
     jdbc-url: jdbc:postgresql://localhost:5432/camunda7
     username: your-username
     password: your-password
   ```

3. **Run the migrator**:
   ```bash
   # On Linux/macOS
   ./start.sh --runtime
   
   # On Windows
   start.bat --runtime
   ```

4. **Monitor the migration progress** in the console output and log files.

## Runtime Migration Example
```bash
# Migrate running process instances
./start.sh --runtime

# List skipped instances
./start.sh --runtime --list-skipped

# Retry previously skipped instances
./start.sh --runtime --retry-skipped
```

## History Migration Example
```bash
# Migrate historical data
./start.sh --history
```

## Custom Configuration Example
```bash
# Use custom configuration file
./start.sh --spring.config.location=file:./my-config.yml
```

## Supported Databases

The migrator supports the following SQL databases:

| Database | Version | JDBC Driver | Notes |
|----------|---------|-------------|-------|
| **H2** | 2.3.232 | `org.h2.Driver` | Default, good for testing |
| **PostgreSQL** | 17 | `org.postgresql.Driver` | Recommended for production |
| **Oracle** | 23ai | `oracle.jdbc.OracleDriver` | Enterprise option |

### Database Setup Requirements:
1. **Include the appropriate JDBC driver** in your classpath
2. **Configure connection details** in `application.yml`
3. **Set table prefixes** if your existing installation uses them
4. **Verify connectivity** before starting migration
5. **Ensure sufficient disk space** for migration data

> **Note**: The database vendor is automatically detected but can be overridden using the `database-vendor` property.

## Configuration

The migrator can be configured using the `application.yml` file. Here are the available configuration options:

### Camunda 8 Client Configuration for Runtime Migration

```yaml
camunda.client:
  mode: self-managed                    # Operation mode: 'self-managed' or 'cloud'
  grpc-address: http://localhost:26500  # The gRPC API endpoint
  rest-address: http://localhost:8088   # The REST API endpoint
```

### Migrator Configuration
```yaml
camunda.migrator:
  batch-size: 500                      # Number of records to process in each batch
  auto-ddl: true                       # Automatically create/update database schema
  table-prefix: MY_PREFIX_             # Optional table prefix for migrator schema
  data-source: C7                      # Choose if the migrator schema is created on the data source of 'C7' or 'C8'
```

### Camunda 7 Database for Runtime and History Migration
```yaml
camunda.migrator.c7.data-source:
  table-prefix: MY_PREFIX_             # Optional prefix for C7 database tables
  auto-ddl: true                       # Automatically create/update C7 database schema
  jdbc-url: jdbc:h2:./h2/data-migrator-source.db
  username: sa                         # Database username
  password: sa                         # Database password
  driver-class-name: org.h2.Driver
```

### Camunda 8 RDBMS Database for History Migration
```yaml
camunda.migrator.c8.data-source:
  table-prefix: MY_PREFIX_             # Optional prefix for C8 RDBMS database tables
  auto-ddl: true                       # Automatically create/update C8 RDBMS database schema
  jdbc-url: jdbc:h2:./h2/data-migrator-target.db
  username: sa                         # Database username
  password: sa                         # Database password
  driver-class-name: org.h2.Driver
```

### Logging Configuration
```yaml
logging:
  level:
    root: INFO                                    # Root logger level
    io.camunda.migrator: INFO                     # Migrator logging
    io.camunda.migrator.RuntimeMigrator: DEBUG    # Runtime migration logging
    io.camunda.migrator.persistence.IdKeyMapper: DEBUG  # ID mapping logging
```

### Configuration Properties Overview

| Prefix | Property                    | Type      | Description                                                                                                                                               |
|------|-----------------------------|-----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `camunda.client` |                             |   | Read more about Camunda Client [configuration options](https://docs.camunda.io/docs/next/apis-tools/spring-zeebe-sdk/configuration/).                     |
| | `.mode`                     | `string`  | Operation mode of the Camunda 8 client. Options: `self-managed` or `cloud`. Default: `self-managed`                                                       |
| | `.grpc-address`             | `string`  | The gRPC API endpoint for Camunda 8 Platform. Default: `http://localhost:26500`                                                                           |
| | `.rest-address`             | `string`  | The REST API endpoint for Camunda 8 Platform. Default: `http://localhost:8088`                                                                            |
| `camunda.migrator` |                             |           |                                                                                                                                                           |
| | `.batch-size`               | `number`  | Number of records to process in each migration batch. Default: `500`                                                                                      |
| | `.auto-ddl`                 | `boolean` | Automatically create/update migrator database schema. Default: `false`                                                                                    |
| | `.table-prefix`             | `string`  | Optional prefix for migrator database tables. Default: _(empty)_                                                                                          |
| | `.data-source`              | `string`  | Choose if the migrator schema is created in the `C7` or `C8` data source. Default: `C7`                                                                   |
| | `.database-vendor`          | `string`  | Database vendor for migrator schema. Options: `h2`, `postgresql`, `oracle`. Default: Automatically detected.                                              |
| `camunda.migrator.c7.data-source` |                             |           |                                                                                                                                                           |
| | `.table-prefix`             | `string`  | Optional prefix for Camunda 7 database tables. Default: _(empty)_                                                                                         |
| | `.auto-ddl`                 | `boolean` | Automatically create/update Camunda 7 database schema. Default: `false`                                                                                   |
| | `.database-vendor`          | `string`  | The database vendor is automatically detected and can currently not be overridden.                                                                        |
| | `.*`                        |   | You can apply all [`HikariConfig` properties](https://github.com/brettwooldridge/HikariCP?tab=readme-ov-file#gear-configuration-knobs-baby). For example: |
| | `.jdbc-url`                 | `string`  | JDBC connection URL for the source Camunda 7 database. Default: `jdbc:h2:mem:migrator`                                                                    |
| | `.username`                 | `string`  | Username for Camunda 7 database connection. Default: `sa`                                                                                                 |
| | `.password`                 | `string`  | Password for Camunda 7 database connection. Default: `sa`                                                                                                 |
| | `.driver-class-name`        | `string`  | JDBC driver class for Camunda 7 database. Default: `org.h2.Driver`                                                                                        |
| `camunda.migrator.c8` |                             |           |                                                                                                                                                           |
| | `.deployment-dir`           | `string`   | Define directory which resources like BPMN processes are automatically deployed to C8.                                                                    |
| `camunda.migrator.c8.data-source` |                             |           | If the `c8.data-source` configuration is absent, the RDBMS history data migrator is disabled.                                                             |
| | `.table-prefix`             | `string`  | Optional prefix for Camunda 8 RDBMS database tables. Default: _(empty)_                                                                                   |
| | `.auto-ddl`                 | `boolean` | Automatically create/update Camunda 8 RDBMS database schema. Default: `false`                                                                             |
| | `.database-vendor`          | `string`  | Database vendor for C8 schema. Options: `h2`, `postgresql`, `oracle`. Default: Automatically detected.                                                    |
| | `.*`                        |   | You can apply all [`HikariConfig` properties](https://github.com/brettwooldridge/HikariCP?tab=readme-ov-file#gear-configuration-knobs-baby). For example:              |
| | `.jdbc-url`                 | `string`  | JDBC connection URL for the target Camunda 8 RDBMS database. Default: `jdbc:h2:mem:migrator`                                                              |
| | `.username`                 | `string`  | Username for Camunda 8 database connection. Default: `sa`                                                                                                 |
| | `.password`                 | `string`  | Password for Camunda 8 database connection. Default: `sa`                                                                                                 |
| | `.driver-class-name`        | `string`  | JDBC driver class for Camunda 8 database. Default: `org.h2.Driver`                                                                                        |
| `logging` |                             |           |                                                                                                                                                           |
| | `.level.root`               | `string`  | Root logger level. Default: `INFO`                                                                                                                        |
| | `.level.io.camunda.migrator` | `string`  | Migrator logging level. Default: `INFO`                                                                                                                   |
| | `.file.name`                | `string`  | Log file location. Set to: `logs/c7-data-migrator.log`. If not specified, logs are output to the console.                                                 |

## Variable transformation

The `VariableInterceptor` interface allows you to define custom logic that executes whenever a variable is accessed or modified during migration. This is useful for auditing, transforming, or validating variable values.

### How to Implement a VariableInterceptor

1. Create a class that implements `VariableInterceptor`.
2. Annotate your class with `@Component` to register it as a Spring bean.
3. Override the `execute(VariableInvocation invocation)` method to add your logic.

**Example:**
```java
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100) // Optional: controls execution order if multiple interceptors exist
public class MyVariableInterceptor implements VariableInterceptor {
  @Override
  public void execute(VariableInvocation invocation) throws Exception {
    Object value = invocation.getC7Variable().getValue();
    // Custom logic here
    invocation.setVariableValue(value); // Optionally modify the value
  }
}
```

### Execution Order

- If multiple interceptors are present, their execution order is determined by the `@Order` annotation (lower values run first).
- If `@Order` is not specified, the default order is used.
-


---

**Already Implemented Interceptors**

The following interceptors are already implemented in the project:

1. **DefaultVariableInterceptor**
- Handles formatting and migration of primitive and object variables.

2. **DateVariableInterceptor**
- Converts Camunda 7 Date variables to Camunda 8 compatible format `yyyy-MM-dd'T'HH:mm:ss.SSSZ`.
- The interceptor is ordered with priority 10 to ensure it runs after the default interceptor.

*To see all registered interceptors and their order, you can fetch them from the Spring context as described previously.*


## Migration Limitations

### Runtime Migration

- To migrate running process instances, the historic process instance must exist.
  - You cannot migrate running instances when you have configured history level to `NONE` or a custom history level that doesn't create historic process instances.
  - The minimum supported history level is `ACTIVITY`.
- You need to add an execution listener of type `migrator` to all your start events.

#### Process Instance Validation

The migrator validates each process instance before migration and will skip instances that fail validation for the following reasons:

1. **Missing C8 Process Definition**
   - If no corresponding C8 process definition is found for the C7 process ID

2. **Multi-Instance Activities**
   - If the process instance has active multi-instance activities

3. **Missing Flow Node Elements**
   - If a C7 process instance is currently at a flow node that doesn't exist in the deployed C8 model

4. **Missing Non Start Event**
    - If a C8 process definition does not have a process level None Start Event, the migrator will skip the instance.

5. **Missing `migrator` Execution Listener on Non Start Event**
    - If a C8 process definition does not have an execution listener of type `migrator` on the None Start Event, the migrator will skip the instance.

When a process instance is skipped:
- The skipped process instance is logged
- The instance is marked as skipped in the migration database
- You can list skipped instances
- You can retry migration of skipped instances after fixing the underlying issues

#### Handling Skipped Instances

1. **List Skipped Instances**
   ```bash
   ./start.sh --runtime --list-skipped
   ```

2. **Retry Skipped Instances**
   ```bash
   ./start.sh --runtime --retry-skipped
   ```

3. **Common Resolution Steps**
   - Deploy the missing C8 process definition
   - Wait for multi-instance activities to complete
   - Ensure all active flow nodes in the C7 process have corresponding elements in the C8 process
   - Modify process instance to a supported state

#### Limitations for BPMN elements

- Async before/after wait states
  - C8 does not support [asynchronous continuation before or after](https://docs.camunda.org/manual/latest/user-guide/process-engine/transactions-in-processes/#asynchronous-continuations) any kind of wait state. Service-task-like activities are executed asynchronously by default in Camunda 8 - so for example a service task waiting for asynchronous continuation before will be correctly migrated. But if you need to migrate an instance currently waiting asynchronously at other elements in a C7 model, like for example a Gateway, this instance would just continue without waiting in the equivalent C8 model. You might need to adjust your model's logic accordingly prior to migration
- Data changed via user operations
  - Data set via user operations like setting a due date to a user task cannot be migrated currently. We plan to address this limitation with [this [ticket](https://github.com/camunda/camunda-bpm-platform/issues/5182).
- Message events
  - only message catch and throw events are supported for migration
  - depending on your implementation, you may need to add [a correlation variable](https://docs.camunda.io/docs/components/modeler/bpmn/message-events/#messages) to the instance pre migration
- Message and Signal start events
  - If your process starts with a message/signal start event, no token exists until the message/signal is received and hence no migration is possible until that moment
  - Once the message/signal is received, the token is created and moved down the execution flow and may be waiting at a migratable element inside the process. However, due to how the migration logic is implemented, at the moment the data migrator only supports processes that start with a normal start event. This is to be addressed with [this ticket](https://github.com/camunda/camunda-bpm-platform/issues/5195)
- Triggered Boundary events
  - C7 boundary events do not have a natural wait state
  - If the process instance to be migrated is currently at a triggered boundary event in Camunda 7, there may still be a job associated with that event, either waiting to be executed or currently running. In this state, the token is considered to be at the element where the job is created: typically the first activity of the boundary event’s handler flow, or technically the point after the boundary event if asyncAfter is used.
  - During migration to Camunda 8, the token will be mapped to the corresponding target element. However, if that element expects input data that is normally produced by the boundary event’s job (e.g. setting variables), this data may be missing in the migrated instance.
  - Recommendation: To ensure a consistent migration, allow boundary event executions to complete before initiating the migration.
- There are elements that are supported in C7 but not supported in C8. Please refer to the [documentation](https://docs.camunda.io/docs/next/components/modeler/bpmn/bpmn-coverage/) for more details on element support in C8 and adjust your models accordingly before migration.
- Call Activity
   - To migrate a subprocess that is started from a call activity, the migrator must set the `legacyId` variable for the subprocess. This requires propagating the parent variables. This can be achieved by updating the C8 call activity in one of the following ways:  
     - Set `propagateAllParentVariables` to `true` (this is the default) in the `zeebe:calledElement` extension element.  
     - Or, if `propagateAllParentVariables` is set to `false`, provide an explicit input mapping:  
   ```xml
   <zeebe:ioMapping>
     <zeebe:input source="=legacyId" target="legacyId" />
   </zeebe:ioMapping>
- Multi-instance:
  - Processes with active multi-instance elements can currently not be migrated. We recommend to finish the execution of any multi-instance elements prior to migration.
- Timer events:
  - Timer start events: prior to migration, you must ensure that your process has at least one [none start event](https://docs.camunda.io/docs/8.6/components/modeler/bpmn/none-events/#none-start-events). Processes that only have a timer start event cannot be migrated. 
  - If your model contains timer events (start and other), you must ensure that no timers fire during the migration process.
    - timers with [date](https://docs.camunda.io/docs/next/components/modeler/bpmn/timer-events/#time-date): ensure the date lies outside the migration time frame
    - timers with [durations](https://docs.camunda.io/docs/next/components/modeler/bpmn/timer-events/#time-duration): ensure the duration is significantly longer than the migration time frame
    - timers with [cycles](https://docs.camunda.io/docs/next/components/modeler/bpmn/timer-events/#time-cycle): ensure the cycle is significantly longer than the migration time frame and/or use a start time that lies outside the migration time frame
  - Note that during deployment and/or migration, the timers may be restarted. If business logic requires you to avoid resetting timer cycles/duration, you need to apply a workaround:
    - timers with cycles: 
      - add a start time to your cycle definition that is equal to the moment in time when the currently running C7 timer is next due
      - you must still ensure that the start time lies outside the migration time frame
    - timers with durations:
      - non interrupting timer boundary events:
        - switch to cycle definition with a start time that is equal to the moment in time when the currently running C7 timer is next due and add a "repeat once" configuration
        - this way, for the first post migration run, the timer will trigger at the start time
        - for all subsequent runs, the defined cycle duration will trigger the timer. The "repeat once" instruction ensures it only fires once, similar to a duration timer
        - you must still ensure that the start time lies outside the migration time frame
      - interrupting boundary and intermediate catching events
        - add a variable to your C7 instance that contains the leftover duration until the next timer is due
        - in your C8 model, adjust the timer duration definition to use an expression: if the variable is set, the value of this variable should be used for the duration. If the variable is not set or does not exist, you may configure a default duration
        - this way, for the first post migration run the variable will exist and the timer will set its duration accordingly
        - for all subsequent runs, the variable will not exist and the default duration will be used
        - again, you must ensure the leftover duration for the first post migration run lies outside the migration time frame
- Variables
    - Camunda 8 supported types: [documentation](https://docs.camunda.io/docs/components/concepts/variables/#variable-values)
    - Camunda 8 variable name restrictions: [documentation](https://docs.camunda.io/docs/next/components/concepts/variables/#variable-values).
      - Variables that do not follow the restrictions will cause issues in FEEL expression evaluation.
    - A Date variable (2025-06-20T11:32:06.868) is migrated to C8 in `ms` format (1750419126868). [ticket](https://github.com/camunda/camunda-bpm-platform/issues/5244)
    - Variables are serialized. (to be changed)
    - XML variable is migrated to JSON string variable. [ticket](https://github.com/camunda/camunda-bpm-platform/issues/5246)
      - Spin XML variable is migrated to XML string variable.
    - Variables set into the scope of embedded sub-processes are not supported yet and will be ignored. Will be implemented in this [ticket](https://github.com/camunda/camunda-bpm-platform/issues/5235).
- Event subprocess:
  - **Important limitation during migration**: Event subprocesses with interrupting start events can cause unexpected behavior during migration if triggered at the wrong moment. This includes timer, message, and signal start events.
  - **What can go wrong**:
    - A task that already ran in Camunda 7 might run again in Camunda 8.
    - The process might end up in the wrong state after migration — for example, being one step behind what you see in C7.
  - **When could it happen**:
    - This can occur when a process instance is already inside an event subprocess in C7, and the start event of that same subprocess is accidentally triggered again in C8 during migration.
  - **How to prevent it**:
    - **Don't correlate messages or send signals during migration**
    - **Temporarily adjust timer start events** in event subprocesses to ensure they do not trigger during migration:
      - See the section on timer events for more details
    - If above suggestions are not feasible in your use case **make sure service tasks are idempotent** — so repeating them does not cause issues.
- Start events
  - It is required that a process instance contains a single process level None Start Event to run the data migrator. 
  - If a process definition only has event-based start events (eg. Message, Timer), it is required to add a temporary None Start Event. This change needs to be reverted after the data migration is completed. 
  - Example adding a None Start Event: 

``` diff
  <bpmn:process id="Process_1fcbsv3" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1">
      <bpmn:outgoing>Flow_FromEventStartEvent</bpmn:outgoing>
      <bpmn:messageEventDefinition id="MessageEventDefinition_1yknqqn" />
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="Flow_FromEventStartEvent" sourceRef="StartEvent_1" targetRef="ActivityId" />
+   <bpmn:startEvent id="NoneStartEvent">
+     <bpmn:outgoing>Flow_FromNoneStartEvent</bpmn:outgoing>
+   </bpmn:startEvent>
+   <bpmn:sequenceFlow id="Flow_FromNoneStartEvent" sourceRef="NoneStartEvent" targetRef="ActivityId" />
    <bpmn:task id="ActivityId">
      <bpmn:incoming>Flow_FromEventStartEvent</bpmn:incoming>
      <bpmn:incoming>Flow_FromNoneStartEvent</bpmn:incoming>
      <bpmn:outgoing>Flow_1o2i34a</bpmn:outgoing>
    </bpmn:task>
    <bpmn:endEvent id="EndEvent">
      <bpmn:incoming>Flow_1o2i34a</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1o2i34a" sourceRef="ActivityId" targetRef="EndEvent" />
  </bpmn:process>
```

## Troubleshooting

### Common Issues and Solutions

#### Migration Fails to Start
**Symptoms**: Migrator exits immediately or fails to connect
**Solutions**:
- Verify Java 21+ is installed: `java -version`
- Check database connectivity and credentials
- Ensure Camunda 8 is running and accessible
- Review `application.yml` configuration

#### Process Instances Are Skipped
**Symptoms**: Instances appear in skipped list
**Solutions**:
- Check that C8 process definitions are deployed
- Verify `migrator` execution listeners are added to start events
- Ensure flow nodes exist in both C7 and C8 models
- Review the skipped instances log for specific reasons

#### Performance Issues
**Symptoms**: Slow migration speed
**Solutions**:
- Tweak `batch-size` in your configuration
- Ensure database has sufficient resources
- Check network latency between components
- Monitor system resources (CPU, memory, disk)

#### Variable Migration Errors
**Symptoms**: Variables not migrated correctly
**Solutions**:
- Check variable name restrictions for C8
- Verify variable types are supported
- Implement your customer variable interceptor if needed

### Getting Help

If you encounter issues not covered here:

1. **Check the logs** - Enable `DEBUG` logging for detailed information
2. **Review limitations** - Ensure your use case is supported
3. **Search existing issues** - Check GitHub issues for similar problems
4. **Create an issue** - Provide logs, configuration, and steps to reproduce

### Debugging Tips

Enable detailed logging for troubleshooting:

```yaml
logging:
  level:
    root: INFO
    io.camunda.migrator: DEBUG
    io.camunda.migrator.RuntimeMigrator: TRACE
  file:
    name: logs/c7-data-migrator.log
```

## Migration Process

The migration process consists of three main phases to ensure a successful transition from Camunda 7 to Camunda 8:

### 1. Preparation Phase
- **Stop C7 process execution** - Prevent new instances from starting during migration
- **Migrate your BPMN models** using the [Migration Analyzer](https://migration-analyzer.consulting-sandbox.camunda.cloud/)
- **Add required `migrator` execution listeners** to normal flow start events of C8 models
- **Adjust C8 models** to ensure compatibility with migration limitations
- **Test migrated models** in C8 environment thoroughly
- **Create backup** of your C7 database before starting migration

### 2. Migration Phase
- **Deploy C8 process models and resources** to the target environment
- **Configure the migrator** with proper database connections and settings
- **Start the migrator** and monitor progress through logs
- **Verify results** in Camunda 8 Operate
- **Handle skipped instances** by reviewing and addressing validation failures
- **Redeploy C8 models if necessary**:
  - Remove `migrator` execution listeners from C8 models after successful migration
  - Revert temporary changes in C8 models if necessary
  - Migrate process instances to latest version of C8 models

### 3. Validation Phase
- **Check migrated instances** in Camunda 8 Operate
- **Verify variable data** has been transferred correctly
- **Test process continuation** by completing some migrated instances
- **Monitor system performance** and resource usage
- **Validate business logic** continues to work as expected

## Development

### Building from Source

1. **Clone the repository**:
   ```bash
   git clone https://github.com/camunda/c7-data-migrator.git
   cd c7-data-migrator
   ```

2. **Build the project**:
   ```bash
   mvn clean install
   ```

3. **Find distribution** in `assembly/target/` directory

### Running Tests

Execute the full test suite:
```bash
mvn verify
```

Run specific test categories:
```bash
# Unit tests only
mvn test

# Integration tests only
mvn integration-test
```

### Development Environment Setup

1. **Install prerequisites**:
   - Java 21+
   - Maven 3.6+
   - Docker (for testing with different databases)

2. **Set up IDE** (IntelliJ IDEA/Eclipse):
   - Import as Maven project
   - Configure Java 21 as project SDK
   - Install Spring Boot plugin (recommended)

3. **Local development database**:
   ```bash
   # Start PostgreSQL with Docker
   docker run --name postgres-dev \
     -e POSTGRES_DB=camunda7 \
     -e POSTGRES_USER=camunda \
     -e POSTGRES_PASSWORD=camunda \
     -p 5432:5432 -d postgres:17
   ```

## Contributing

We welcome contributions to the C7 Data Migrator! Here's how you can help:

### Ways to Contribute

1. **Report bugs** - Create detailed issue reports
2. **Suggest features** - Propose new functionality
3. **Submit code** - Fix bugs or implement features
4. **Improve documentation** - Help others understand the tool
5. **Test and provide feedback** - Try the tool and share your experience

See our [issue tracker](https://github.com/camunda/camunda-bpm-platform/issues).

### Before Contributing

1. **Read the [Contributions Guide](https://github.com/camunda/camunda-bpm-platform/blob/master/CONTRIBUTING.md)**
2. **Check existing issues** to avoid duplicates
3. **Discuss major changes** in an issue before implementing

### Development Guidelines

- **Follow Java coding standards** and existing code style
- **Write tests** for new functionality
- **Update documentation** when adding features
- **Use meaningful commit messages**
- **Keep changes focused** - one feature/fix per pull request

### License Headers

Every source file must contain the license header. See [license header template](./license/header.txt) for the exact format required.

### Pull Request Process

1. Fork the repository
2. Create a feature branch from `main`
3. Make your changes with tests
4. Ensure all tests pass
5. Update documentation if needed
6. Submit a pull request with a clear description

## License

The source files in this repository are made available under the [Camunda License Version 1.0](./CAMUNDA-LICENSE-1.0.txt).

---

## Additional Resources

- **[Camunda 8 Documentation](https://docs.camunda.io/)** - Official Camunda 8 documentation
- **[Migration Guide](https://docs.camunda.io/docs/guides/migrating-from-camunda-platform-7/)** - General migration guidance
- **[Migration Analyzer](https://migration-analyzer.consulting-sandbox.camunda.cloud/)** - Tool for migrating BPMN models
- **[Community Forum](https://forum.camunda.io/)** - Get help from the community
- **[GitHub Issues](https://github.com/camunda/c7-data-migrator/issues)** - Report bugs and request features

---

*Last updated: July 2025*
