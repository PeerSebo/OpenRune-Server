param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$gradleTemp = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.jdk-afunix-tmp'
New-Item -ItemType Directory -Force -Path $gradleTemp | Out-Null
$env:TEMP = $gradleTemp
$env:TMP = $gradleTemp
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Push-Location $repoRoot
try {
    & "$repoRoot\gradlew.bat" @GradleArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
