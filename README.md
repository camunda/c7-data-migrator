# C7 Data Migrator

### How to use the Migrator

1. Prerequisites: Use Java 21
2. Consider whether any of the [migration limitations](#migration-limitations) apply to your current C7 models and apply any changes necessary.
1. Stop C7 process execution. I.e., shut down your engines.
2. Migrate your models 
   1. Can use https://migration-analyzer.consulting-sandbox.camunda.cloud/ to migrate your C7 models.
   2. If required, make any manual adjustments required in the C8 models. Note the migrator tool will leave hints in your diagram.
   3. Add "migrator" End Execution listener of the start event of each C8 model. Example: [link](./qa/src/test/resources/io/camunda/migrator/bpmn/c8/simpleProcess.bpmn).
1. Start C8.
1. Deploy migrated C8 process models.
1. Build or download the distribution.
1. Start Migrator (start.sh/start.bat) and wait to finish.
1. Navigate to Operate and check result.

#### Migration Limitations
- Message events:
  - only message catch and throw events are supported for migration
  - depending on your implementation, you may need to add [a correlation variable](https://docs.camunda.io/docs/components/modeler/bpmn/message-events/#messages) to the instance pre migration
- Boundary events:
  - currently not supported

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


## BPMN element migration FAQ
### eventBasedGateway
When migrating an `eventBasedGateway` from C7 to C8, the following additional changes are required:
- In C8 a `zeebe:subscription` inside an `extensionElements` is required for the `message` element.  This subscription must include a `correlationKey` property.
- The `correlationKey` has to reference an existing variable by name. For example:
```
  <bpmn:message id="Message_3qpc6ol" name="Message_3qpc6ol">
    <bpmn:extensionElements>
      <zeebe:subscription correlationKey="=catchEvent1CorrelationVariable" />
    </bpmn:extensionElements>
  </bpmn:message>
```
where `catchEvent1CorrelationVariable` must be a variable that exists when a token reaches the `eventBasedGateway`.


## Contributing

Read the [Contributions Guide](https://github.com/camunda/camunda-bpm-platform/blob/master/CONTRIBUTING.md).

### License headers

Every source file in an open-source repository needs to contain the following license header at the top, formatted as this file:
[license header](./license/header.txt).

## License

The source files in this repository are made available under the [Camunda License Version 1.0](./CAMUNDA-LICENSE-1.0.txt)
