#!/usr/bin/env sh
cd "$(dirname "$0")" || exit 1

if ! command -v java >/dev/null 2>&1; then
  echo "Java was not found. Install JDK 21 or newer (https://adoptium.net) and run this again."
  exit 1
fi

chmod +x ./gradlew
echo "Building SkyLyrics. The first run downloads Minecraft and takes a few minutes."
if ! ./gradlew build; then
  echo "The build failed. Copy the error above and send it to Jax."
  exit 1
fi

mkdir -p JAR-READY
cp build/libs/skylyrics-1.0.0.jar JAR-READY/
echo "DONE: $(pwd)/JAR-READY/skylyrics-1.0.0.jar"
echo "Drop it into your Fabric 1.21.4 mods folder (plus Fabric API)."
