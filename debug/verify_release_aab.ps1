param(
    [string]$BundlePath = "app\build\outputs\bundle\release\app-release.aab"
)

$ErrorActionPreference = "Stop"

$resolvedBundle = (Resolve-Path -LiteralPath $BundlePath).Path
$javaHome = if ($env:JAVA_HOME) {
    $env:JAVA_HOME
} else {
    "C:\Program Files\Android\Android Studio\jbr"
}
$jar = Join-Path $javaHome "bin\jar.exe"
$jarsigner = Join-Path $javaHome "bin\jarsigner.exe"

foreach ($tool in @($jar, $jarsigner)) {
    if (-not (Test-Path -LiteralPath $tool)) {
        throw "必要なJavaツールが見つかりません: $tool"
    }
}

$entries = & $jar tf $resolvedBundle
if ($LASTEXITCODE -ne 0) {
    throw "AABの内容一覧を取得できませんでした: $resolvedBundle"
}

$signatureEntries = @(
    $entries | Where-Object { $_ -match '^META-INF/.+\.(RSA|DSA|EC)$' }
)
if ($signatureEntries.Count -eq 0) {
    [Console]::Error.WriteLine(
        "AABに署名エントリがありません。Playへアップロードしないでください: $resolvedBundle"
    )
    exit 2
}

& $jarsigner -verify $resolvedBundle | Out-Host
if ($LASTEXITCODE -ne 0) {
    [Console]::Error.WriteLine("AABの署名検証に失敗しました: $resolvedBundle")
    exit 3
}

$bundle = Get-Item -LiteralPath $resolvedBundle
$sha256 = (Get-FileHash -LiteralPath $resolvedBundle -Algorithm SHA256).Hash
Write-Output "署名エントリ数: $($signatureEntries.Count)"
Write-Output "サイズ: $($bundle.Length) bytes"
Write-Output "SHA-256: $sha256"
