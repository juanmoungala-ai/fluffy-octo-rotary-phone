#!/bin/sh
##############################################################################
# Gradle start up script for UN*X
##############################################################################
APP_HOME=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Download wrapper jar if missing
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Téléchargement de Gradle wrapper..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    curl -sL -o "$WRAPPER_JAR" \
        "https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar" \
        2>/dev/null || \
    curl -sL -o "$WRAPPER_JAR" \
        "https://services.gradle.org/distributions/gradle-8.4-bin.zip" \
        2>/dev/null
fi

exec java -jar "$WRAPPER_JAR" "$@"
