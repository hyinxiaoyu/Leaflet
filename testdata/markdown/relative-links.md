# Relative links

Links resolve inside the authorized library tree; `..` may climb within the
tree but must never escape it.

- [sibling document](sibling.md)
- [nested guide](guides/advanced/deep-target.md)
- [parent climb](../testdata-anchor.md)
- [fragment in same file](#relative-links)
- [fragment to duplicate heading](duplicate-headings.md#overview)
- [fragment to missing heading](sibling.md#no-such-heading)
- [path with spaces](my%20docs/notes%20with%20spaces.md)
- [path with Chinese](资料/说明.md)
- [missing target](does-not-exist.md)
- [missing target with fragment](does-not-exist.md#section)
- [escaped above root](../../outside-root.md)
- [absolute path (must not resolve as file path)](/etc/passwd.md)
- [external https](https://example.com/some/page)
- [unknown scheme](foobar:not-a-known-scheme)
- [empty destination]()
- [mail link](mailto:someone@example.com)
