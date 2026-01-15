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

export LAUNCHER_LINUX=dist/katechup3d-editor
cat > LAUNCHER_LINUX <<LAUNCHERLINUXSCRIPT
#!/bin/bash

java -jar "Katechup3d-1.0.0.jar" 1280x960 "$1"
LAUNCHERLINUXSCRIPT
chmod +x $LAUNCHER_LINUX

export WINDOWS_INSTALLER=dist/katechup3d.install.cmd
cat > WINDOWS_INSTALLER <<WININSTALLSCRIPT
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

echo "java -jar %SCRIPT_PATH%\\Katechup3d-1.0.0.jar.jar 1280x960 default.kt3" > Katechup3d.cmd

echo Windows Registry Editor Version 5.00^
     ^
 ; Associate .kt3 files with a custom application^
 [HKEY_CLASSES_ROOT\\.kt3]^
 @="Katechup3dFile"^
 ^
 [HKEY_CLASSES_ROOT\\kt3File]^
 @="kt3 File"^
 ^
 ; Default icon (optional)^
 [HKEY_CLASSES_ROOT\\Katechup3dFile\\DefaultIcon]^
 @="%SCRIPT_PATH%\\Icon.ico"^
 ^
 ; Command to execute^
 [HKEY_CLASSES_ROOT\\Katechup3dFile\\shell\\open\\command]^
 @="\\"%SCRIPT_PATH%\\Katechup3d.cmd\\" \\"%%1\\""^
 > Katechup3d.open-kt3.reg

:: End of script
endlocal
pause
WININSTALLSCRIPT
