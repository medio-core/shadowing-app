# Play Console 用 512x512 アイコン PNG を生成する。
Add-Type -AssemblyName System.Drawing

$outPath = Join-Path $PSScriptRoot "..\docs\play-store\icon-512.png"
$size = 512
$bmp = New-Object System.Drawing.Bitmap $size, $size
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::FromArgb(255, 37, 99, 235))

$white = [System.Drawing.Brushes]::White
$play = @(
    [System.Drawing.Point]::new(180, 140),
    [System.Drawing.Point]::new(360, 256),
    [System.Drawing.Point]::new(180, 372)
)
$g.FillPolygon($white, $play)
$g.FillRectangle($white, 120, 400, 272, 28)
$g.FillRectangle($white, 148, 440, 216, 22)

$g.Dispose()
$dir = Split-Path $outPath -Parent
if (-not (Test-Path $dir)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}
$bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

$info = Get-Item $outPath
Write-Output "Saved: $outPath"
Write-Output "Bytes: $($info.Length)"
$check = [System.Drawing.Image]::FromFile($outPath)
Write-Output "Pixels: $($check.Width) x $($check.Height)"
$check.Dispose()
