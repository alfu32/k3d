#!/bin/bash

export SDKMAN_DIR="$HOME/.sdkman"
[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"

tag=$1

echo $tag

BUILD_RUNDATE=$(date '+%Y-%m-%d %H:%M:%S')
GIT_COMMIT=$(git rev-parse HEAD)
GIT_BRANCH="$(git branch | egrep "^\* ")"
GIT_TAG=$(git describe --tags $(git rev-list --tags --max-count=1))

export VERSIONFILE=core/src/main/kotlin/com/github/alfu32/sketch/Katechup3dVersion.kt
cat > $VERSIONFILE <<VERSIONCLASS
package com.github.alfu32.sketch

data class Katechup3dVersion(
  val buildGitCommit:String = "$GIT_COMMIT",
  val buildDate:String = "$BUILD_RUNDATE",
  val buildGitBranch:String = "${GIT_BRANCH/\*\ /}",
  val buildGitTag:String = "$GIT_TAG",
  val buildVersion:String = "$tag"
)
VERSIONCLASS

# Define a list of version strings
if [[ "$2" == "" ]];then
  versions="21.0.9-tem"
else
  versions="$2"
fi
mkdir -p dist
rm -rf ./dist/*.jar
# Iterate over the list of versions
for jdk_release in $versions
do
    # Extract the major version number (the first number before the dot)
    major_version="${jdk_release%%.*}"
    echo "creating dist/voxd31-editor-desktop-jvm$major_version-$tag.jar using sdk release $jdk_release"
    sdk use java "$jdk_release"
    ./gradlew clean dist  "-PjavaCompatVersion=$major_version" -PreleaseNumber=$tag
    mv lwjgl3/build/libs/*.jar dist/
    ## git add -f dist/voxd31-editor-desktop-jvm$major_version-$tag.jar
    # cp ./assets/voxd31.icon.png  dist/
done


cp scripts/katechup3d* ./dist/
