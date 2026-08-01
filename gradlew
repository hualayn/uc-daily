#!/bin/sh
# Gradle wrapper script
WRAPPER_MAIN="org.gradle.wrapper.GradleWrapperMain"
GRADLE_WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$GRADLE_WRAPPER_JAR" ]; then
    echo "gradle-wrapper.jar not found. Please generate it via Android Studio or:"
    echo "  gradle wrapper"
    exit 1
fi

exec java -jar "$GRADLE_WRAPPER_JAR" "$@"
