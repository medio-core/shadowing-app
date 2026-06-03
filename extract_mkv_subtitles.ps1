#Requires -Version 5.1
<#
Extract subtitle tracks from MKV files with MKVToolNix.

Usage:
  1. Drag an MKV file onto extract_mkv_subtitles.bat.
  2. If no file is passed, a file picker will open.
#>

[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$InputFiles
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8

function Find-MkvTool {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $candidates = @(
        Join-Path $env:ProgramFiles "MKVToolNix\$Name.exe"
        Join-Path ${env:ProgramFiles(x86)} "MKVToolNix\$Name.exe"
        Join-Path $env:LOCALAPPDATA "Programs\MKVToolNix\$Name.exe"
    )

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return $candidate
        }
    }

    throw "$Name was not found. Please install MKVToolNix."
}

function Select-MkvFiles {
    Add-Type -AssemblyName System.Windows.Forms

    $dialog = New-Object -TypeName "System.Windows.Forms.OpenFileDialog"
    $dialog.Title = "Select MKV files"
    $dialog.Filter = "MKV files (*.mkv)|*.mkv|All files (*.*)|*.*"
    $dialog.Multiselect = $true

    if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
        return @()
    }

    return $dialog.FileNames
}

function Get-SubtitleExtension {
    param(
        [Parameter(Mandatory = $true)]
        [string]$CodecId
    )

    switch -Regex ($CodecId) {
        "S_TEXT/UTF8" { return "srt" }
        "S_TEXT/ASS" { return "ass" }
        "S_TEXT/SSA" { return "ssa" }
        "S_TEXT/WEBVTT" { return "vtt" }
        "S_HDMV/PGS" { return "sup" }
        "S_VOBSUB" { return "sub" }
        default { return "sub" }
    }
}

function Convert-AssTimeToSrt {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TimeText
    )

    if ($TimeText -notmatch "^(\d+):(\d{2}):(\d{2})\.(\d{2})$") {
        return $TimeText.Replace(".", ",")
    }

    $hours = [int]$Matches[1]
    $minutes = [int]$Matches[2]
    $seconds = [int]$Matches[3]
    $centis = [int]$Matches[4]
    $millis = $centis * 10

    return "{0:00}:{1:00}:{2:00},{3:000}" -f `
        $hours, $minutes, $seconds, $millis
}

function Convert-AssTextToPlain {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text
    )

    return $Text `
        -replace "\{[^}]*\}", "" `
        -replace "\\N", "`r`n" `
        -replace "\\n", "`r`n" `
        -replace "\\h", " "
}

function Convert-AssToSrt {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AssPath
    )

    $srtPath = [System.IO.Path]::ChangeExtension($AssPath, ".srt")
    $lines = Get-Content -LiteralPath $AssPath -Encoding UTF8
    $items = New-Object System.Collections.Generic.List[string]
    $index = 1

    foreach ($line in $lines) {
        if (-not $line.StartsWith("Dialogue:")) {
            continue
        }

        $payload = $line.Substring("Dialogue:".Length).TrimStart()
        $parts = $payload -split ",", 10
        if ($parts.Count -lt 10) {
            continue
        }

        $start = Convert-AssTimeToSrt $parts[1].Trim()
        $end = Convert-AssTimeToSrt $parts[2].Trim()
        $text = Convert-AssTextToPlain $parts[9]
        if ([string]::IsNullOrWhiteSpace($text)) {
            continue
        }

        $items.Add([string]$index)
        $items.Add("$start --> $end")
        $items.Add($text.Trim())
        $items.Add("")
        $index += 1
    }

    if ($items.Count -gt 0) {
        Set-Content -LiteralPath $srtPath -Value $items -Encoding UTF8
        return $srtPath
    }

    return $null
}

function Get-SafeFileNamePart {
    param(
        [string]$Text
    )

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return ""
    }

    $invalid = [Regex]::Escape(
        -join [System.IO.Path]::GetInvalidFileNameChars()
    )
    return ($Text -replace "[$invalid]", "_" -replace "\s+", "_").Trim("_")
}

function Get-TrackLanguage {
    param(
        [Parameter(Mandatory = $true)]
        $Track
    )

    $lang = $Track.properties.language_ietf
    if ([string]::IsNullOrWhiteSpace($lang)) {
        $lang = $Track.properties.language
    }
    if ([string]::IsNullOrWhiteSpace($lang)) {
        return "und"
    }
    return Get-SafeFileNamePart $lang
}

function Extract-Subtitles {
    param(
        [Parameter(Mandatory = $true)]
        [string]$MkvPath,
        [Parameter(Mandatory = $true)]
        [string]$MkvMerge,
        [Parameter(Mandatory = $true)]
        [string]$MkvExtract
    )

    if (-not (Test-Path -LiteralPath $MkvPath)) {
        Write-Warning "File not found: $MkvPath"
        return
    }

    Write-Host ""
    Write-Host "=== $MkvPath ==="

    $jsonText = & $MkvMerge --identification-format json --identify $MkvPath
    $info = $jsonText | ConvertFrom-Json
    $subtitleTracks = @($info.tracks | Where-Object { $_.type -eq "subtitles" })

    if ($subtitleTracks.Count -eq 0) {
        Write-Host "No subtitle tracks found."
        return
    }

    $directory = Split-Path -Parent $MkvPath
    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($MkvPath)
    $extractArgs = @("tracks", $MkvPath)
    $outputs = @()

    foreach ($track in $subtitleTracks) {
        $codecId = [string]$track.properties.codec_id
        $ext = Get-SubtitleExtension $codecId
        $lang = Get-TrackLanguage $track
        $trackName = Get-SafeFileNamePart $track.properties.track_name

        $nameParts = @($baseName, "track$($track.id)", $lang)
        if (-not [string]::IsNullOrWhiteSpace($trackName)) {
            $nameParts += $trackName
        }

        $outputName = (($nameParts -join "_") + ".$ext")
        $outputPath = Join-Path $directory $outputName

        $extractArgs += "$($track.id):$outputPath"
        $outputs += [PSCustomObject]@{
            Path = $outputPath
            CodecId = $codecId
            Extension = $ext
        }
    }

    & $MkvExtract @extractArgs

    foreach ($output in $outputs) {
        if (Test-Path -LiteralPath $output.Path) {
            Write-Host "Extracted: $($output.Path)"
            if ($output.Extension -in @("ass", "ssa")) {
                $srt = Convert-AssToSrt $output.Path
                if ($srt) {
                    Write-Host "Converted to SRT: $srt"
                }
            }
            if ($output.Extension -in @("sup", "sub")) {
                Write-Warning "Image subtitle. OCR is required to make SRT."
            }
        }
    }
}

try {
    $mkvMerge = Find-MkvTool "mkvmerge"
    $mkvExtract = Find-MkvTool "mkvextract"

    $files = @($InputFiles | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($files.Count -eq 0) {
        $files = @(Select-MkvFiles)
    }

    if ($files.Count -eq 0) {
        Write-Host "Canceled."
        exit 0
    }

    foreach ($file in $files) {
        Extract-Subtitles `
            -MkvPath $file `
            -MkvMerge $mkvMerge `
            -MkvExtract $mkvExtract
    }

    Write-Host ""
    Write-Host "Done. Select the generated .srt files in the app."
} catch {
    Write-Error $_
    exit 1
}
