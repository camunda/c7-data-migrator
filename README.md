# C7 Data Migrator

> [!WARNING]  
> The C7 Data Migrator is still in development and not yet ready for production use. 

However, even though the C7 Data Migrator is not yet ready for production, we encourage users to try it out, play around with the current functionality and provide feedback.

## How to use the Migrator

1. Prerequisites: Use Java 21
2. Consider whether any of the [migration limitations](#migration-limitations) apply to your current C7 models and apply any changes necessary.
1. Stop C7 process execution. I.e., shut down your engines.
2. Migrate your models 
   1. Can use https://migration-analyzer.consulting-sandbox.camunda.cloud/ to migrate your C7 models.
   2. If required, make any manual adjustments required in the C8 models. Note the migrator tool will leave hints in your diagram.
   3. Add "migrator" End Execution listener of the start event of each C8 model. Example: [link](./qa/src/test/resources/io/camunda/migrator/bpmn/c8/simpleProcess.bpmn).
1. Start C8.
1. Deploy migrated C8 process models and any other required resources such as referenced DMN or forms.
1. Build or download the distribution.
1. Start Migrator (start.sh/start.bat) and wait to finish.
   2. Do not make any changes to the C8 deployments while the migrator is running.
1. Navigate to Operate and check result.
1. When a process instance gets skipped, you can start C7 again, modify the process instance into a supported state and shut down C7 again.

## Migration Limitations

### Runtime Migration

- To migrate running process instances, the historic process instance must exist.
  - You cannot migrate running instances when you have configured history level to `NONE` or a custom history level that doesn't create historic process instances.
  - The minimum supported history level is `ACTIVITY`.
- You need to add an execution listener of type `migrator` to all your start events.

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

## Configuration

* `migrator.batch-size` - configure the number of items (process instances, jobs) to be processed per iteration. Default: 500

## Troubleshooting

**Q:** I can see the process instances in Operate, but they are stuck in the `migrator` listener on start events. What am I missing?
**A:** If the migration stopped unexpectedly before/during the `migrator` execution listener job execution, rerun the Data Migrator to complete the migration.

## Development Setup
1. Prerequisites: Use Java 21
1. Set up Camunda 8
   1. Download Camunda [c8run-8.8](https://github.com/camunda/camunda/releases/tag/c8run-8.8)
   1. Extract files
   1. Start from distribution root by running `./start.sh` (or `c8run.exe start`)
1. Clone this repository
1. Build from root with `mvn clean install` (add `-DskipTests` to skip the tests)
1. (Optional) Run the [example data generator](./examples/generate-runtime/src/main/java/io/camunda/migrator/example/ExampleApplication.java)
1. (Optional) Run the migrator
   1. Either using [the example](./examples/migrate-runtime/src/main/java/io/camunda/migrator/example/RuntimeExampleApplication.java)
   1. Or by building, extracting and running [the distribution](./assembly)

## Contributing

Read the [Contributions Guide](https://github.com/camunda/camunda-bpm-platform/blob/master/CONTRIBUTING.md).

### License headers

Every source file in an open-source repository needs to contain the following license header at the top, formatted as this file:
[license header](./license/header.txt).

## License

The source files in this repository are made available under the [Camunda License Version 1.0](./CAMUNDA-LICENSE-1.0.txt)
