# Product Vision

English | [中文](vision.zh.md)


> Source: `docs/archive/init.md` §§1–2 (product positioning and milestone priorities), §§26–27 (MVP scope and non-goals), and §67 (ultimate decision test). Scope interpretation follows §§1–2 of `docs/plans/M0-M1-implementation-plan.md`. This document defines WHAT and WHY; implementation details live in `docs/architecture/` and `docs/adr/`.

### Product positioning

Leaflet is a **local-first, folder-first Android reader** for the large volume of local text created in the AI era. Its primary formats are **Markdown and TXT**, and it treats **E-Ink as a first-class reading platform**.

- **Local-first:** Original files remain the single source of truth. Markdown/TXT produced by ChatGPT, Claude, Codex, Gemini, other agents, or research scripts already lives in user folders. Leaflet reads those files directly; users do not import them as books. The database stores auxiliary indexes, reading progress, recent documents, settings, search indexes, and UI state. Deleting the Leaflet database must leave original files untouched.
- **Folder-first:** Adding a library means choosing an Android folder. That folder becomes the Library, its original structure is retained, and its Markdown/TXT files are read directly. The folder hierarchy is the library structure; Leaflet does not replace it with a second bookshelf model.
- **Markdown and TXT first:** The first release must read Markdown and TXT (REQ-0002). PDF, EPUB, CBZ, and other paginated or book formats are out of scope.
- **E-Ink is first-class:** E-Ink is a platform, not a monochrome theme (Principle 4 in `principles.md`). Runtime modes (STANDARD / EINK) and distribution flavors (`full` / `eink`) are separate from day one. Display behavior is centralized in DisplayProfile / MotionPolicy rather than scattered `if (isEink)` checks.

### Non-goals

The following are explicitly out of scope unless the user adds a requirement: Markdown editing, note editing, accounts, cloud sync or storage, AI Chat, RAG, vector databases, online AI, PDF/EPUB/CBZ, handwriting, OCR, advanced annotations, bidirectional-link knowledge bases, an Obsidian clone, plugins, WebDAV/Git/Syncthing clients, full tagging, and an online store. Library-wide FTS is also out of scope in the first phase: the MVP searches only the open document (REQ-0008); cross-library search is an M4 item. Complex animations and rich interaction are out of scope for the first release; animations remain minimal and can be disabled globally, with all animation off by default in E-Ink mode.

Use the decision test in `docs/archive/init.md` §67: if a design makes Leaflet increasingly resemble Obsidian, Notion, a Markdown IDE, a file manager, or an AI chat app, its direction may be wrong.

### Core user journey

The core experience must remain:

```text
Open the app
    ↓
See your document folders
    ↓
Tap a document
    ↓
Read comfortably
    ↓
Exit
```

Complex features must not disrupt this path. M0/M1 work exists to make it stable, fast, and comfortable on E-Ink. Any feature outside this path must justify its cost on E-Ink (Principle 10 in `principles.md`).

### M0/M1 boundary

- M0 delivers a buildable project with both flavors and the basic “add folder → browse → read → restore position” flow.
- M1 delivers an E-Ink MVP Alpha release candidate that can be accepted. Signing and public distribution are separate from development acceptance.
- Do not create empty implementations for non-goals in the name of “architecture preparation.”
- M1 includes usable baseline support for tables, code blocks, and relative resources. M2 improves their performance and experience; it does not defer the required baseline (implementation plan §2).
