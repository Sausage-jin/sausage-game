Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$exe = Get-ChildItem "dist\*.exe" | Select-Object -First 1
if ($exe -eq $null) { Write-Host "未找到 exe"; exit 1 }
$f = [System.IO.File]::ReadAllBytes($exe.FullName)
$n = $f.Length
$sz = [BitConverter]::ToInt64($f, $n - 8)
$off = $n - 8 - $sz
$pk = ($f[$off] -eq 0x50 -and $f[$off + 1] -eq 0x4B)
$ok = ($off + $sz + 8 -eq $n)
Write-Host "文件: $($exe.Name)"
Write-Host "总大小: $([math]::Round($n/1MB,1)) MB"
Write-Host "ZIP大小: $([math]::Round($sz/1MB,1)) MB"
Write-Host "PK签名: $pk"
Write-Host "结构校验: $ok"
# 验证 ZIP 可打开且含关键文件
$ms = New-Object System.IO.MemoryStream($f, $off, [int]$sz)
$zip = New-Object System.IO.Compression.ZipArchive($ms, [System.IO.Compression.ZipArchiveMode]::Read)
$need = @("game.jar", "settings.properties", "app.ico", "uninstall.exe")
foreach ($name in $need) {
    $e = $zip.GetEntry($name)
    Write-Host ("  " + $name + ": " + $(if ($e -ne $null) { "OK" } else { "缺失" }))
}
Write-Host "  runtime 条目示例:"
$zip.Entries | Where-Object { $_.FullName -like "*javaw*" -or $_.FullName -like "*java.exe*" } | ForEach-Object {
    Write-Host ("    " + $_.FullName + " (" + $_.Length + " bytes)")
}
$runtimeCount = ($zip.Entries | Where-Object { $_.FullName -like "runtime*" }).Count
Write-Host "  runtime 总条目数: $runtimeCount"
$zip.Dispose()
$ms.Dispose()