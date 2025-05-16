This is a distribution of

       Camunda 7 Data Migrator v${project.version}

visit
       https://github.com/camunda/c7-data-migrator/blob/main/README.md

==================

Usage

Before running the migrator, some setup steps are required for both Camunda 7 and Camunda 8. Please refer to the [project README](https://github.com/camunda/c7-data-migrator/blob/main/README.md) for details.
To run the Camunda 7 Data Migrator:

1. Adjust `./configuration/application.yml`  
   - The `source` config should point to your Camunda 7 database  
   - The `target` config should point to your Camunda 8 database

2. Execute the `start` script:  
   - `start.bat` for Windows  
   - `start.sh` for Linux/macOS

Options

- `--runtime` – Migrate runtime data only  
- `--history` – Migrate history data only 

==================

Contents:

  /
      Contains two start scripts: one for Windows (`.bat`) and one for Linux/macOS (`.sh`).

  internal/
      Contains the Java application.

  configuration/
      Contains all configuration resources, including `application.yml`.

  userlib/
      Add custom database drivers, plugins, or other extensions here.

  logs/
      Created during run time and contains the log outputs


==================

      Camunda 7 Data Migrator v${project.version}

=================
