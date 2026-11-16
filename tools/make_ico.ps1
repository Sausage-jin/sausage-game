# 将 touxaing.png 转换为多尺寸 app.ico (16/32/48/256)
Add-Type -AssemblyName System.Drawing
$srcPath = Join-Path $PSScriptRoot "..\image\touxaing.png"
$outPath = Join-Path $PSScriptRoot "..\app.ico"
$src = [System.Drawing.Image]::FromFile($srcPath)
Write-Host "源图: $($src.Width)x$($src.Height)"
$sizes = @(16, 32, 48, 256)
$pngs = @()
foreach ($s in $sizes) {
    $bmp = New-Object System.Drawing.Bitmap($s, $s)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.DrawImage($src, 0, 0, $s, $s)
    $g.Dispose()
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    $pngs += , $ms.ToArray()
}
$src.Dispose()
$fs = [System.IO.File]::Create($outPath)
$bw = New-Object System.IO.BinaryWriter($fs)
$bw.Write([uint16]0)                    # reserved
$bw.Write([uint16]1)                    # type = icon
$bw.Write([uint16]$sizes.Count)         # image count
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $s = $sizes[$i]
    $w = if ($s -ge 256) { [byte]0 } else { [byte]$s }
    $bw.Write($w)                       # width (0=256)
    $bw.Write($w)                       # height
    $bw.Write([byte]0)                  # color count
    $bw.Write([byte]0)                  # reserved
    $bw.Write([uint16]1)                # planes
    $bw.Write([uint16]32)               # bits per pixel
    $bw.Write([uint32]$pngs[$i].Length) # data size
    $bw.Write([uint32]$offset)          # data offset
    $offset += $pngs[$i].Length
}
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $bw.Write($pngs[$i])
}
$bw.Close()
$fs.Close()
Write-Host "已生成: $outPath ($((Get-Item $outPath).Length) 字节)"