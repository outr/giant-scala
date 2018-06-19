#!/usr/bin/env bash

set -e

sbt +clean
sbt +test
sbt +macrosJS/publishSigned +macrosJVM/publishSigned +coreJS/publishSigned +coreJVM/publishSigned sonatypeRelease