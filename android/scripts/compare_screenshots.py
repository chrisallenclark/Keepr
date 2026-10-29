#!/usr/bin/env python3
"""Puts each Android screenshot beside its iOS reference.

iOS screenshots come from the iOS workflow (`screenshots` artifact); Android
ones from the emulator jobs. The mapping below says which iOS screen each
Android capture should be judged against. Composites are written at full size
for people, and optionally echoed into the log as small base64 JPEGs so a
reviewer without artifact access can still see them.

Usage: compare_screenshots.py IOS_DIR ANDROID_DIR OUT_DIR [--print GLOB...]
"""
import base64
import fnmatch
import io
import sys
from pathlib import Path

from PIL import Image, ImageDraw

# Android capture name (without device prefix) -> iOS reference name.
# Captures with no iOS counterpart are still composited, alone.
REFERENCE = {
    "today-business": "today-business",
    "people-business": "people-business",
    "followUp-business": "followUp-business",
    "search-business": "search-business",
    "profile-business": "profile-business",
    "today-personal": "today-personal",
}

# Full-size enough to judge type and spacing, small enough to move around.
HEIGHT = 1000
CHUNK = 16000


def fit(image: Image.Image, height: int) -> Image.Image:
    width = round(image.width * height / image.height)
    return image.convert("RGB").resize((width, height), Image.LANCZOS)


def label(image: Image.Image, text: str) -> Image.Image:
    bar = 44
    out = Image.new("RGB", (image.width, image.height + bar), (128, 128, 128))
    out.paste(image, (0, bar))
    ImageDraw.Draw(out).text((12, 12), text, fill=(255, 255, 255))
    return out


def reference_for(stem: str, ios_dir: Path):
    # stem looks like "<device>-<screen>-<context>-<appearance>"
    parts = stem.split("-")
    if len(parts) < 4:
        return None
    appearance = parts[-1]
    key = "-".join(parts[1:-1])
    target = REFERENCE.get(key)
    if target is None:
        return None
    path = ios_dir / f"{target}-{appearance}.png"
    return path if path.exists() else None


def emit(name: str, image: Image.Image, width: int) -> None:
    small = image.resize((width, round(image.height * width / image.width)), Image.LANCZOS)
    buffer = io.BytesIO()
    small.save(buffer, "JPEG", quality=70, optimize=True)
    data = base64.b64encode(buffer.getvalue()).decode()
    parts = [data[i:i + CHUNK] for i in range(0, len(data), CHUNK)]
    for index, part in enumerate(parts):
        print(f"@@IMG {name} {index + 1}/{len(parts)} {part}")


def main() -> int:
    ios_dir, android_dir, out_dir = (Path(arg) for arg in sys.argv[1:4])
    patterns = sys.argv[sys.argv.index("--print") + 1:] if "--print" in sys.argv else []
    out_dir.mkdir(parents=True, exist_ok=True)

    written = []
    for shot in sorted(android_dir.rglob("*.png")):
        android = label(fit(Image.open(shot), HEIGHT), f"Android  {shot.stem}")
        ref_path = reference_for(shot.stem, ios_dir)
        if ref_path:
            ios = label(fit(Image.open(ref_path), HEIGHT), f"iOS  {ref_path.stem}")
            canvas = Image.new("RGB", (ios.width + android.width + 24, android.height), (128, 128, 128))
            canvas.paste(ios, (0, 0))
            canvas.paste(android, (ios.width + 24, 0))
        else:
            canvas = android
        canvas.save(out_dir / f"{shot.stem}.png")
        written.append((shot.stem, canvas))

    # The iOS references on their own, so they can be studied before an
    # Android counterpart exists.
    for ref in sorted(ios_dir.glob("*.png")):
        image = label(fit(Image.open(ref), HEIGHT), f"iOS  {ref.stem}")
        image.save(out_dir / f"ios-{ref.stem}.png")
        written.append((f"ios-{ref.stem}", image))

    for name, image in written:
        if any(fnmatch.fnmatch(name, pattern) for pattern in patterns):
            emit(name, image, 330 if image.width < 900 else 640)

    print(f"Wrote {len(written)} images to {out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
