package io.leaflet.reader.review

import io.leaflet.markdown.LeafletDocumentParser
import io.leaflet.markdown.TextDecoder
import io.leaflet.model.DocumentId
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val source = "| Wide | Short |\n| --- | --- |\n| ${"界".repeat(10000)} | b |"
    val parsed = LeafletDocumentParser.createForJvmTests().parse(
        DocumentId("wide.md"), TextDecoder.decode(source.toByteArray()), "wide.md", true,
    )
    println("sourceChars=${source.length}, degraded=${parsed.degraded}, kinds=${parsed.blocks.map { it.kind }}, blockSourceChars=${parsed.blocks.map { it.source.length }}")
}
