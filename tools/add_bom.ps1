# Convert ps1 files under dist/ to UTF-8 with BOM
Get-ChildItem -Path "dist" -Recurse -Filter "*.ps1" | ForEach-Object {
    $bytes = [IO.File]::ReadAllBytes($_.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        Write-Host "Already BOM: $($_.Name)"
        return
    }
    $text = [Text.Encoding]::UTF8.GetString($bytes)
    [IO.File]::WriteAllText($_.FullName, $text, [Text.UTF8Encoding]::new($true))
    Write-Host "BOM added: $($_.Name)"
}