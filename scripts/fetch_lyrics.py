#!/usr/bin/env python3
"""
Fetch lyrics from Genius for Headache, Vegyn, and Headache PLZ & Vegyn,
then extract individual lines/couplets and save as quotes.json.

Usage:
    export GENIUS_API_TOKEN="your_token_here"
    python scripts/fetch_lyrics.py

Get a token at: https://genius.com/api-clients
"""

import json
import os
import re
import sys
import time
from pathlib import Path

try:
    import lyricsgenius
except ImportError:
    print("Error: lyricsgenius not installed. Run: pip install lyricsgenius")
    sys.exit(1)


# Artist configurations: (search name, genius artist ID, max_songs)
ARTISTS = [
    ("Headache (PLZ)", 3551967, None),  # Small catalog — fetch all
    ("Vegyn", 991444, 200),  # Large catalog — cap to avoid timeout
]

# Artists whose songs we want (by Genius artist ID).
# Used to filter out songs where Vegyn is just a producer/feature.
ALLOWED_PRIMARY_ARTIST_IDS = {
    3551967,  # Headache (PLZ)
    991444,  # Vegyn
}

# Additional search terms for collaborative projects
COLLAB_SEARCH_TERMS = [
    "Headache PLZ Vegyn",
]

# Section headers to strip from lyrics
SECTION_HEADER_RE = re.compile(
    r"^\[.*?\]$"  # e.g. [Chorus], [Verse 1], [Bridge]
)

# Lines to skip
SKIP_PATTERNS = [
    re.compile(r"^\d+\s*Contributors?", re.IGNORECASE),
    re.compile(r"^You might also like", re.IGNORECASE),
    re.compile(r"^See .* Live", re.IGNORECASE),
    re.compile(r"^Get tickets", re.IGNORECASE),
    re.compile(r"Embed$", re.IGNORECASE),
    re.compile(r"^\d+Embed$", re.IGNORECASE),
    re.compile(r"^Translations", re.IGNORECASE),
    re.compile(r".*Lyrics$"),  # Title line like "Song TitleLyrics"
]

MIN_LINE_LENGTH = 10  # Skip very short lines
MAX_LINE_LENGTH = 200  # Skip absurdly long lines (probably parsing errors)


def get_genius_client():
    """Initialize the Genius API client."""
    token = os.environ.get("GENIUS_API_TOKEN")
    if not token:
        print("Error: GENIUS_API_TOKEN environment variable not set.")
        print("Get a token at: https://genius.com/api-clients")
        sys.exit(1)

    genius = lyricsgenius.Genius(
        token,
        timeout=30,
        retries=3,
        remove_section_headers=False,  # We'll handle this ourselves
        skip_non_songs=True,
        excluded_terms=[
            "Remix",
            "Live",
            "Instrumental",
            "Demo",
            "Skit",
            "Interview",
        ],
    )
    genius.verbose = True
    return genius


def fetch_artist_songs(genius, artist_name, artist_id, max_songs=None):
    """Fetch all songs for a given artist, filtering to primary artist only."""
    print(f"\n{'=' * 60}")
    print(f"Fetching songs for: {artist_name} (ID: {artist_id})")
    if max_songs:
        print(f"  (limited to {max_songs} songs)")
    print(f"{'=' * 60}")

    try:
        artist = genius.search_artist(
            artist_name,
            artist_id=artist_id,
            max_songs=max_songs,
            sort="title",
            get_full_info=True,
        )
        if artist and artist.songs:
            # Filter to only songs where this artist is the primary artist
            filtered = []
            for song in artist.songs:
                primary_id = None
                if hasattr(song, "_body"):
                    primary_id = song._body.get("primary_artist", {}).get("id")
                if primary_id is None or primary_id in ALLOWED_PRIMARY_ARTIST_IDS:
                    filtered.append(song)
                else:
                    print(
                        f"  Skipping (not primary): {song.title} "
                        f"(primary artist ID: {primary_id})"
                    )
            print(
                f"Found {len(filtered)} songs for {artist_name} "
                f"(filtered from {len(artist.songs)})"
            )
            return filtered
        else:
            print(f"No songs found for {artist_name}")
            return []
    except Exception as e:
        print(f"Error fetching {artist_name}: {e}")
        return []


def fetch_collab_songs(genius, search_term):
    """Search for collaborative songs by search term."""
    print(f"\n{'=' * 60}")
    print(f"Searching for collaborative songs: {search_term}")
    print(f"{'=' * 60}")

    songs = []
    page = 1
    while True:
        try:
            results = genius.search_songs(search_term, per_page=20, page=page)
            hits = results.get("hits", [])
            if not hits:
                break

            for hit in hits:
                song_info = hit.get("result", {})
                artist_name = song_info.get("primary_artist", {}).get("name", "")

                # Only include if the artist matches our search
                if (
                    search_term.lower() in artist_name.lower()
                    or "headache" in artist_name.lower()
                    and "vegyn" in artist_name.lower()
                ):
                    song_id = song_info.get("id")
                    if song_id:
                        try:
                            song = genius.search_song(
                                song_info.get("title", ""),
                                artist_name,
                            )
                            if song:
                                songs.append(song)
                                print(f"  Found: {song.title} by {song.artist}")
                            time.sleep(0.5)  # Rate limiting
                        except Exception as e:
                            print(f"  Error fetching song {song_id}: {e}")

            page += 1
            if page > 5:  # Safety limit
                break

        except Exception as e:
            print(f"Error searching '{search_term}': {e}")
            break

    print(f"Found {len(songs)} collab songs for '{search_term}'")
    return songs


def should_skip_line(line):
    """Check if a line should be skipped."""
    stripped = line.strip()

    if not stripped:
        return True

    if len(stripped) < MIN_LINE_LENGTH:
        return True

    if len(stripped) > MAX_LINE_LENGTH:
        return True

    if SECTION_HEADER_RE.match(stripped):
        return True

    for pattern in SKIP_PATTERNS:
        if pattern.match(stripped):
            return True

    return False


def extract_quotes_from_lyrics(lyrics, song_title, artist_name, album_name):
    """Extract individual lines and couplets from lyrics."""
    if not lyrics:
        return []

    quotes = []
    lines = lyrics.split("\n")
    clean_lines = []

    # First pass: clean and filter lines
    for line in lines:
        stripped = line.strip()
        if not should_skip_line(stripped):
            clean_lines.append(stripped)

    # Extract individual lines
    for line in clean_lines:
        quotes.append(
            {
                "text": line,
                "song": song_title,
                "artist": artist_name,
                "album": album_name or "Single",
            }
        )

    # Extract couplets (consecutive pairs)
    for i in range(len(clean_lines) - 1):
        couplet = f"{clean_lines[i]} / {clean_lines[i + 1]}"
        if len(couplet) <= MAX_LINE_LENGTH:
            quotes.append(
                {
                    "text": couplet,
                    "song": song_title,
                    "artist": artist_name,
                    "album": album_name or "Single",
                }
            )

    return quotes


def get_album_name(song):
    """Try to extract album name from song metadata."""
    # song.album from lyricsgenius can be a string or a dict
    album = getattr(song, "album", None)
    if album:
        if isinstance(album, str):
            return album
        if isinstance(album, dict):
            return album.get("name", None)
    # Fallback: try from the raw API body
    if hasattr(song, "_body"):
        album_info = song._body.get("album")
        if album_info and isinstance(album_info, dict):
            return album_info.get("name", None)
    return None


def deduplicate_quotes(quotes):
    """Remove duplicate quotes based on text content."""
    seen = set()
    unique = []
    for quote in quotes:
        key = quote["text"].lower().strip()
        if key not in seen:
            seen.add(key)
            unique.append(quote)
    return unique


def main():
    genius = get_genius_client()
    all_quotes = []
    seen_song_ids = set()

    # Fetch songs for each main artist
    for artist_name, artist_id, max_songs in ARTISTS:
        songs = fetch_artist_songs(genius, artist_name, artist_id, max_songs)
        for song in songs:
            # breakpoint()
            if song.title in seen_song_ids:
                continue
            seen_song_ids.add(song.title)

            album = get_album_name(song)
            quotes = extract_quotes_from_lyrics(
                song.lyrics, song.title, song.artist, album
            )
            all_quotes.extend(quotes)
            print(f"  Extracted {len(quotes)} quotes from: {song.title}")

    # Fetch collaborative songs
    for search_term in COLLAB_SEARCH_TERMS:
        songs = fetch_collab_songs(genius, search_term)
        for song in songs:
            if song.title in seen_song_ids:
                continue
            seen_song_ids.add(song.title)

            album = get_album_name(song)
            quotes = extract_quotes_from_lyrics(
                song.lyrics, song.title, song.artist, album
            )
            all_quotes.extend(quotes)
            print(f"  Extracted {len(quotes)} quotes from: {song.title}")

    # Deduplicate
    all_quotes = deduplicate_quotes(all_quotes)

    # Save as a JS file (window.QUOTES_DATA = [...]) so it works with file:// protocol
    output_path = Path(__file__).parent.parent / "data" / "quotes.js"
    output_path.parent.mkdir(parents=True, exist_ok=True)

    json_str = json.dumps(all_quotes, indent=2, ensure_ascii=False)
    with open(output_path, "w", encoding="utf-8") as f:
        f.write("window.QUOTES_DATA = ")
        f.write(json_str)
        f.write(";\n")

    print(f"\n{'=' * 60}")
    print(f"Done! Saved {len(all_quotes)} quotes to {output_path}")
    print(f"{'=' * 60}")

    # Print summary
    artists = {}
    for q in all_quotes:
        artists[q["artist"]] = artists.get(q["artist"], 0) + 1
    print("\nQuotes per artist:")
    for artist, count in sorted(artists.items()):
        print(f"  {artist}: {count}")


if __name__ == "__main__":
    main()
