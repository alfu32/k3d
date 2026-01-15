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

echo "java -jar %SCRIPT_PATH%\voxd31-editor-desktop.jar 1280x960 default.vxdi" > voxd31-editor.cmd

echo Windows Registry Editor Version 5.00^
     ^
 ; Associate .vxdi files with a custom application^
 [HKEY_CLASSES_ROOT\.vxdi]^
 @="VxdiFile"^
 ^
 [HKEY_CLASSES_ROOT\VxdiFile]^
 @="vxdi File"^
 ^
 ; Default icon (optional)^
 [HKEY_CLASSES_ROOT\VxdiFile\DefaultIcon]^
 @="%SCRIPT_PATH%\\Icon.ico"^
 ^
 ; Command to execute^
 [HKEY_CLASSES_ROOT\VxdiFile\shell\open\command]^
 @="\"%SCRIPT_PATH%\\voxd31-editor.cmd\" \"%%1\""^
 > voxd31-editor.open-vxdi.reg

:: End of script
endlocal
pause