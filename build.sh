#!/bin/sh
# Builds LanBall-v9.jar (needs JDK 17+).
set -e
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/classes/mobile
javac --release 17 -encoding UTF-8 -d build/classes src/lanball/*.java
cp resources/mobile/* build/classes/mobile/
jar --create --file LanBall-v9.jar --main-class lanball.LanBall -C build/classes .
echo Built LanBall-v9.jar
