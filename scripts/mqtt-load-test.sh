#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CLASSPATH_FILE="$(mktemp "${TMPDIR:-/tmp}/ja-uav-mqtt-load-test.XXXXXX")"
MAIN_CLASS="com.jingansi.uav.engine.biz.mqtt.tool.MqttLoadTestMain"

cleanup() {
  rm -f "$CLASSPATH_FILE"
}
trap cleanup EXIT

cd "$ROOT_DIR"

mvn -q -pl engine-biz -am -DskipTests compile dependency:build-classpath \
  -Dmdep.outputFile="$CLASSPATH_FILE" \
  -Dmdep.pathSeparator=:

CLASSPATH="$(cat "$CLASSPATH_FILE"):$ROOT_DIR/engine-common/target/classes:$ROOT_DIR/engine-dao/target/classes:$ROOT_DIR/engine-biz/target/classes"

exec java -cp "$CLASSPATH" "$MAIN_CLASS" "$@"
