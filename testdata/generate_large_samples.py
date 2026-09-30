#!/usr/bin/env python3
"""Deterministic large-sample generator for Leaflet reader tests.

Generates exact-size fixtures (no randomness, no timestamps in output):

  large-many-<tier>.md        many small blocks (headings/paragraphs/lists/
                              quotes/tables/short code blocks, CJK + emoji)
  large-single-para-<tier>.md one giant paragraph (+ title heading only)
  large-single-code-<tier>.md one giant fenced code block (+ title heading only)
  large-1m.txt                1 MiB plain-text sample (TXT reading path)

Tiers (exact bytes, KiB-based): 100k=102400, 1m=1048576, 5m=5242880, 10m=10485760.

Usage:
  python3 testdata/generate_large_samples.py [generate] [--out DIR]
  python3 testdata/generate_large_samples.py verify [--out DIR]

`generate` writes the files then runs the same checks as `verify`.
Exit status is 0 only if every size/shape/manifest check passes.

Only the Python standard library is used. Re-running produces
byte-identical files (verified by hashing two in-memory generations).
"""

from __future__ import annotations

import argparse
import hashlib
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = Path(__file__).resolve().parent / "generated"

TIERS: dict[str, int] = {
    "100k": 100 * 1024,
    "1m": 1024 * 1024,
    "5m": 5 * 1024 * 1024,
    "10m": 10 * 1024 * 1024,
}

# Fixed 68-byte filler line: "// filler %08d " (19) + 48 x "z" + "\n" (1).
FILLER_LINE_LEN = 68


def filler_line(i: int) -> bytes:
    line = f"// filler {i:08d} " + "z" * 48 + "\n"
    raw = line.encode("ascii")
    assert len(raw) == FILLER_LINE_LEN, len(raw)
    return raw


def pad_comment(length: int) -> bytes:
    """An HTML-comment line of exactly `length` bytes (13 <= length)."""
    assert 13 <= length, length
    raw = ("<!-- " + "p" * (length - 10) + " -->\n").encode("ascii")
    assert len(raw) == length, (len(raw), length)
    return raw


def top_up(parts: list[bytes], target: int) -> bytes:
    """Append deterministic filler so b"".join(parts) is exactly `target` bytes."""
    used = sum(len(p) for p in parts)
    assert used <= target, (used, target)
    remaining = target - used
    n_full = remaining // FILLER_LINE_LEN
    rem = remaining % FILLER_LINE_LEN
    if rem != 0 and rem < 13:
        # A short pad comment would be invalid; trade one full line for room.
        n_full -= 1
        rem += FILLER_LINE_LEN
    base_index = used % 100_000_000  # deterministic, derived from content length
    for i in range(n_full):
        parts.append(filler_line(base_index + i))
    if rem:
        parts.append(pad_comment(rem))
    out = b"".join(parts)
    assert len(out) == target, (len(out), target)
    return out


# ---------------------------------------------------------------- many blocks

_MANY_CYCLE = """\
## Block {n:06d} — heading mix 中文 見出し 📚

Paragraph {n:06d}: Lorem ipsum dolor sit amet, consectetur adipiscing elit. \
Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua {n:06d}. \
床前明月光，疑是地上霜。古池や蛙飛び込む水の音。Emoji {n:06d}: 📖✅➡️.

- item a-{n:06d} with **bold**
- item b-{n:06d} with `code`
  - nested c-{n:06d}

> Quote {n:06d}:学而时习之，不亦说乎。

| key-{n:06d} | value |
| ---- | ----- |
| k{n:06d} | v{n:06d} |

```kotlin
fun block{n:06d}(): String = "answer-{n:06d}"
```

- [ ] task open {n:06d}
- [x] task done {n:06d}

"""


def build_many(target: int) -> bytes:
    header = (
        "# Large many-blocks fixture (deterministic)\n\n"
        "Thousands of small blocks. Exercises incremental parsing, scroll\n"
        "stability, TOC length, search fan-out, and scan time with no single\n"
        "pathological block. All content is generated or hand-written for this\n"
        "repo; classical one-line quotations used are public-domain works.\n\n"
    ).encode("utf-8")
    parts = [header]
    n = 0
    # Leave room for at least the filler math: keep appending whole cycle
    # blocks while another full cycle plus a minimal tail still fits.
    while True:
        block = _MANY_CYCLE.format(n=n).encode("utf-8")
        if sum(len(p) for p in parts) + len(block) + 13 > target:
            break
        parts.append(block)
        n += 1
    assert n > 0, "target too small for one cycle block"
    return top_up(parts, target)


# ------------------------------------------------------- single giant shapes

_PARA_SENTENCE = (
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod "
    "tempor incididunt ut labore et dolore magna aliqua {n:06d}. "
)


def build_single_para(target: int) -> bytes:
    header = (
        "# Large single-paragraph fixture (deterministic)\n\n"
        "Exactly one giant paragraph follows. The reader must bound it "
        "(split, window, or plain-text fallback) instead of laying out one "
        "unbounded block; a visible loading/fallback notice is expected.\n\n"
    ).encode("utf-8")
    body_target = target - len(header) - 1  # trailing newline
    assert body_target > 0
    chunks: list[bytes] = []
    n = 0
    total = 0
    while True:
        s = _PARA_SENTENCE.format(n=n % 1_000_000).encode("ascii")
        if total + len(s) > body_target:
            break
        chunks.append(s)
        total += len(s)
        n += 1
    tail = body_target - total
    if tail:
        # Repeat the alphabet pattern; ASCII so any cut point stays valid UTF-8.
        pattern = b"abcdefghijklmnopqrstuvwxyz0123456789 "
        chunks.append((pattern * (tail // len(pattern) + 1))[:tail])
    body = b"".join(chunks)
    assert len(body) == body_target
    # Single line: no newline inside the paragraph body.
    assert b"\n" not in body
    return header + body + b"\n"


def build_single_code(target: int) -> bytes:
    header = (
        "# Large single-code-block fixture (deterministic)\n\n"
        "Exactly one giant fenced block follows. E-Ink defaults to soft-wrap; "
        "with wrap off the reader must bound it with horizontal scroll.\n\n"
        "```text\n"
    ).encode("utf-8")
    footer = b"```\n"
    body_target = target - len(header) - len(footer)
    assert body_target > 0
    # Very long lines (~1024 chars) to stress wrap/scroll paths.
    chunks: list[bytes] = []
    total = 0
    i = 0
    while True:
        line = (
            f"{i:08d}-" + "ABCDEF0123456789" * 63 + f"-{i:08d}\n"
        ).encode("ascii")
        assert len(line) == 8 + 1 + 1008 + 1 + 8 + 1, len(line)
        if total + len(line) > body_target:
            break
        chunks.append(line)
        total += len(line)
        i += 1
    tail = body_target - total
    if tail:
        chunks.append(b"x" * (tail - 1) + b"\n" if tail > 1 else b"x")
        if tail == 1:
            pass  # single trailing char, still inside the fence
    body = b"".join(chunks)
    # Keep the fence intact: body may not contain a fence line.
    assert b"\n```" not in body
    out = header + body + footer
    assert len(out) == target, (len(out), target)
    return out


# ------------------------------------------------------------------ large TXT

_TXT_PARA = (
    "Plain-text paragraph {n:06d}: no Markdown here, just lines separated by "
    "blank lines. Lorem ipsum dolor sit amet {n:06d}. "
    "纯文本段落用于 TXT 路径测试。プレーンテキストの段落。Emoji: 📚.\n"
)


def build_txt(target: int) -> bytes:
    header = (
        "Large plain-text fixture (deterministic).\n"
        "TXT path must never parse Markdown; '#' and '-' stay literal.\n\n"
        "# Not A Heading In TXT Mode\n\n"
        "- not a list, just a dash line\n\n"
    ).encode("utf-8")
    parts = [header]
    n = 0
    while True:
        block = (_TXT_PARA.format(n=n) + "\n").encode("utf-8")
        if sum(len(p) for p in parts) + len(block) + 13 > target:
            break
        parts.append(block)
        n += 1
    return top_up(parts, target)


# ------------------------------------------------------------------ manifest

def expected_files() -> dict[str, int]:
    files = {}
    for tier, size in TIERS.items():
        files[f"large-many-{tier}.md"] = size
        files[f"large-single-para-{tier}.md"] = size
        files[f"large-single-code-{tier}.md"] = size
    files["large-1m.txt"] = TIERS["1m"]
    return files


def build_all() -> dict[str, bytes]:
    out: dict[str, bytes] = {}
    for tier, size in TIERS.items():
        out[f"large-many-{tier}.md"] = build_many(size)
        out[f"large-single-para-{tier}.md"] = build_single_para(size)
        out[f"large-single-code-{tier}.md"] = build_single_code(size)
    out["large-1m.txt"] = build_txt(TIERS["1m"])
    return out


# -------------------------------------------------------------------- checks

def split_blocks(data: bytes) -> list[bytes]:
    text = data.decode("utf-8")
    return [b for b in text.split("\n\n") if b.strip()]


def check(data: bytes, name: str, size: int) -> list[str]:
    errors: list[str] = []
    if len(data) != size:
        errors.append(f"{name}: size {len(data)} != {size}")
        return errors
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as e:
        return [f"{name}: not valid UTF-8: {e}"]
    if not data.endswith(b"\n"):
        errors.append(f"{name}: must end with newline")
    if name.startswith("large-many-"):
        blocks = split_blocks(data)
        if len(blocks) < size // 400:
            errors.append(
                f"{name}: only {len(blocks)} blocks, expected >= {size // 400}"
            )
        biggest = max(len(b.encode("utf-8")) for b in blocks)
        if biggest > 4096:
            errors.append(f"{name}: max block {biggest} bytes > 4096")
    elif name.startswith("large-single-para-"):
        lines = text.split("\n")
        # Title + description + blank lines, then exactly one giant line.
        giant = [ln for ln in lines if len(ln.encode("utf-8")) > 4096]
        if len(giant) != 1:
            errors.append(f"{name}: expected 1 giant line, found {len(giant)}")
        elif len(giant[0].encode("utf-8")) < size // 2:
            errors.append(f"{name}: giant line smaller than half the file")
    elif name.startswith("large-single-code-"):
        if text.count("```") != 2:
            errors.append(f"{name}: expected exactly one fenced block")
        inner = text.split("```")[1] if text.count("```") == 2 else ""
        if len(inner.encode("utf-8")) < size // 2:
            errors.append(f"{name}: fenced body smaller than half the file")
        long_lines = [
            ln for ln in inner.split("\n") if len(ln.encode("utf-8")) >= 500
        ]
        if not long_lines:
            errors.append(f"{name}: no long (>=500B) code lines found")
    elif name == "large-1m.txt":
        if "# Not A Heading" not in text:
            errors.append(f"{name}: TXT marker paragraph missing")
    return errors


def verify(out_dir: Path) -> list[str]:
    errors: list[str] = []
    for name, size in expected_files().items():
        path = out_dir / name
        if not path.is_file():
            errors.append(f"missing generated file: {name}")
            continue
        errors.extend(check(path.read_bytes(), name, size))
    # Determinism: two independent in-memory generations must hash identically.
    first = {k: hashlib.sha256(v).hexdigest() for k, v in build_all().items()}
    second = {k: hashlib.sha256(v).hexdigest() for k, v in build_all().items()}
    for name in first:
        if first[name] != second[name]:
            errors.append(f"{name}: non-deterministic generation")
    on_disk = {}
    for name in expected_files():
        path = out_dir / name
        if path.is_file():
            on_disk[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    for name in first:
        if name in on_disk and on_disk[name] != first[name]:
            errors.append(f"{name}: on-disk bytes differ from fresh generation")
    return errors


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "command", nargs="?", default="generate", choices=["generate", "verify"]
    )
    parser.add_argument("--out", default=str(DEFAULT_OUT))
    args = parser.parse_args(argv)
    out_dir = Path(args.out)
    if args.command == "generate":
        out_dir.mkdir(parents=True, exist_ok=True)
        blobs = build_all()
        for name, data in blobs.items():
            (out_dir / name).write_bytes(data)
            print(f"wrote {name} ({len(data)} bytes)")
    errors = verify(out_dir)
    if errors:
        print("FAIL:")
        for e in errors:
            print(f"  - {e}")
        return 1
    total = sum(expected_files().values())
    print(
        f"OK: {len(expected_files())} files, "
        f"{total} bytes total, sizes exact, shapes hold, generation deterministic."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
