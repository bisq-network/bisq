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
$lfNormalizedPaths = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
$null = $lfNormalizedPaths.Add('gradlew')
$null = $lfNormalizedPaths.Add('gradle/wrapper/gradle-wrapper.properties')

function Get-Sha256Hex {
    param(
        [Parameter(Mandatory = $true)]
        [byte[]] $Bytes
    )

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return (($sha256.ComputeHash($Bytes) | ForEach-Object { $_.ToString('x2') }) -join '')
    } finally {
        $sha256.Dispose()
    }
}

function Convert-CrLfToLf {
    param(
        [Parameter(Mandatory = $true)]
        [byte[]] $Bytes
    )

    $normalized = [System.IO.MemoryStream]::new()
    try {
        for ($index = 0; $index -lt $Bytes.Length; $index += 1) {
            if ($Bytes[$index] -eq 13 -and ($index + 1) -lt $Bytes.Length -and $Bytes[$index + 1] -eq 10) {
                $normalized.WriteByte(10)
                $index += 1
            } else {
                $normalized.WriteByte($Bytes[$index])
            }
        }

        return $normalized.ToArray()
    } finally {
        $normalized.Dispose()
    }
}

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
        $manifestPath = $relativePath.Replace('\', '/')
        if ($lfNormalizedPaths.Contains($manifestPath)) {
            $normalizedHash = Get-Sha256Hex -Bytes (Convert-CrLfToLf -Bytes ([System.IO.File]::ReadAllBytes($filePath)))
            if ($normalizedHash -eq $expectedHash) {
                $checkedCount += 1
                Write-Output "${relativePath}: OK (CRLF normalized to LF)"
                continue
            }
        }

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
