#!/usr/bin/env bash

set -e

sbt clean
#sbt test
sbt macrosJS/publishLocal macrosJVM/publishLocal coreJS/publishLocal coreJVM/publishLocal plugin/publishLocal