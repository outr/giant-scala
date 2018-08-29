#!/usr/bin/env bash

set -e

sbt +clean
sbt +test
sbt +macrosJS/publishLocal +macrosJVM/publishLocal +coreJS/publishLocal +coreJVM/publishLocal ++2.12.6 plugin/publishLocal