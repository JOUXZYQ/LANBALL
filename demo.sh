#!/bin/sh
# Rehearsal: two windows on one machine, separate demo-data folder.
cd "$(dirname "$0")" && exec java -cp LanBall-v9.jar lanball.Demo
