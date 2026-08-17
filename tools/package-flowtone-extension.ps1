param(
    [Parameter(Mandatory = $true)]
    [string]$Source,
    [Parameter(Mandatory = $true)]
    [string]$Output
)

$sourcePath = (Resolve-Path -LiteralPath $Source).Path
$manifest = Join-Path $sourcePath "manifest.json"
$main = Join-Path $sourcePath "main.js"
if (-not (Test-Path -LiteralPath $manifest) -or -not (Test-Path -LiteralPath $main)) {
    throw "扩展源码必须包含 manifest.json 和 main.js"
}

$outputPath = [System.IO.Path]::GetFullPath($Output)
$outputDirectory = [System.IO.Path]::GetDirectoryName($outputPath)
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
$temporaryZip = [System.IO.Path]::ChangeExtension($outputPath, ".tmp.zip")
if (Test-Path -LiteralPath $temporaryZip) { Remove-Item -LiteralPath $temporaryZip }
Compress-Archive -LiteralPath $manifest, $main -DestinationPath $temporaryZip -CompressionLevel Optimal
Move-Item -LiteralPath $temporaryZip -Destination $outputPath -Force
Write-Output $outputPath
