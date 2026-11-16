Add-Type -AssemblyName System.Drawing
$src = 'e:\A=studay\youxi\1\2\image'
$dst = 'e:\A=studay\youxi\1\image\poker_deck'
# 清空旧 png
Get-ChildItem $dst -Filter '*.png' | Remove-Item -Force
New-Item -ItemType Directory -Force -Path $dst | Out-Null
$tw = 210; $th = 315
$jpegCodec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$ep = New-Object System.Drawing.Imaging.EncoderParameters(1)
$ep.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, [long]90)
$count = 0
Get-ChildItem $src -Filter '*.png' | ForEach-Object {
    $img = [System.Drawing.Image]::FromFile($_.FullName)
    $bmp = New-Object System.Drawing.Bitmap($tw, $th)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    # 白底（JPEG 无透明）
    $g.Clear([System.Drawing.Color]::White)
    $g.DrawImage($img, 0, 0, $tw, $th)
    $g.Dispose()
    $outName = [System.IO.Path]::GetFileNameWithoutExtension($_.Name) + '.jpg'
    $bmp.Save((Join-Path $dst $outName), $jpegCodec, $ep)
    $bmp.Dispose()
    $img.Dispose()
    $count++
}
$ep.Dispose()
Write-Host "Done: $count jpg images"