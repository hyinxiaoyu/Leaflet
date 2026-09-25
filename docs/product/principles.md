# Product Principles

English | [中文](principles.zh.md)

> Source: the ten original principles in `docs/archive/init.md` §66. These principles guide requirements review and architecture decisions. Any conflicting design must provide evidence and rationale and record them in the relevant REQ / ADR.

## Principle 1

```text
Files belong to the user.
```

Original Markdown/TXT files are the single source of truth. The app stores auxiliary data only and must not damage, lock, or hold user files hostage.

## Principle 2

```text
Folders are libraries.
```

Keep the user's original folder structure for browsing and organization; do not replace it with a separate bookshelf model.

## Principle 3

```text
Reading comes before editing.
```

Leaflet is a reader, not an editor. No feature may come at the cost of the reading experience.

## Principle 4

```text
E-Ink is a platform, not a theme.
```

Centralize display behavior in abstractions such as DisplayProfile and MotionPolicy. Do not scatter `if (isEink)` checks or treat E-Ink as a black-and-white skin.

## Principle 5

```text
Offline by default.
```

Do not connect to a network automatically or load remote resources (remote images use placeholders). Do not add accounts or cloud services.

## Principle 6

```text
Fast enough for AI-sized documents.
```

Establish performance baselines early. Large-document operations must be cancellable and must not cause OOM, ANR, or crashes.

## Principle 7

```text
Do not lock users into Leaflet.
```

Deleting the database, removing a Library, or uninstalling the app must not affect original files. Do not use a proprietary format to lock users in.

## Principle 8

```text
Simple UI, powerful reading.
```

Keep the core path (open → TOC → read → exit) minimal. Complexity belongs only in deeper reading capabilities.

## Principle 9

```text
One codebase, multiple distributions.
```

The `full` and `eink` flavors share 90%+ of their core code. Express differences through flavor configuration; do not duplicate the Reader implementation.

## Principle 10

```text
Every feature must justify its cost on E-Ink.
```

Animations, frequent refreshes, and complex effects are disabled by default on E-Ink unless their benefits outweigh ghosting and power costs.
