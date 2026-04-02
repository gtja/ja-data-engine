#!/bin/bash

JAVA_OPTS="${JAVA_OPTS:--Djava.security.egd=file:/dev/./urandom -XX:TieredStopAtLevel=1 -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/home/jingansi/apps/logs/oom_dump.hprof -XX:ErrorFile=/home/jingansi/apps/logs/hs_err_%p.log}"

exec java ${JAVA_OPTS} -jar /home/jingansi/apps/app.jar
