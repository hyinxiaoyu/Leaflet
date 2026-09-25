# testdata — reader fixtures & large-sample generator

English | [中文](README.zh.md)

Scope: T03 only. Deterministic, self-authored fixtures for T05/T06/T10/T11
reader work. No third-party documents are copied here: every file is either
hand-written for this repo or produced by `generate_large_samples.py`.
The two classical one-line quotations (`long.md`: opening line of “Quiet Night Thought” (静夜思),
opening line of “The Tale of the Heike” (平家物語), plus one McLuhan-attributed sentence) are public-domain
or short fair-use quotations, not copied documents.

## Layout

```text
testdata/
├── README.md                    # this file
├── generate_large_samples.py    # deterministic generator (stdlib only)
├── testdata-anchor.md           # parent-climb (../) link/image target
├── testdata-anchor.png          # parent-climb image target (16x16 PNG)
├── markdown/                    # small hand-written fixtures (UTF-8)
│   ├── simple.md / headings.md / lists.md / tables.md / code.md
│   ├── long.md / unicode.md / chinese.md / japanese.md
│   ├── relative-links.md / relative-images.md / malformed.md / huge.md
│   ├── nested-lists.md / duplicate-headings.md
│   ├── long-paragraph.md        # one ~64 KB paragraph line (shape demo)
│   ├── long-codeblock.md        # one ~64 KB fenced block (shape demo)
│   ├── empty.md / empty.txt / sample.txt
│   ├── bom.md                   # UTF-8 BOM + CJK body
│   ├── broken-encoding.md       # truncated multibyte + illegal bytes
│   ├── sibling.md               # relative-links sibling target
│   ├── guides/advanced/deep-target.md   # nested link target
│   ├── my docs/notes with spaces.md     # spaced-path link target
│   ├── my docs/spaced image.png         # spaced-path image target
│   ├── 资料/说明.md                     # Chinese-path link target
│   ├── 资料/图片.png                    # Chinese-path image target
│   └── assets/leaf-16.png, leaf-256.png, vector.svg
└── generated/                   # generator output (git-ignored, 13 files)
    ├── large-many-{100k,1m,5m,10m}.md
    ├── large-single-para-{100k,1m,5m,10m}.md
    ├── large-single-code-{100k,1m,5m,10m}.md
    └── large-1m.txt
```

Regenerate:

```bash
python3 testdata/generate_large_samples.py generate   # write + verify
python3 testdata/generate_large_samples.py verify     # check only
python3 testdata/generate_large_samples.py --out /tmp/gen
```

`verify` asserts: exact byte size per tier (100k=102400, 1m=1048576,
5m=5242880, 10m=10485760), UTF-8 validity, trailing newline, shape rules
(many: block count ≥ size/400 and max block ≤ 4096 B; single-para: exactly
one >4096 B line ≥ half the file; single-code: exactly one fence, body ≥
half the file, has ≥500 B lines), in-memory determinism (two generations
hash-equal), and on-disk bytes equal fresh generation. Exit 0 only if all
pass. Verified 2026-09-18: 13 files, 51,687,424 bytes total, re-run
byte-identical (sha256 diff clean).

## docs/archive/init.md §48 coverage map

| §48 item | File(s) | Assertion target |
| --- | --- | --- |
| simple.md | `markdown/simple.md` | bold/italic/strike/inline-code/link/quote/hr/list/fence baseline renders |
| headings.md | `markdown/headings.md` | H1–H6 extract; `#NoSpace` stays paragraph; duplicate `## Duplicate` ×3 anchors unique |
| lists.md | `markdown/lists.md` | unordered/ordered/task/mixed nesting renders |
| tables.md | `markdown/tables.md` | simple/alignment/wide/inline-markup tables; wide table gets horizontal scroll, never clipped |
| code.md | `markdown/code.md` | fenced ± language, indented, inline, long-line wrap case; language tag shown, unknown tag degrades |
| long.md | `markdown/long.md` | 20-section scroll/TOC/search/position-restore drill; needles `long-doc-needle-*` findable |
| unicode.md | `markdown/unicode.md` | multi-script line, emoji, accents, combining marks, ZWJ family, RTL snippet, full-width punctuation |
| chinese.md | `markdown/chinese.md` | CJK punctuation/quotes/lists/table; search needle 量子计算机 found |
| japanese.md | `markdown/japanese.md` | kana/kanji mixing, 古池や needle, task list |
| relative-links.md | `markdown/relative-links.md` + targets | sibling/nested/fragment/dup-fragment/encoded-space/CJK-path resolve in-tree; missing target / missing fragment / `..`-escape / absolute-path / unknown-scheme / empty-dest show explicit missing-target state, never crash; external https hands to outside app on tap only |
| relative-images.md | `markdown/relative-images.md` + `assets/`, spaced/CJK/parent targets | local images decode sampled; missing/unsupported/empty-source show placeholder; remote http(s) shows static placeholder, zero network fetch |
| malformed.md | `markdown/malformed.md` | unclosed emphasis/fence/link/image, ragged table, stray markers render as literal fallback; no crash, no hang |
| huge.md | `markdown/huge.md` (pointer) + `generated/large-*` | 100 KB–10 MB open with no OOM/ANR/crash; giant-block shapes show loading/fallback notice; load cancellable, cancel releases resources; content readable (no silent truncation) |
| Chinese/Japanese/English/emoji | `chinese.md`, `japanese.md`, `unicode.md`, `simple.md` | Scripts render and search works for each |
| Long paragraph | `long-paragraph.md` (~64 KB single line), `generated/large-single-para-*` | Bounded layout (split/window/fallback) + notice; no unbounded single-block layout |
| Long code line | Long-line case in `code.md`, `long-codeblock.md`, `generated/large-single-code-*` | E-Ink wraps by default; wrap-off supports horizontal scroll |
| Large Markdown | `generated/large-many-*`, `generated/large-single-*` | AC-2 three-no (no OOM/ANR/crash) for all four tiers |
| Malformed Markdown | `malformed.md`, `broken-encoding.md` | Explicit notice/fallback; never crash or claim auto-detection |
| Empty files | `empty.md` (0 B), `empty.txt` (0 B) | Empty state, not an error; open/close cleanly |
| Large TXT | `sample.txt`, `generated/large-1m.txt` | Literal text; `#`/`-` are not parsed; `txt-needle-plain-sample` is searchable |
| (extra) Duplicate headings | `duplicate-headings.md` (+ `headings.md`) | Three `Overview` + two `概述` headings have unique anchors; fragments land on correct instance |
| (extra) BOM | `bom.md` (EF BB BF + CJK) | BOM stripped, heading + body correct |
| (extra) Damaged encoding | `broken-encoding.md` (truncated CJK, lone continuation bytes, FF/FE) | Notice/fallback; valid lines remain readable; no crash |
| (extra) Giant paragraph/code | `long-paragraph.md`, `long-codeblock.md` | Small-scale shape demos for generated giant-block files |
| (extra) Nested lists | `nested-lists.md` (5-level unordered, 4-level ordered, nested tasks) | Correct indentation; no stack/recursion failure |
| (extra) Image and relative-link folders | `assets/`, `my docs/`, `资料/` (documents), `guides/advanced/`, `../testdata-anchor.*` | Spaces/CJK/nested/`../` resolve; `../../outside-root` rejected; missing target explicit |

## Notes for test authors

- All `.md`/`.txt` fixtures use LF endings and end with exactly one newline
  (except `empty.*`, which are 0 bytes).
- `vector.svg` is intentionally unsupported: image pipeline must show a
  placeholder, never attempt SVG rendering or network fetch.
- `leaf-256.png` (256×256) is the sampling/oversize test; `leaf-16.png` the
  baseline. Both are repo-generated solid-color PNGs (stdlib `zlib`, no
  image tools needed).
- `generated/` is git-ignored (`generated/.gitignore`: `*` + `!.gitignore`);
  CI/tests must run the generator first, never commit outputs.
- Test resources must never enter the release APK: fixtures live only under
  repo-root `testdata/`, which no `app/build.gradle.kts` `sourceSets`,
  `assets.srcDirs`, or `res.srcDirs` references (verified 2026-09-18; only
  `app/src/` is packaged — see "APK non-pollution" below).

## APK non-pollution (evidence, 2026-09-18)

- `app/build.gradle.kts` declares no `sourceSets` block at all: packaging
  uses the AGP default (`app/src/main/{java,res,assets,AndroidManifest.xml}`).
- `grep -rn "testdata" app/ core/ --include=*.kts --include=*.kt` → no hits
  outside this README's own directory (fixtures are referenced by tests via
  relative filesystem path, not packaged resources).
- `generated/` outputs are git-ignored and therefore absent from clean
  checkouts/artifacts.
