#!/bin/sh
# Starts LanBall. Saved players and matches go in the lanball-v4-data folder next to this file.
cd "$(dirname "$0")" && exec java -jar LanBall-v7.jar "$@"
