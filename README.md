# C7 Data Migrator

[![Java Version](https://img.shields.io/badge/Java-21-blue)](https://www.oracle.com/java/technologies/downloads/#java21)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![Status](https://img.shields.io/badge/Status-In%20Development-yellow)](https://github.com/camunda/c7-data-migrator)

A tool for migrating Camunda 7 process instances and related data to Camunda 8. This migrator helps organizations transition their running process instances while preserving execution state, variables, and business data.

> [!WARNING]  
> The C7 Data Migrator is still in development and not yet ready for production use. However, we encourage users to try it out and provide feedback.

## Table of Contents
- [Key Features](#key-features)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Migration Process](#migration-process)
- [Configuration](#configuration)
- [Migration Limitations](#migration-limitations)
- [Architecture](#architecture)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)

## Key Features

- State-preserving migration of running process instances
- Variable data migration with type conversion
- Error handling and retry mechanisms
- Detailed logging and reporting
- Support for complex BPMN constructs

## Prerequisites

- Java 21 or higher
- Maven 3.6+ (for building from source)
- Running instance of Camunda 8
- Access to Camunda 7 database
- Migrated BPMN models (C7 → C8)

## Quick Start

1. Download the latest release or build from source:
   ```bash
   mvn clean install
   ```

2. Configure your source (C7) and target (C8) connection details in `application.yml`

3. Run the migrator:
   ```bash
   ./start.sh  # or start.bat on Windows
   ```

## Migration Process

1. **Preparation Phase**
   - Stop C7 process execution
   - Migrate your BPMN models using the [Migration Analyzer](https://migration-analyzer.consulting-sandbox.camunda.cloud/)
   - Add required `migrator` execution listeners to normal flow start events of C8 models
   - Adjust C8 models to ensure compatibility with migration limitations
   - Test migrated models in C8

2. **Migration Phase**
   - Deploy C8 process models and resources
   - Start the migrator
   - Monitor progress in logs
   - Verify results in Operate
   - Redeploy C8 models if necessary
     - Remove `migrator` execution listeners from C8 models after successful migration
     - Revert changes in C8 models if necessary
     - Migrate process instances to latest version of C8 models

3. **Validation Phase**
   - Check migrated instances
   - Verify variable data
   - Test process continuation

## Migration Limitations

### Runtime Migration

- To migrate running process instances, the historic process instance must exist.
  - You cannot migrate running instances when you have configured history level to `NONE` or a custom history level that doesn't create historic process instances.
  - The minimum supported history level is `ACTIVITY`.
- You need to add an execution listener of type `migrator` to all your start events.

### Process Instance Validation

The migrator validates each process instance before migration and will skip instances that fail validation for the following reasons:

1. **Missing C8 Process Definition**
   - If no corresponding C8 process definition is found for the C7 process ID
   - Error message: "No C8 process found for process ID [processId] required for instance with legacyID [instanceId]"

2. **Multi-Instance Activities**
   - If the process instance has active multi-instance activities
   - Error message: "Found multi-instance loop characteristics for [elementName] in C7 process instance [instanceId]"

3. **Missing Flow Node Elements**
   - If a C7 process instance is currently at a flow node that doesn't exist in the deployed C8 model
   - Error message: "C7 instance detected which is currently in a C7 flow node which does not exist in the equivalent deployed C8 model"

When a process instance is skipped:
- The skip is logged with a warning message
- The instance is marked as skipped in the migration database
- You can list skipped instances using the migrator tool
- You can retry migration of skipped instances after fixing the underlying issues

### Handling Skipped Instances

1. **List Skipped Instances**
   ```bash
   ./start.sh --list-skipped
   ```

2. **Retry Skipped Instances**
   ```bash
   ./start.sh --retry-skipped
   ```

3. **Common Resolution Steps**
   - Deploy the missing C8 process definition
   - Wait for multi-instance activities to complete
   - Ensure all active flow nodes have corresponding elements in C8
   - Modify process instance state to a supported configuration

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
  - Processes with timer start events are not yet supported for migration. We plan to address this limitation with [this ticket](https://github.com/camunda/camunda-bpm-platform/issues/5200)
  - If your model contains other timer events, you must ensure that no timers fire during the migration process.
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


## Configuration

The C7 Data Migrator can be configured using properties in your `application.yml` file. Here's a complete example configuration.

### Available Properties

| Property Name                       | Description                                                            | Default Value |
|-------------------------------------|------------------------------------------------------------------------|-------------|
| `migrator.batch-size`               | Number of items (process instances, jobs) to be processed per request. | 500         |
| `migrator.rdbms-exporter.auto-ddl`  | Enable automatic database schema creation for exporter.                | true        |
| `migrator.c7.auto-ddl`              | Enable automatic database schema creation for C7.                      | true        |
| `migrator.source.jdbc-url`          | JDBC URL for the source database.                                      | jdbc:h2:./h2/data-migrator-source.db |
| `migrator.source.username`          | Username for the source database.                                      | sa          |
| `migrator.source.password`          | Password for the source database.                                      | -           |
| `migrator.source.driver-class-name` | Driver class for the source database.                                  | org.h2.Driver |
| `migrator.target.jdbc-url`          | JDBC URL for the target database.                                      | jdbc:h2:./h2/data-migrator-target.db |
| `migrator.target.username`          | Username for the target database.                                      | sa          |
| `migrator.target.password`          | Password for the target database.                                      | -           |
| `migrator.target.driver-class-name` | Driver class for the target database.                                  | org.h2.Driver |
| `camunda.client.*`                  | Read more about [all configuration options](https://docs.camunda.io/docs/next/apis-tools/spring-zeebe-sdk/configuration/).                         |             |
| `camunda.client.mode`               | Camunda 8 operating mode (e.g., 'self-managed')                        | -           |
| `camunda.client.grpc-address`       | gRPC API address for Camunda 8                                         | http://localhost:26500 |
| `camunda.client.rest-address`       | REST API address for Camunda 8                                         | http://localhost:8088 |
| `logging.level.root`                | Root logging level                                                     | INFO        |
| `logging.level.io.camunda.migrator` | Logging level for migrator components                                  | INFO        |
| `logging.file.name`                 | Log file location                                                      | logs/c7-data-migrator.log |

## Development

### Building from Source

1. Clone the repository
2. Build the project:
   ```bash
   mvn clean install
   ```
3. Find distribution in `assembly/target/`

### Running Tests

```bash
mvn verify
```

## Contributing

Read the [Contributions Guide](https://github.com/camunda/camunda-bpm-platform/blob/master/CONTRIBUTING.md).

### License headers

Every source file in an open-source repository needs to contain the following license header at the top, formatted as this file:
[license header](./license/header.txt).

## License

The source files in this repository are made available under the [Camunda License Version 1.0](./CAMUNDA-LICENSE-1.0.txt)
