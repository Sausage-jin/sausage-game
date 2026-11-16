from PIL import Image, ImageDraw, ImageFont

SRC = r"E:\A=studay\youxi\1\image\blackground.jpg"
OUT = r"E:\A=studay\youxi\1\image\blackground_sausage.jpg"
TEXT = "sausage.v.1.0.09"

im = Image.open(SRC).convert("RGB")
W, H = im.size
d = ImageDraw.Draw(im)

font_path = r"C:\Windows\Fonts\arial.ttf"
size = 40
font = ImageFont.truetype(font_path, size)

# 左下角定位，距边留白
x, y = 36, H - size - 36

# 纯黑色字体
d.text((x, y), TEXT, font=font, fill=(0, 0, 0))

im.save(OUT, quality=95)
print("OK", OUT, im.size, "text at", (x, y))
