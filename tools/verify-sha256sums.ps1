[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $ChecksumFile
)

$ErrorActionPreference = 'Stop'

$checksumPath = (Resolve-Path -LiteralPath $ChecksumFile).Path
$lineNumber = 0
$checkedCount = 0
$violations = [System.Collections.Generic.List[string]]::new()

foreach ($line in [System.IO.File]::ReadLines($checksumPath)) {
    $lineNumber += 1
    $trimmedLine = $line.Trim()
    if ($trimmedLine.Length -eq 0 -or $trimmedLine.StartsWith('#')) {
        continue
    }

    if ($trimmedLine -notmatch '^([0-9A-Fa-f]{64})[ \t]+(.+)$') {
        $violations.Add("${ChecksumFile}:${lineNumber}: expected '<sha256>  <path>'")
        continue
    }

    $expectedHash = $Matches[1].ToLowerInvariant()
    $relativePath = $Matches[2].Trim()
    if ($relativePath.StartsWith('*')) {
        $relativePath = $relativePath.Substring(1)
    }

    if ($relativePath.Length -eq 0) {
        $violations.Add("${ChecksumFile}:${lineNumber}: checksum entry has an empty path")
        continue
    }

    $filePath = Join-Path -Path (Get-Location) -ChildPath $relativePath
    if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
        $violations.Add("${relativePath}: file not found")
        continue
    }

    $actualHash = (Get-FileHash -LiteralPath $filePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $expectedHash) {
        $violations.Add("${relativePath}: expected ${expectedHash} but was ${actualHash}")
        continue
    }

    $checkedCount += 1
    Write-Output "${relativePath}: OK"
}

if ($checkedCount -eq 0) {
    $violations.Add("${ChecksumFile}: no checksum entries found")
}

if ($violations.Count -gt 0) {
    throw "SHA-256 checksum verification failed:`n  $($violations -join "`n  ")"
}

Write-Output "Verified ${checkedCount} checksum entries from ${ChecksumFile}."
