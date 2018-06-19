#!/usr/bin/env bash

set -e

sbt +clean
sbt +test
sbt +macros/publishSigned +core/publishSigned sonatypeRelease