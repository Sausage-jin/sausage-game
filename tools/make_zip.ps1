# Create distribution zip from dist/ folder
$distDir = Get-ChildItem -Path "dist" -Directory | Select-Object -First 1
if (-not $distDir) { Write-Host "ERROR: no dist folder"; exit 1 }
$zipName = $distDir.Name + ".zip"
$zipPath = Join-Path "dist" $zipName
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
Write-Host "Zipping $($distDir.FullName) -> $zipPath ..."
Compress-Archive -Path $distDir.FullName -DestinationPath $zipPath -CompressionLevel Optimal
$size = (Get-Item $zipPath).Length / 1MB
Write-Host ("Done: {0} ({1:N1} MB)" -f $zipName, $size)