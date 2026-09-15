import os
from PIL import Image

out_dir = os.path.join('app', 'desktopApp', 'src', 'main', 'resources')
os.makedirs(out_dir, exist_ok=True)

src_path = r'C:\Users\rushikesh\.gemini\antigravity-ide\brain\a30c7f5e-7fe2-4730-b987-5efa827bfb60\railandaz_app_logo_1789203522484.jpg'
out_path = os.path.join(out_dir, 'icon.ico')

img = Image.open(src_path)
# Ensure square
size = max(img.size)
square_img = Image.new('RGBA', (size, size), (11, 25, 44, 255))
offset = ((size - img.width) // 2, (size - img.height) // 2)
square_img.paste(img, offset)

square_img.save(out_path, format='ICO', sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)])
print("Successfully generated icon at:", out_path)
