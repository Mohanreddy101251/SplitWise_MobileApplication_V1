# Builds SpliWise: unit tests + debug APK. Output also saved to build.log.
# Uses the Gradle already unpacked in E:\AndroidDev (the wrapper re-download times out on slow networks).
$env:JAVA_HOME = 'E:\AndroidDev\jdk\jdk-17.0.21.0.2+1'
$env:ANDROID_HOME = 'E:\AndroidDev\sdk'
Set-Location $PSScriptRoot
& E:\AndroidDev\gradle-8.11.1\bin\gradle.bat testDebugUnitTest assembleDebug --console=plain 2>&1 | Tee-Object -FilePath build.log
"EXIT CODE: $LASTEXITCODE" | Tee-Object -FilePath build.log -Append
