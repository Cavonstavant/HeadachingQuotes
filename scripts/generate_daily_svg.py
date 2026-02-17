#!/usr/bin/env python3
"""
Generate a daily quote SVG file (quotes/daily.svg).

Uses the same deterministic hash as js/app.js to pick the daily quote,
so the SVG always matches what the website displays.

Usage:
    python scripts/generate_daily_svg.py
"""

import ctypes
import json
import re
import textwrap
from datetime import datetime, timezone
from pathlib import Path

# ── Configuration ──────────────────────────────────────────────

SVG_WIDTH = 800
SVG_PADDING_X = 60
SVG_PADDING_TOP = 60
SVG_PADDING_BOTTOM = 50

# Quote text styling
QUOTE_FONT = "Helvetica Neue, Helvetica, Arial, sans-serif"
QUOTE_FONT_SIZE = 28
QUOTE_LINE_HEIGHT = 1.35
QUOTE_COLOR = "#000000"
QUOTE_WRAP_WIDTH = 42  # chars per line (approximate for the font size)

# Credit line styling
CREDIT_FONT = "Courier New, Courier, monospace"  # monospace fallback for Departure Mono
CREDIT_FONT_SIZE = 11
CREDIT_COLOR = "#666666"
CREDIT_MARGIN_TOP = 32

# Background
BG_COLOR = "#ffffff"


# ── Hash function (must match js/app.js exactly) ──────────────


def hash_string(s: str) -> int:
    """
    Replicate the JS hash function:
        hash = 0
        for char in str:
            hash = (hash << 5) - hash + charCode
            hash |= 0   // force 32-bit signed integer
        return Math.abs(hash)
    """
    h = 0
    for ch in s:
        code = ord(ch)
        # (hash << 5) - hash + char, forced to 32-bit signed int
        h = ctypes.c_int32((h << 5) - h + code).value
    return abs(h)


def today_string() -> str:
    """UTC date string in YYYY-MM-DD format."""
    d = datetime.now(timezone.utc)
    return f"{d.year}-{d.month:02d}-{d.day:02d}"


def get_daily_index(num_quotes: int) -> int:
    """Pick the daily quote index (same logic as app.js)."""
    return hash_string(today_string()) % num_quotes


# ── SVG generation ────────────────────────────────────────────


def escape_xml(s: str) -> str:
    """Escape special XML characters."""
    return (
        s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "&apos;")
    )


def wrap_quote_text(text: str, width: int) -> list[str]:
    """
    Wrap quote text into lines. Respects existing ' / ' couplet separators.
    """
    # If the quote has a couplet separator, split there first
    if " / " in text:
        parts = text.split(" / ")
        lines = []
        for part in parts:
            lines.extend(textwrap.wrap(part, width=width))
        return lines
    return textwrap.wrap(text, width=width)


def generate_svg(quote: dict) -> str:
    """Generate an SVG string for a single quote."""
    text = quote["text"]
    song = quote["song"]
    artist = quote["artist"]
    album = quote.get("album", "")

    # Wrap the quote text
    lines = wrap_quote_text(text, QUOTE_WRAP_WIDTH)

    # Calculate dimensions
    quote_line_h = QUOTE_FONT_SIZE * QUOTE_LINE_HEIGHT
    quote_block_h = quote_line_h * len(lines)
    credit_y_offset = CREDIT_MARGIN_TOP

    # Credit lines
    credit_line_1 = f"{song}  \u2014  {artist}"
    credit_line_2 = album if album else ""
    credit_block_h = CREDIT_FONT_SIZE * 1.6 * (2 if credit_line_2 else 1)

    total_content_h = quote_block_h + credit_y_offset + credit_block_h
    svg_height = int(SVG_PADDING_TOP + total_content_h + SVG_PADDING_BOTTOM)

    # Build quote tspans
    quote_start_y = SVG_PADDING_TOP + QUOTE_FONT_SIZE
    quote_tspans = []
    for i, line in enumerate(lines):
        prefix = "\u201c" if i == 0 else ""
        suffix = "\u201d" if i == len(lines) - 1 else ""
        y = quote_start_y + i * quote_line_h
        quote_tspans.append(
            f'    <tspan x="{SVG_PADDING_X}" y="{y:.0f}">'
            f"{escape_xml(prefix + line + suffix)}</tspan>"
        )
    quote_tspans_str = "\n".join(quote_tspans)

    # Credit position
    credit_base_y = quote_start_y + quote_block_h + credit_y_offset
    credit_tspans = (
        f'    <tspan x="{SVG_PADDING_X}" y="{credit_base_y:.0f}">'
        f"{escape_xml(credit_line_1)}</tspan>"
    )
    if credit_line_2:
        credit_line_2_y = credit_base_y + CREDIT_FONT_SIZE * 1.6
        credit_tspans += (
            f'\n    <tspan x="{SVG_PADDING_X}" y="{credit_line_2_y:.0f}">'
            f"{escape_xml(credit_line_2)}</tspan>"
        )

    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" width="{SVG_WIDTH}" height="{svg_height}" viewBox="0 0 {SVG_WIDTH} {svg_height}">
  <rect width="100%" height="100%" fill="{BG_COLOR}"/>
  <text
    font-family="{QUOTE_FONT}"
    font-size="{QUOTE_FONT_SIZE}"
    fill="{QUOTE_COLOR}"
    font-weight="400"
    letter-spacing="-0.01em">
{quote_tspans_str}
  </text>
  <text
    font-family="{CREDIT_FONT}"
    font-size="{CREDIT_FONT_SIZE}"
    fill="{CREDIT_COLOR}"
    font-weight="400"
    text-transform="uppercase"
    letter-spacing="0.1em">
{credit_tspans}
  </text>
</svg>
"""
    return svg


# ── Main ──────────────────────────────────────────────────────


def main():
    # Load quotes
    quotes_path = Path(__file__).parent.parent / "data" / "quotes.js"
    content = quotes_path.read_text(encoding="utf-8")

    # Strip JS wrapper: "window.QUOTES_DATA = [...];\n"
    json_str = content.replace("window.QUOTES_DATA = ", "").rstrip().rstrip(";")
    quotes = json.loads(json_str)

    if not quotes:
        print("Error: No quotes found in data/quotes.js")
        return

    # Pick today's quote
    date_str = today_string()
    index = get_daily_index(len(quotes))
    quote = quotes[index]

    print(f"Date:  {date_str}")
    print(f"Index: {index} / {len(quotes)}")
    print(f"Quote: \u201c{quote['text']}\u201d")
    print(f"Song:  {quote['song']} \u2014 {quote['artist']}")
    print(f"Album: {quote.get('album', 'N/A')}")

    # Generate SVG
    svg = generate_svg(quote)

    # Write output
    output_dir = Path(__file__).parent.parent / "quotes"
    output_dir.mkdir(exist_ok=True)
    output_path = output_dir / "daily.svg"
    output_path.write_text(svg, encoding="utf-8")

    print(f"\nSaved: {output_path} ({len(svg)} bytes)")


if __name__ == "__main__":
    main()
