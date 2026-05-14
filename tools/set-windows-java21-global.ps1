[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-JavaVersionOutput {
    param(
        [Parameter(Mandatory = $true)]
        [string] $JavaExe
    )

    $output = & $JavaExe -version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to run ${JavaExe} -version"
    }
    return ($output -join "`n")
}

function Assert-JavaVersion {
    param(
        [Parameter(Mandatory = $true)]
        [string] $JavaExe,
        [Parameter(Mandatory = $true)]
        [string] $ExpectedVersion
    )

    $versionOutput = Get-JavaVersionOutput -JavaExe $JavaExe
    if ($versionOutput -notmatch "version `"$([regex]::Escape($ExpectedVersion))") {
        throw "${JavaExe} is not Java ${ExpectedVersion}:`n${versionOutput}"
    }
    return $versionOutput
}

if (-not (Test-IsAdministrator)) {
    $arguments = @(
        '-NoProfile',
        '-ExecutionPolicy',
        'Bypass',
        '-File',
        "`"$PSCommandPath`""
    )
    $process = Start-Process -FilePath powershell.exe -ArgumentList $arguments -Verb RunAs -Wait -PassThru
    exit $process.ExitCode
}

$expectedVersion = '21.0.6'
$scriptDirectory = Split-Path -Parent $PSCommandPath
$repoRoot = Split-Path -Parent $scriptDirectory
$source = Join-Path $repoRoot 'build\toolchains\zulu-21.0.6\zulu21.40.17-ca-jdk21.0.6-win_x64'
$sourceJava = Join-Path $source 'bin\java.exe'
$target = 'C:\Program Files\Zulu\zulu-21.0.6'
$targetBin = Join-Path $target 'bin'
$targetJava = Join-Path $targetBin 'java.exe'

Write-Host "Configuring machine-wide Java ${expectedVersion}."
Write-Host "Source: ${source}"
Write-Host "Target: ${target}"

if (-not (Test-Path -LiteralPath $sourceJava -PathType Leaf)) {
    throw "Expected JDK source was not found: ${sourceJava}"
}

$sourceVersion = Assert-JavaVersion -JavaExe $sourceJava -ExpectedVersion $expectedVersion
Write-Host "Verified source JDK:"
Write-Host $sourceVersion

New-Item -ItemType Directory -Force -Path $target | Out-Null
Get-ChildItem -LiteralPath $source -Force | Copy-Item -Destination $target -Recurse -Force

$targetVersion = Assert-JavaVersion -JavaExe $targetJava -ExpectedVersion $expectedVersion
Write-Host "Verified installed JDK:"
Write-Host $targetVersion

[Environment]::SetEnvironmentVariable('JAVA_HOME', $target, 'Machine')

$machinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
$entries = $machinePath -split ';' | Where-Object { $_ -and $_.Trim().Length -gt 0 }
$targetBinCanonical = $targetBin.TrimEnd('\')
$filteredEntries = $entries | Where-Object {
    $entry = $_.Trim().TrimEnd('\')
    $entry -ine $targetBinCanonical
}
$newEntries = @($targetBin) + $filteredEntries
[Environment]::SetEnvironmentVariable('Path', ($newEntries -join ';'), 'Machine')

$env:JAVA_HOME = $target
$env:Path = "${targetBin};${env:Path}"

Write-Host ''
Write-Host "Machine JAVA_HOME is now:"
Write-Host ([Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine'))
Write-Host ''
Write-Host "First machine PATH entries are now:"
([Environment]::GetEnvironmentVariable('Path', 'Machine') -split ';' | Select-Object -First 5) |
    ForEach-Object { Write-Host "  $_" }
Write-Host ''
Write-Host 'Open a new terminal, then run:'
Write-Host '  where java'
Write-Host '  java -version'
Write-Host '  .\gradlew.bat --stop'
