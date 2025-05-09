# C7 Data Migrator


### How to use the Migrator

1. Prerequisites: Use Java 21
1. Stop C7 process execution. I.e., shut down your engines.
1. Start C8.
1. Deploy migrated C8 process models.
   1. Can use https://migration-analyzer.consulting-sandbox.camunda.cloud/ to migrate your C7 models.
   1. Add "migrator" End Execution listener of the start event of each C8 model. Example: [link](./qa/src/test/resources/io/camunda/migrator/bpmn/c8/simpleProcess.bpmn). 
1. Build or download the distribution.
1. Start Migrator (start.sh/start.bat) and wait to finish.
1. Navigate to Operate and check result.

## Contributing

Read the [Contributions Guide](https://github.com/camunda/camunda-bpm-platform/blob/master/CONTRIBUTING.md).

### License headers

Every source file in an open-source repository needs to contain the following license header at the top, formatted as this file:
[license header](./license/header.txt).

## License

The source files in this repository are made available under the [Camunda License Version 1.0](./CAMUNDA-LICENSE-1.0.txt)
