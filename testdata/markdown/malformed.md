# Malformed Markdown (must not crash; degrade gracefully)

Unclosed emphasis *this never ends

Unclosed strong **also never ends

Unclosed fence:

```kotlin
fun broken() {
    println("no closing fence in this file")

Unclosed link [text without destination

Unclosed image ![alt without close

Table with ragged rows:

| a | b | c |
| --- | --- |
| only one cell |
| too | many | cells | here | extra |

Blockquote without content:

>
>
>text after empty quotes

List with inconsistent markers:

- dash
* star
+ plus
  1) paren style (not standard)

#HeadingsWithoutSpace

Trailing fence with wrong language tag:

```not-a-real-language-xyz
some text
```

HTML-ish angle brackets that are not tags: <3 love and a < b comparison.

A lone carriage return below (line ends with CR only):
trailing spaces and tabs here:   	
