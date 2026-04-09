#!/bin/sh
#
# Gradle wrapper script для Unix
#

# Определяем директорию скрипта
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# JVM аргументы
DEFAULT_JVM_OPTS='-Dfile.encoding=UTF-8 "-Xmx64m" "-Xms64m"'

# Ищем java
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" $DEFAULT_JVM_OPTS \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
