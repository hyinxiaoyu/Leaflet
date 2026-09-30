# Long document (multi-section reading fixture)

A deliberately long but ordinary document: many small blocks, mixed syntax,
used to exercise scrolling, TOC, search, and position restore.

## Table of contents (manual)

- [Section 01](#section-01) through [Section 20](#section-20)
- [Code interlude](#code-interlude)
- [Table gallery](#table-gallery)
- [Closing](#closing)

## Section 01

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor
incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam.

- point one with **bold**
- point two with `code`
- point three with a [link](sibling.md)

## Section 02

> A quote to break the rhythm.
> Second line of the same quote.

1. ordered item one
2. ordered item two
3. ordered item three

## Section 03

Paragraph followed by a short fenced block:

```kotlin
val answer = 42
println("answer=$answer")
```

## Section 04

中文段落穿插：床前明月光，疑是地上霜。举头望明月，低头思故乡。
（李白《静夜思》，公有领域古典文本引用）

## Section 05

日本語の段落：古池や蛙飛び込む水の音。（松尾芭蕉の俳句、公有領域の古典引用）

## Section 06

Emoji checkpoint: 📚 🔋 🌙 ✅ ➡️ 🎉 — headings and body must align.

## Section 07

Another plain section. The reader should keep scroll position stable while
flipping through these without jank on low-end hardware.

## Section 08

- [ ] unchecked task in a long doc
- [x] checked task in a long doc

## Section 09

| col a | col b | col c |
| ----- | ----- | ----- |
| 1 | 2 | 3 |
| 4 | 5 | 6 |

## Section 10

Halfway mark. Search keyword: `long-doc-needle-10`.

## Section 11

Repeating structural content so find-in-page crosses many blocks.
Needle: long-doc-needle-11.

## Section 12

Repeating structural content so find-in-page crosses many blocks.
Needle: long-doc-needle-12.

## Section 13

Repeating structural content so find-in-page crosses many blocks.
Needle: long-doc-needle-13.

## Section 14

Repeating structural content so find-in-page crosses many blocks.
Needle: long-doc-needle-14.

## Section 15

Repeating structural content so find-in-page crosses many blocks.
Needle: long-doc-needle-15.

## Section 16

Nested list drill:

- a
  - b
    - c
      - d

## Section 17

Second quote:

> We shape our tools and thereafter our tools shape us.
> — commonly attributed to Marshall McLuhan (short quotation, fair use)

## Section 18

Inline mix: **bold** *italic* ~~strike~~ `code` [link](https://example.com).

## Section 19

Almost there. The next section holds the code interlude.

## Section 20

Final numbered section before the galleries.

## Code interlude

```python
def fibonacci(n):
    a, b = 0, 1
    for _ in range(n):
        yield a
        a, b = b, a + b
```

```text
plain block to test non-highlighted rendering
line two of the plain block
```

## Table gallery

| Language | Greeting | Note |
| -------- | -------- | ---- |
| English | Hello | baseline |
| 中文 | 你好 | CJK column |
| 日本語 | こんにちは | kana + kanji |
| Emoji | 👋🌍 | grapheme clusters |

## Closing

End of the long fixture. Search keyword: `long-doc-needle-end`.
