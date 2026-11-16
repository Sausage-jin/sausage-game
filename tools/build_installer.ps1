$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
$netDir = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319"
$pkgDir = Join-Path $root "pkg"
$distDir = Join-Path $root "dist"
New-Item -ItemType Directory -Force -Path $pkgDir, $distDir | Out-Null

Write-Host "== 1/4 编译 uninstall.exe =="
& $csc /nologo /codepage:65001 /target:winexe /out:"$pkgDir\uninstall.exe" `
    /r:System.Windows.Forms.dll /r:System.Drawing.dll `
    "$PSScriptRoot\Uninstaller.cs"
if ($LASTEXITCODE -ne 0) { throw "uninstall.exe 编译失败" }
Write-Host "   OK"

Write-Host "== 2/4 编译 Installer.exe =="
& $csc /nologo /codepage:65001 /target:winexe /win32icon:"$root\app.ico" `
    /out:"$pkgDir\Installer.exe" `
    /r:System.Windows.Forms.dll /r:System.Drawing.dll `
    /r:"$netDir\System.IO.Compression.dll" `
    /r:"$netDir\System.IO.Compression.FileSystem.dll" `
    "$PSScriptRoot\Installer.cs"
if ($LASTEXITCODE -ne 0) { throw "Installer.exe 编译失败" }
Write-Host "   OK"

Write-Host "== 3/4 构建 payload.zip =="
$payloadZip = Join-Path $pkgDir "payload.zip"
if (Test-Path $payloadZip) { Remove-Item $payloadZip -Force }
Compress-Archive -CompressionLevel Fastest -DestinationPath $payloadZip -Path @(
    "$root\runtime",
    "$root\game.jar",
    "$root\settings.properties",
    "$root\app.ico",
    "$root\image",
    "$root\music",
    "$pkgDir\uninstall.exe"
)
$zipSize = (Get-Item $payloadZip).Length
Write-Host "   OK ($([math]::Round($zipSize/1MB,1)) MB)"

Write-Host "== 4/4 拼接 SFX 安装程序 =="
$outExe = Join-Path $distDir "烂头背枪-安装程序.exe"
$fs = [System.IO.File]::Create($outExe)
$instBytes = [System.IO.File]::ReadAllBytes("$pkgDir\Installer.exe")
$fs.Write($instBytes, 0, $instBytes.Length)
$zipBytes = [System.IO.File]::ReadAllBytes($payloadZip)
$fs.Write($zipBytes, 0, $zipBytes.Length)
$sizeBytes = [BitConverter]::GetBytes([int64]$zipBytes.Length)
$fs.Write($sizeBytes, 0, 8)
$fs.Close()
$totalMB = [math]::Round((Get-Item $outExe).Length / 1MB, 1)
Write-Host "安装程序已生成: $outExe ($totalMB MB)"