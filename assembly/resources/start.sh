#!/bin/bash
BASEDIR=$(dirname "$0")
CONFIGURATION="$BASEDIR/configuration/application.yml"
DEPLOYMENT_DIR="$BASEDIR/configuration/resources"
classPath="$BASEDIR/configuration/userlib"

JAR_PATH="$BASEDIR/internal/c7-data-migrator.jar"
COMMON_OPTS="-Dloader.path=$classPath -Dmigrator.deployment-dir=$DEPLOYMENT_DIR -Dspring.config.location=file:$CONFIGURATION"

print_usage() {
  echo "Usage: run.sh [--runtime] [--history] [--retry]"
  echo "Options:"
  echo "  --runtime     - Migrate runtime data only"
  echo "  --history     - Migrate history data only"
  echo "  --retry       - Retry only previously skipped data"
}

if [[ $# -gt 3 ]]; then
  echo "Error: Too many arguments."
  print_usage
  exit 1
fi

for arg in "$@"; do
  case "$arg" in
    --runtime|--history|--retry)
      ;;
    *)
      echo "Invalid flag: $arg"
      print_usage
      exit 1
      ;;
  esac
done

if [[ $# -eq 0 ]]; then
  echo "Starting application without migration flag"
  java $COMMON_OPTS -jar "$JAR_PATH" --runtime --history
else
  echo "Starting migration with flags: $*"
  java $COMMON_OPTS -jar "$JAR_PATH" "$@"
fi