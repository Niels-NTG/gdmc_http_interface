#!/bin/sh

echo "==================== Note: All build jars will be in the folder called 'buildAllJars' ===================="
mkdir -p buildAllJars | true

# Loop trough everything in the version properties folder
for d in versionProperties/*; do
  # Get the name of the version that is going to be compiled
  version=$(echo "$d" | sed "s/versionProperties\///" | sed "s/.properties//")

  # Clean out the folders, build it, and merge it
  # (We could use "./" to run gradlew, but as it is a shell script im going to be running it with the "sh" command)
  echo "==================== Cleaning workspace to build $version ===================="
  sh gradlew clean -PmcVer=$version --no-daemon || true
  echo "====================Building $version ===================="
  sh gradlew build -PmcVer=$version --no-daemon || true
  echo "====================Publishing $version ===================="
  sh gradlew publish -PmcVer=$version --no-daemon || true
  # The "| true" at the end of those are just to make sure the script continues even if a build fails
done
