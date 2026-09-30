# REQ-0009 — Local Resources and Links

English | [中文](REQ-0009-local-resources-links.zh.md)

Status: READY
Priority: P0
Milestone: M1

## Problem

Folder-organized AI libraries contain cross-links between documents and relative-path images. A link such as `../Flink/architecture.md` should work directly. Broken links, Chinese/space paths that fail, unbounded `../`, silent remote image loading, or treating a content URI as a filesystem path interrupts reading and can cross the security boundary. These are core requirements of the folder-first model.

Source: `docs/archive/init.md` §§11–13, 15, 58; implementation-plan T10; relative-resource bridge research in `docs/research/markdown-renderers.md` §§4–5 (R5).

## User Story

As a reader of directory-organized AI material, I want in-library relative document links to navigate and relative images to display so that the library's organization remains usable while reading.

## Scope

- In-library relative Markdown/TXT links using `./`, `../`, nested folders, spaces, and Chinese paths.
- Parent navigation and document fragments such as `other.md#Heading`; jump to the target heading.
- Same-document anchors such as `#Install` scroll within the current document.
- Local relative images: resolve path, sample decode to viewport size, bound memory, cancel loads/release resources, and show placeholders for missing/damaged/unsupported formats.
- Remote images (`http(s)://`, `//`) never auto-load; show a static placeholder.
- External links are handed to another app only after a user tap.
- Resolve relative to the source document's parent inside the authorized Library tree. Permit `..` within the grant and reject escape. Never concatenate content URIs as paths. Use consistent URI encoding/normalization; do not execute unknown schemes.

## Non-goals

No network image loader/cache stack, built-in browser, cross-Library access, embedded PDF/EPUB/audio/video rendering (only MD/TXT targets open as documents; see edge cases), background whole-library link checking, image editing, or saving images to the gallery.

## User Experience

- Make tappable in-library document links visually distinct. Open the target in the current Reader session and jump to the fragment.
- Missing or out-of-tree targets show a clear “missing or outside this Library” notice; no crash or blank hang.
- Show local images inline. Loading uses a static placeholder; failures use a damaged-image placeholder with text. No spinner animation on E-Ink.
- Remote images show a static “remote images are not loaded automatically” placeholder.
- External links invoke the system chooser/handler. If no app can handle one, show a notice without crashing.

## Functional Requirements

- **FR-1:** Resolve relative document links from the source document's directory, normalized against the authorized Library root. Support `./`, `../`, nested, spaced, and Chinese paths.
- **FR-2:** Allow `..` while the normalized result remains inside the grant. Reject and report any path escaping the authorized tree.
- **FR-3:** Access content URIs only through SAF/DocumentFile semantics; never build filesystem paths by string concatenation. Keep percent-encoding and normalization of `.`, `..`, repeated/trailing slashes consistent end-to-end.
- **FR-4:** For `doc.md#fragment`, open the target document and locate the fragment's heading anchor. Duplicate-anchor behavior is deterministic and tested, consistent with REQ-0006.
- **FR-5:** A same-document `#fragment` scrolls within the current document. Unknown anchors show a notice and do not pretend success by jumping to the top.
- **FR-6:** Open local image streams through SAF and sample-decode for viewport size (`inSampleSize` or equivalent). Bound per-image decode memory; downsample or show a placeholder over the limit, never OOM.
- **FR-7:** Image work is cancellable on fast scroll, document switch, and exit; promptly release decode resources.
- **FR-8:** Missing, damaged, and unsupported images show placeholders without crashing or blocking other document content.
- **FR-9:** Never issue a network request for remote image URLs; show a static placeholder.
- **FR-10:** Invoke external-link intents only from a user gesture. Do not execute unknown schemes; do not crash.
- **FR-11:** If no external app handles a link, show a notice and preserve Reader state.

## Acceptance Criteria

- **AC-1:** In `relative-links.md` with spaces, Chinese filenames, nested folders, `./`, and `../`, every in-tree link opens the correct target; assert content.
- **AC-2:** Tapping `../../escape.md` outside the grant is rejected with a notice and no unauthorized read; cover in unit and instrumented tests.
- **AC-3:** `other.md#duplicate-heading` lands on the same target selected by TOC rules (joint assertion with REQ-0006).
- **AC-4:** Existing same-document anchor scrolls to its target. Missing anchor shows a notice and leaves position unchanged.
- **AC-5:** Local images, including a large image, are visible with bounded peak memory. Until the REQ-0011 limit is measured, assert no OOM and that downsample/placeholder path ran.
- **AC-6:** Missing, damaged, and remote HTTPS images show the corresponding placeholders and issue zero network requests.
- **AC-7:** Tapping HTTPS launches an external intent (intent stub in instrumented test); tapping unknown `foo://` does nothing and does not crash.
- **AC-8:** With no external handler, tapping a link shows a notice and preserves document content and position.

## Edge Cases

A direct link to a non-MD/TXT target (PDF/PNG) must not pretend to render as a document. Image extensions use the image path; other types show unsupported. Percent-encoded fragments, case, spaces, and hyphen variants must follow the TOC normalization rule. Deleted or unauthorized targets use REQ-0001 recovery messaging. Very large images (for example, tens-of-megabytes PNG) downsample or show a placeholder without OOM. Same-name images in different folders and case-only differences must have documented, deterministic behavior across case-sensitive/insensitive providers.

## E-Ink Considerations

Image loading/failure placeholders are static text, simple progress/border only, no spinner or shimmer. Grayscale legibility is sufficient; do not add network or heavy native dependencies for image enhancement. Link taps and image loads must not trigger smooth scrolling or crossfade.

## Standard Android Considerations

Standard may use a subtle placeholder transition but must not hide failure. External links use system handlers; do not build a browser.

## Performance Considerations

Sample-decode only to displayed dimensions; cancel offscreen image tasks during fast long-document scroll. The initial scan does not parse full text (`docs/archive/init.md` §13); link validity is checked on tap. Avoid O(N²) path normalization. Measure image memory limits in REQ-0011 rather than inventing thresholds.

## Storage / Data Impact

Do not copy originals or store images/documents in the database; it stores indexes and reading state only. Any decode cache is memory-only or tightly bounded temporary cache, never a default full-library copy, and is discarded when the document closes. Lightweight error state/logging may record missing/out-of-tree targets but not source contents.

## Test Plan

Unit-test path normalization with spaces/Chinese/`../`/escape rejection, fragment matching, unknown-scheme rejection, and SAF URI semantics through a DocumentFile test seam. Use `relative-links.md` and `relative-images.md` plus Chinese/nested/duplicate-fragment/missing-target samples. Instrumented tests with a real SAF provider cover in-tree navigation, escape rejection, image display/cancellation, external intent/no-handler notice, and large-image memory safety. Record E-Ink image/link/failure behavior in the device matrix; mark missing hardware NOT_RUN.

## Dependencies

Requires REQ-0001 Library grant/URI semantics, REQ-0002 Reader rendering, REQ-0006 TOC/anchor, REQ-0005 gesture arbitration, and T09 before T10.

## R3 evidence (2026-09-28)

The R3-06 acceptance suite follows links on the real page and found two defects, both fixed: DEF-R3-06-01 (a missing external handler was swallowed by `runCatching`, so the no-handler notice never appeared in production) and DEF-R3-06-02 (a successful `#anchor` jump never scrolled because the pending-jump effect did not re-run). Cross-document fragments, duplicate headings, unknown anchors, out-of-tree rejection, CJK relative paths, remote placeholders, and the missing-image placeholder are now covered on the page. The real-provider image pipeline is now covered by `ReaderImagePipelineTest` (live provider, sampling inside the decode budget, pixel check, missing/revoked, cancellation) and CJK/space/`../` resolution through the real `DocumentSourceImpl` + DAO by `DocumentSourceImplTest`. The image entry's rotation/destruction recycle and a page-level CJK link tap remain NOT_RUN. See [r3-06-delivery.md](../plans/r3-06-delivery.md).

## Open Questions

- T10 froze fragments as raw → percent-decode → TOC-rule slugify; the algorithm is shared with TOC, and first duplicate is best-effort.
- T10's initial image decode cap is 12 MiB (`ImageSample.MAX_DECODE_BYTES`), still unverified until T13/REQ-0011 measurements.
- T10 froze unsupported non-MD/TXT targets as a media notice, not rendered content.
- Cross-Library links are rejected by default; changing this requires a new REQ.
