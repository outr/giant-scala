#!/usr/bin/env bash

set -e

sbt +clean +test
sbt +macros/publishSigned +core/publishSigned sonatypeRelease