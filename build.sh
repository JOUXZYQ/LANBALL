#!/bin/sh
# Builds LanBall-v7.jar with the JDK only (Java 8 or newer). No Maven, no libraries.
cd "$(dirname "$0")" || exit 1
rm -rf out && javac -d out src/lanball/*.java && jar cfe LanBall-v7.jar lanball.LanBall -C out . && echo "Built LanBall-v7.jar"
