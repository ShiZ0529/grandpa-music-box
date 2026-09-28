#!/usr/bin/env python3
"""Generate legacy launcher PNG icons (Android 7.x) with zero dependencies."""
import os
import struct
import zlib

ORANGE = (245, 124, 0)      # #F57C00
WHITE = (255, 255, 255)
CLEAR = (0, 0, 0, 0)

SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")


def rounded_rect_mask(x, y, s, radius):
    """True if (x,y) is inside a rounded square of size s."""
    if x < radius and y < radius:
        return (x - radius) ** 2 + (y - radius) ** 2 <= radius * radius
    if x >= s - radius and y < radius:
        return (x - (s - 1 - radius)) ** 2 + (y - radius) ** 2 <= radius * radius
    if x < radius and y >= s - radius:
        return (x - radius) ** 2 + (y - (s - 1 - radius)) ** 2 <= radius * radius
    if x >= s - radius and y >= s - radius:
        return (x - (s - 1 - radius)) ** 2 + (y - (s - 1 - radius)) ** 2 <= radius * radius
    return True


def in_ellipse(x, y, cx, cy, rx, ry):
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def draw_icon(size):
    px = [[CLEAR] * size for _ in range(size)]
    radius = int(size * 0.20)
    for y in range(size):
        for x in range(size):
            if not rounded_rect_mask(x, y, size, radius):
                continue
            # white music note: head (ellipse) + stem + flag
            head_cx, head_cy = size * 0.40, size * 0.68
            head_rx, head_ry = size * 0.145, size * 0.115
            stem_x0, stem_x1 = size * 0.51, size * 0.585
            stem_y0, stem_y1 = size * 0.16, size * 0.70
            flag_x0, flag_x1 = size * 0.585, size * 0.78
            flag_y0, flag_y1 = size * 0.16, size * 0.36

            on_note = False
            if in_ellipse(x, y, head_cx, head_cy, head_rx, head_ry):
                on_note = True
            elif stem_x0 <= x <= stem_x1 and stem_y0 <= y <= stem_y1:
                on_note = True
            elif flag_x0 <= x <= flag_x1 and flag_y0 <= y <= flag_y1:
                # slanted flag: cut off the lower-left corner
                t = (y - flag_y0) / (flag_y1 - flag_y0)
                if x >= flag_x0 + t * (flag_x1 - flag_x0) * 0.55:
                    on_note = True
            px[y][x] = WHITE + (255,) if on_note else ORANGE + (255,)
    return px


def write_png(path, px):
    h = len(px)
    w = len(px[0])
    raw = b""
    for row in px:
        raw += b"\x00" + b"".join(struct.pack("4B", *p) for p in row)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        c += struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        return c

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def main():
    for dpi, size in SIZES.items():
        out_dir = os.path.normpath(os.path.join(RES, "mipmap-" + dpi))
        os.makedirs(out_dir, exist_ok=True)
        out = os.path.join(out_dir, "ic_launcher.png")
        write_png(out, draw_icon(size))
        print("wrote", out, size, "x", size)


if __name__ == "__main__":
    main()
