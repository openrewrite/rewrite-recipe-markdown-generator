#!/usr/bin/env bash
set -euo pipefail

# Installs all OpenRewrite and Moderne recipe modules into the Moderne CLI
# marketplace, then exports recipes-v5.csv for use in documentation sites.
#
# Usage:
#   ./install-marketplace.sh [output-dir]
#
# Arguments:
#   output-dir  Directory to write recipes-v5.csv to (default: build/docs)
#
# Prerequisites:
#   - Moderne CLI (mod) must be installed and on PATH
#   - npm and pip must be available for TypeScript/Python recipe modules

OUTPUT_DIR="${1:-build/docs}"
mkdir -p "$OUTPUT_DIR"

if ! command -v mod &>/dev/null; then
  echo "Error: Moderne CLI (mod) is not installed or not on PATH." >&2
  echo "Install it from https://docs.moderne.io/user-documentation/moderne-cli/getting-started/cli-intro" >&2
  exit 1
fi

echo "==> Clearing existing recipe marketplace..."
mod config recipes delete

echo "==> Installing Maven recipe modules..."
mod config recipes jar install \
  org.openrewrite:rewrite-core:LATEST \
  org.openrewrite:rewrite-csharp:LATEST \
  org.openrewrite:rewrite-docker:LATEST \
  org.openrewrite:rewrite-gradle:LATEST \
  org.openrewrite:rewrite-groovy:LATEST \
  org.openrewrite:rewrite-hcl:LATEST \
  org.openrewrite:rewrite-java:LATEST \
  org.openrewrite:rewrite-javascript:LATEST \
  org.openrewrite:rewrite-json:LATEST \
  org.openrewrite:rewrite-kotlin:LATEST \
  org.openrewrite:rewrite-maven:LATEST \
  org.openrewrite:rewrite-properties:LATEST \
  org.openrewrite:rewrite-protobuf:LATEST \
  org.openrewrite:rewrite-python:LATEST \
  org.openrewrite:rewrite-toml:LATEST \
  org.openrewrite:rewrite-xml:LATEST \
  org.openrewrite:rewrite-yaml:LATEST \
  org.openrewrite.meta:rewrite-analysis:LATEST \
  org.openrewrite.recipe:rewrite-ai-search:LATEST \
  org.openrewrite.recipe:rewrite-all:LATEST \
  org.openrewrite.recipe:rewrite-android:LATEST \
  org.openrewrite.recipe:rewrite-apache:LATEST \
  org.openrewrite.recipe:rewrite-azul:LATEST \
  org.openrewrite.recipe:rewrite-circleci:LATEST \
  org.openrewrite.recipe:rewrite-codemods:LATEST \
  org.openrewrite.recipe:rewrite-codemods-ng:LATEST \
  org.openrewrite.recipe:rewrite-compiled-analysis:LATEST \
  org.openrewrite.recipe:rewrite-concourse:LATEST \
  org.openrewrite.recipe:rewrite-cucumber-jvm:LATEST \
  org.openrewrite.recipe:rewrite-dotnet:LATEST \
  org.openrewrite.recipe:rewrite-dropwizard:LATEST \
  org.openrewrite.recipe:rewrite-feature-flags:LATEST \
  org.openrewrite.recipe:rewrite-github-actions:LATEST \
  org.openrewrite.recipe:rewrite-gitlab:LATEST \
  org.openrewrite.recipe:rewrite-hibernate:LATEST \
  org.openrewrite.recipe:rewrite-jackson:LATEST \
  org.openrewrite.recipe:rewrite-java-dependencies:LATEST \
  org.openrewrite.recipe:rewrite-java-security:LATEST \
  org.openrewrite.recipe:rewrite-jenkins:LATEST \
  org.openrewrite.recipe:rewrite-joda:LATEST \
  org.openrewrite.recipe:rewrite-kubernetes:LATEST \
  org.openrewrite.recipe:rewrite-liberty:LATEST \
  org.openrewrite.recipe:rewrite-logging-frameworks:LATEST \
  org.openrewrite.recipe:rewrite-micrometer:LATEST \
  org.openrewrite.recipe:rewrite-micronaut:LATEST \
  org.openrewrite.recipe:rewrite-migrate-java:LATEST \
  org.openrewrite.recipe:rewrite-netty:LATEST \
  org.openrewrite.recipe:rewrite-nodejs:LATEST \
  org.openrewrite.recipe:rewrite-okhttp:LATEST \
  org.openrewrite.recipe:rewrite-openapi:LATEST \
  org.openrewrite.recipe:rewrite-prethink:LATEST \
  org.openrewrite.recipe:rewrite-quarkus:LATEST \
  org.openrewrite.recipe:rewrite-reactive-streams:LATEST \
  org.openrewrite.recipe:rewrite-rewrite:LATEST \
  org.openrewrite.recipe:rewrite-spring:LATEST \
  org.openrewrite.recipe:rewrite-spring-to-quarkus:LATEST \
  org.openrewrite.recipe:rewrite-sql:LATEST \
  org.openrewrite.recipe:rewrite-static-analysis:LATEST \
  org.openrewrite.recipe:rewrite-struts:LATEST \
  org.openrewrite.recipe:rewrite-terraform:LATEST \
  org.openrewrite.recipe:rewrite-testing-frameworks:LATEST \
  org.openrewrite.recipe:rewrite-third-party:LATEST \
  io.moderne.recipe:rewrite-ai:LATEST \
  io.moderne.recipe:rewrite-angular:LATEST \
  io.moderne.recipe:rewrite-cryptography:LATEST \
  io.moderne.recipe:rewrite-devcenter:LATEST \
  io.moderne.recipe:rewrite-elastic:LATEST \
  io.moderne.recipe:rewrite-hibernate:LATEST \
  io.moderne.recipe:rewrite-java-application-server:LATEST \
  io.moderne.recipe:rewrite-jasperreports:LATEST \
  io.moderne.recipe:rewrite-kafka:LATEST \
  io.moderne.recipe:rewrite-program-analysis:LATEST \
  io.moderne.recipe:rewrite-prethink:LATEST \
  io.moderne.recipe:rewrite-react:LATEST \
  io.moderne.recipe:rewrite-spring:LATEST \
  io.moderne.recipe:rewrite-tapestry:LATEST \
  io.moderne.recipe:rewrite-vulncheck:LATEST

echo "==> Installing npm recipe modules..."
mod config recipes npm install \
  @openrewrite/rewrite \
  @openrewrite/recipes-nodejs

echo "==> Installing pip recipe modules..."
mod config recipes pip install \
  openrewrite

echo "==> Exporting recipes-v5.csv..."
mod config recipes export csv "$OUTPUT_DIR/recipes-v5.csv"

echo "==> Done! Wrote $OUTPUT_DIR/recipes-v5.csv"
