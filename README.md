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
  - C8 does not support [asynchronous continuation (before/after)](https://docs.camunda.org/manual/latest/user-guide/process-engine/transactions-in-processes/#asynchronous-continuations) wait states. Therefore, if you were to migrate an instance currently waiting asynchronously at an element in a C7 model, this instance would just continue without waiting in the equivalent C8 model. Please adjust your model's logic accordingly prior to migration
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
  - Processes with active tokens in [timer events](https://docs.camunda.org/manual/latest/reference/bpmn20/events/timer-events/) are not yet supported for migration. We have [this ticket](https://github.com/camunda/camunda-bpm-platform/issues/5173) to address this limitation in the future. 

## Configuration

* `migrator.batch-size` - configure the number of items (process instances, jobs) to be processed per iteration. Default: 500

## Troubleshooting

**Q:** I can see the process instances in Operate, but they are stuck in the `migrator` listener on start events. What am I missing?
**A:** If the migration stopped unexpectedly before/during the `migrator` execution listener job execution, rerun the Data Migrator to complete the migration.

**Q:** I can see the process instances in Operate, but they were started from the start event instead of migrating the tokens into the correct flow nodes.  What's wrong?
**A:** Check if you forgot to add the `migrator` execution listener to the start events of your process models in C8.

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
