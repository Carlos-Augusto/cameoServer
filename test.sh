#!/bin/bash

export SBT_OPTS="-Dsbt.log.noformat=true"

./sbt clean compile

for spec in $(ls test); do
    if [ -f "test/$spec" ] ; then
        specName=${spec%.*}
        echo "Testing: " ${specName}
        ./sbt "test-only ${specName} html junitxml console"
    fi
done