param(
    [string]$JavaHome = "D:\Program\DevEco Studio\jbr",
    [string]$OhosSdkHome = "D:\Program\DevEco Studio\sdk\default\openharmony",
    [string]$DevecoStudioHome = "D:\Program\DevEco Studio"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

$env:JAVA_HOME = $JavaHome
$env:OHOS_SDK_HOME = $OhosSdkHome
$env:DEVECO_STUDIO_HOME = $DevecoStudioHome

Push-Location $root
try {
    Write-Host "[sync-ohos-shared] Build :shared:linkDebugSharedOhosArm64"
    & .\gradlew.bat -c settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }

    $soSource = Join-Path $root "shared\build\bin\ohosArm64\debugShared\libshared.so"
    $headerSource = Join-Path $root "shared\build\bin\ohosArm64\debugShared\libshared_api.h"
    $soTargetDir = Join-Path $root "ohosApp\entry\libs\arm64-v8a"
    $headerTargetDir = Join-Path $root "ohosApp\entry\src\main\cpp\include"

    New-Item -ItemType Directory -Path $soTargetDir -Force | Out-Null
    New-Item -ItemType Directory -Path $headerTargetDir -Force | Out-Null

    Copy-Item -LiteralPath $soSource -Destination (Join-Path $soTargetDir "libshared.so") -Force
    Copy-Item -LiteralPath $headerSource -Destination (Join-Path $headerTargetDir "libshared_api.h") -Force

    Write-Host "[sync-ohos-shared] Copied libshared.so and libshared_api.h"
} finally {
    Pop-Location
}