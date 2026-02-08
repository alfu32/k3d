#!/bin/bash

export SDKMAN_DIR="$HOME/.sdkman"
[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"

tag=$1

echo $tag

BUILD_RUNDATE=$(date '+%Y-%m-%d %H:%M:%S')
GIT_COMMIT=$(git rev-parse HEAD)
GIT_BRANCH="$(git branch | egrep "^\* ")"
GIT_TAG=$(git describe --tags $(git rev-list --tags --max-count=1))

export VERSIONFILE=core/src/main/kotlin/com/github/alfu32/sketch/K3DVersion.kt
cat > $VERSIONFILE <<VERSIONCLASS
package com.github.alfu32.sketch

data class K3DVersion(
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
rm -rf ./dist
mkdir -p dist
# Iterate over the list of versions
for jdk_release in $versions
do
    # Extract the major version number (the first number before the dot)
    major_version="${jdk_release%%.*}"
    echo "creating dist/voxd31-editor-desktop-jvm$major_version-$tag.jar using sdk release $jdk_release"
    sdk use java "$jdk_release"
    ./gradlew clean dist pluginApiJar "-PjavaCompatVersion=$major_version" -PreleaseNumber=$tag
    mv lwjgl3/build/libs/*.jar dist/
    ## git add -f dist/voxd31-editor-desktop-jvm$major_version-$tag.jar
    # cp ./assets/voxd31.icon.png  dist/
done

mkdir -p dist/plugins
cp core/build/libs/k3d-plugin-api*.jar dist/plugins/ 2>/dev/null || true
cp scripts/*.groovy dist/plugins/ 2>/dev/null || true
rm -f dist/plugins/polyline.groovy


export LAUNCHER_LINUX=dist/k3d-editor
# cat > $LAUNCHER_LINUX <<LAUNCHERLINUXSCRIPT
# #!/bin/bash
#
# INSTALL_PATH="$(cd "\$(dirname "\$0")" && pwd)"
# RUN_PATH="$(pwd)"
#
# if [[ -n "\$1" ]]; then
#   java -jar "\$INSTALL_PATH/k3d-editor.jar" --file "\$1"
# else
#   java -jar "\$INSTALL_PATH/k3d-editor.jar"
# fi
# LAUNCHERLINUXSCRIPT
# chmod +x $LAUNCHER_LINUX

mv dist/k3d-*.jar dist/k3d-editor.jar

cp scripts/k3d-editor $LAUNCHER_LINUX
chmod +x $LAUNCHER_LINUX

export WINDOWS_INSTALLER=dist/k3d.install.cmd
cat > $WINDOWS_INSTALLER <<WININSTALLSCRIPT
@echo off
setlocal

:: Get Java version and capture it into a variable
for /f "tokens=3" %%i in ('java -version 2^>^&1') do (
    set "JAVA_VERSION=%%i"
    goto version_done
)
:version_done

:: Clean up Java version string
set JAVA_VERSION=%JAVA_VERSION:"=%

:: Get the absolute path of the batch script
set "SCRIPT_PATH=%~dp0"

:: Optionally remove the trailing backslash
set "SCRIPT_PATH=%SCRIPT_PATH:~0,-1%"

:: Print the Java version and script path
echo Java Version: %JAVA_VERSION%
echo Script Path: %SCRIPT_PATH%

echo "java -jar %SCRIPT_PATH%\\k3d-editor.jar edit --file \"%%1\"" > k3d.cmd

echo Windows Registry Editor Version 5.00^
     ^
 ; Associate .kt3 files with a custom application^
 [HKEY_CLASSES_ROOT\\.kt3]^
 @="K3DFile"^
 ^
 [HKEY_CLASSES_ROOT\\kt3File]^
 @="kt3 File"^
 ^
 ; Default icon (optional)^
 [HKEY_CLASSES_ROOT\\K3DFile\\DefaultIcon]^
 @="%SCRIPT_PATH%\\Icon.ico"^
 ^
 ; Command to execute^
 [HKEY_CLASSES_ROOT\\K3DFile\\shell\\open\\command]^
 @="\\"%SCRIPT_PATH%\\K3D.cmd\\" \\"%%1\\""^
 > K3D.open-kt3.reg

:: End of script
endlocal
pause
WININSTALLSCRIPT
