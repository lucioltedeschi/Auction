from pathlib import Path
from PIL import Image

folder = Path("build/manual_render")
pages = sorted(folder.glob("page-*.png"))
for group_index in range(0, len(pages), 4):
    images = []
    for page in pages[group_index:group_index + 4]:
        image = Image.open(page).convert("RGB")
        images.append(image.resize((int(image.width * .48), int(image.height * .48))))
    width = max(image.width for image in images)
    height = max(image.height for image in images)
    canvas = Image.new("RGB", (width * 2, height * 2), "#D1D5DB")
    for index, image in enumerate(images):
        canvas.paste(image, ((index % 2) * width, (index // 2) * height))
    canvas.save(folder / f"contact-{group_index // 4 + 1}.png")
