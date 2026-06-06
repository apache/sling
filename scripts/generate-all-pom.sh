#!/bin/bash -eu

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

cat << POM_START
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.apache.sling</groupId>
    <artifactId>sling-all-reactor</artifactId>
    <packaging>pom</packaging>
    <version>1-SNAPSHOT</version>

POM_START

echo "    <modules>"
for repo_dir in $(repo list --path-only); do
    # TODO - skipping due to unexpected behaviour of feature tooling in aggregator
    if [[ "${repo_dir}" = "org-apache-sling-starter" || "${repo_dir}" = "org-apache-sling-app-cms" ]]; then
        continue
    fi
    if [ -f "${SCRIPT_DIR}/../../${repo_dir}/pom.xml" ]; then
        echo "        <module>$repo_dir</module>"
    fi
done
echo "    </modules>"
echo "</project>"
