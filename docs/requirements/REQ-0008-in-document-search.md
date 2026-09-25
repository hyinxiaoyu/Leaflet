# REQ-0008 — In-document Search

English | [中文](REQ-0008-in-document-search.zh.md)

Status: READY
Priority: P0
Milestone: M1

## Problem

AI material often contains thousands of lines, and users need to find parameters, error codes, and other terms quickly. Without in-document search, they must page through manually, which is especially painful on E-Ink. The MVP defers library-wide FTS (`docs/archive/init.md` §25) but must deliver correct search within the current document (§26).

Source: `docs/archive/init.md` §§25–26; implementation-plan T11.

## User Story

As a reader looking for key information in a long document, I want to enter a term, see the match count, and jump between matches so that I can locate content without manually paging through the whole document.

## Scope

- Literal search within the currently loaded document, with count, previous/next navigation, and current-match highlighting.
- Search Markdown's visible text, not syntax markers such as `#`, `*`, or `[]()`. Visible code and table-cell text are searchable.
- Locate through parse/render mapping: block → rendered item → visible line/offset. Never use source offsets as screen coordinates.
- Search is cancellable. New input or document switch cancels old work; stale results cannot replace current results.
- Define search behavior for large files and plain-text fallback; at minimum, all fallback text remains searchable (FR-9).
- Clearly represent matches, no-match state, and document switches in UI.

## Non-goals

No regular expressions or advanced query syntax (AND/OR/wildcards/case options outside literal behavior), library-wide search/background indexing/Room FTS, cross-document result aggregation, persistent search history, replace, or full-document reparse per keystroke.

## User Experience

Expose search from Reader menu/toolbar. The expanded bar has a query input, count such as `3/12`, previous/next, and close. Show an explicit no-match state. Highlight the current match differently from other matches. Scroll each match into view; E-Ink uses instant scroll. Closing search or switching documents clears old state/highlights. Empty input performs no search and shows no error.

## Functional Requirements

- **FR-1:** Run literal matching against the current loaded document; return total count and support previous/next navigation.
- **FR-2:** Match rendered visible text from headings, body, lists, quotes, code, and table cells. Markdown syntax itself is excluded.
- **FR-3:** Map block index + visible in-block offset → RecyclerView item → line. Source offset is parse-side input only, never a scroll pixel or item coordinate.
- **FR-4:** Run search as cancellable background work. New query/document cancels the prior job; discard stale results.
- **FR-5:** Switching documents clears query, count, highlights, and job state.
- **FR-6:** Reuse parsed structure and incrementally scan/match; do not reparse the full document on query changes.
- **FR-7:** Show current match index and total. Show an empty state for zero matches; empty query starts no job.
- **FR-8:** TXT supports literal search over all visible text.
- **FR-9:** Search and navigate within plain-text fallback, including giant-block fallback. If any fallback cannot be located, document/UI must declare and T11 must accept it; never silently skip text.

## Acceptance Criteria

- **AC-1:** A Chinese sample with five dispersed matches shows count 5 and previous/next visits all five in document order with correct index.
- **AC-2:** In `# Heading`, `**bold**`, and `[text](url)`, searching `**` or `](` does not match source markers; searching visible “Heading” does.
- **AC-3:** Matches in code and tables are found and navigable to the correct item.
- **AC-4:** After five rapid characters within 200 ms, show only the final query result; profiling/logs prove earlier jobs were cancelled and did not overwrite it.
- **AC-5:** Search A, switch to B: B starts empty and A's highlights are gone.
- **AC-6:** No-match query shows the empty state, without crash or jump.
- **AC-7:** Emoji, Japanese, and repeated matches count/navigate correctly. Cross-block queries follow the locked “no cross-block match” rule.
- **AC-8:** Search a 10 MB sample or fallback without ANR/OOM. Loading is allowed; behavior matches FR-9.

## Edge Cases

Empty/whitespace query performs no search. Emoji, Chinese/Japanese, and mixed-case queries match literally; case behavior is frozen below. Empty documents show no matches. Repeated matches navigate in stable document order. Exiting Reader cancels background work and prevents callbacks to destroyed UI. Giant fallback follows FR-9.

## E-Ink Considerations

Use instant navigation, no smooth scroll, and static high-contrast highlights. Avoid full-screen refresh storms while typing; actual ghosting results require device-matrix evidence. Keyboard show/hide must not lose position (coordinate with REQ-0005).

## Standard Android Considerations

A subtle search-bar transition is allowed but cannot carry result meaning. System keyboard suggestions may be used, but matching uses the literal query string.

## Performance Considerations

Do not reparse on every input; match against parsed visible text or an incremental scan. Large-document work must be cancellable with loading feedback. Avoid O(N²). Search latency thresholds come from REQ-0011, not invented here.

## Storage / Data Impact

Do not persist queries/results, create a library-wide index, or write originals. Release in-memory visible-text index and cancellation resources when the document closes. FTS mention reserves future schema/Repository compatibility only; this REQ creates no table.

## Test Plan

Unit tests cover literal counts (Chinese/Japanese/emoji/repeats/no match), syntax exclusion, no cross-block matching, and stale-result rejection. Use `long.md`, `chinese.md`, `japanese.md`, `unicode.md`, `code.md`, `tables.md`, `malformed.md`, plus a 10 MB sample. UI/instrumented tests cover match traversal, document-switch reset, and rapid-query cancellation. Record E-Ink jump behavior in `eink-test-matrix.md`; mark missing hardware NOT_RUN.

## Dependencies

Requires REQ-0002 block rendering, REQ-0003 position semantics, and REQ-0005/0006 shared navigation mapping. T11 depends on T10 so resource/link parsing is stable before search mapping is finalized.

## Open Questions

- T11 froze case-sensitive search (`String.indexOf` literal semantics); `ErrorCode`, `errorcode`, and `ERRORCODE` do not match each other. Tests lock this and the search hint states case sensitivity. Case-insensitive behavior requires a new REQ.
- “Current chapter only” search is not included; add a new REQ if needed.
- T11 giant-block fallback uses bounded `PLAIN_FALLBACK` chunks, which remain searchable and navigable (`DocumentSearchVisibilityTest.degradedFallbackChunksAreSearchable`). No unlocatable fallback or extra message remains.
- Set latency regression thresholds only after REQ-0011 baselines.
