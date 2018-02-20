#!/usr/bin/env bash

sbt +clean +test +macros/publishSigned +core/publishSigned sonatypeRelease