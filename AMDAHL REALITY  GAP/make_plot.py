from __future__ import annotations

import csv
import struct
import sys
import zlib
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError as exc:
    raise SystemExit("Pillow is required. Install with: python -m pip install pillow") from exc


BASE_DIR = Path(__file__).resolve().parent
CSV_PATH = BASE_DIR / "results.csv"
OUT_PATH = BASE_DIR / "speedup_plot.png"


def read_phase3_points():
    points = []
    with CSV_PATH.open(newline="", encoding="utf-8") as handle:
        for row in csv.DictReader(handle):
            if row["section"] != "phase3":
                continue
            points.append(
                {
                    "threads": int(row["threads"]),
                    "s_emp": float(row["s_emp"]),
                    "s_theo": float(row["s_theo"]),
                    "linear": float(row["threads"]),
                }
            )
    return sorted(points, key=lambda item: item["threads"])


def nice_font(size):
    for name in ("arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def xy(point, max_threads, max_speedup, left, top, width, height, key):
    x = left + (point["threads"] / max_threads) * width
    y = top + height - (point[key] / max_speedup) * height
    return int(round(x)), int(round(y))


def draw_series(draw, points, key, color, label, max_threads, max_speedup, box, font):
    left, top, width, height = box
    coords = [xy(p, max_threads, max_speedup, left, top, width, height, key) for p in points]
    if len(coords) > 1:
        draw.line(coords, fill=color, width=3)
    for x, y in coords:
        draw.ellipse((x - 5, y - 5, x + 5, y + 5), fill=color)

    legend_x = left + width - 210
    legend_y = top + 20 + draw_series.legend_offset
    draw.line((legend_x, legend_y + 8, legend_x + 34, legend_y + 8), fill=color, width=3)
    draw.ellipse((legend_x + 13, legend_y + 3, legend_x + 23, legend_y + 13), fill=color)
    draw.text((legend_x + 44, legend_y), label, fill=(30, 30, 30), font=font)
    draw_series.legend_offset += 26


draw_series.legend_offset = 0


def main():
    if not CSV_PATH.exists():
        raise SystemExit(f"{CSV_PATH} does not exist. Run collatz first.")

    points = read_phase3_points()
    if not points:
        raise SystemExit("No phase3 rows found in results.csv.")

    width, height = 1100, 760
    left, top = 105, 90
    plot_w, plot_h = 860, 520
    img = Image.new("RGB", (width, height), (248, 249, 250))
    draw = ImageDraw.Draw(img)
    title_font = nice_font(28)
    label_font = nice_font(17)
    small_font = nice_font(14)

    max_threads = max(p["threads"] for p in points)
    max_speedup = max(max(p["linear"], p["s_theo"], p["s_emp"]) for p in points) * 1.08
    max_speedup = max(2.0, max_speedup)

    draw.rectangle((left, top, left + plot_w, top + plot_h), fill=(255, 255, 255), outline=(200, 205, 210))
    draw.text((left, 28), "Collatz OpenMP Speedup: Empirical vs Amdahl vs Linear Ideal", fill=(25, 35, 45), font=title_font)

    for i in range(0, 6):
        y_value = max_speedup * i / 5
        y = top + plot_h - (y_value / max_speedup) * plot_h
        draw.line((left, y, left + plot_w, y), fill=(225, 229, 233), width=1)
        draw.text((20, y - 9), f"{y_value:.1f}x", fill=(70, 76, 82), font=small_font)

    for point in points:
        x = left + (point["threads"] / max_threads) * plot_w
        draw.line((x, top, x, top + plot_h), fill=(235, 238, 241), width=1)
        draw.text((x - 10, top + plot_h + 12), str(point["threads"]), fill=(70, 76, 82), font=small_font)

    draw.line((left, top + plot_h, left + plot_w, top + plot_h), fill=(80, 85, 90), width=2)
    draw.line((left, top, left, top + plot_h), fill=(80, 85, 90), width=2)
    draw.text((left + plot_w // 2 - 55, height - 72), "Threads (k)", fill=(25, 35, 45), font=label_font)
    draw.text((18, top - 36), "Speedup", fill=(25, 35, 45), font=label_font)

    box = (left, top, plot_w, plot_h)
    draw_series.legend_offset = 0
    draw_series(draw, points, "s_emp", (17, 121, 191), "S_emp(k)", max_threads, max_speedup, box, label_font)
    draw_series(draw, points, "s_theo", (202, 91, 38), "S_theo(k)", max_threads, max_speedup, box, label_font)
    draw_series(draw, points, "linear", (75, 160, 95), "Linear ideal", max_threads, max_speedup, box, label_font)

    footer = "Generated from results.csv. Use real benchmark runs only; do not hand-edit timing values."
    draw.text((left, height - 34), footer, fill=(95, 100, 105), font=small_font)
    img.save(OUT_PATH)
    print(f"Wrote {OUT_PATH}")


if __name__ == "__main__":
    main()
