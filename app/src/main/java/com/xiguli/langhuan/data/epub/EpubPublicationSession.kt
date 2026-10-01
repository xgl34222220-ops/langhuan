package com.xiguli.langhuan.data.epub

import android.content.Context
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpStreamResponse
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

object EpubPublicationSession {
    private val offlineClient = object : HttpClient {
        override suspend fun stream(request: HttpRequest): Try<HttpStreamResponse, HttpError> =
            Try.failure(HttpError.IO(java.io.IOException("EPUB 网络资源已禁用")))
    }

    suspend fun open(context: Context, prepared: EpubOriginalStore.Prepared): Publication {
        val retriever = AssetRetriever(context.contentResolver, offlineClient)
        // Only the sanitized derivative is parsed. The exact original remains private and intact.
        val asset = retriever.retrieve(prepared.rendering, MediaType.EPUB).getOrElse {
            error("无法读取 EPUB 原版资源")
        }
        val opener = PublicationOpener(
            DefaultPublicationParser(context, offlineClient, retriever, pdfFactory = null),
            onCreatePublication = { container = SafeEpubContainer(container, prepared.archive) },
        )
        val publication = opener.open(asset, allowUserInteraction = false).getOrElse {
            asset.close()
            error("此 EPUB 无法打开，可能已损坏或受保护")
        }
        if (publication.isRestricted || !publication.conformsTo(Publication.Profile.EPUB) || publication.readingOrder.isEmpty()) {
            publication.close()
            error("此 EPUB 受保护或没有可阅读的章节")
        }
        return publication
    }
}

/** Never delegate unknown URLs to the SDK's composite/network resource container. */
internal class SafeEpubContainer(
    private val delegate: Container<Resource>,
    archive: EpubArchivePolicy.Archive,
) : Container<Resource> {
    private val allowed = archive.mediaTypes.filterValues { type ->
        type in setOf("application/xhtml+xml", "text/html", "text/css", "image/svg+xml", "image/png", "image/jpeg",
            "image/gif", "image/webp", "font/ttf", "font/otf", "font/woff", "font/woff2",
            "application/vnd.ms-opentype", "application/font-sfnt", "application/font-woff",
            "application/x-font-ttf", "application/x-font-opentype", "application/x-dtbncx+xml")
    }.keys
    private val urls = delegate.entries.mapNotNull { url ->
        EpubArchivePolicy.localPath("", url.toString())?.takeIf { it in allowed }?.let { it to url }
    }.toMap()
    override val entries: Set<Url> = urls.values.toSet()
    override fun get(url: Url): Resource? {
        val path = EpubArchivePolicy.localPath("", url.toString()) ?: return null
        return urls[path]?.let { delegate[it] }
    }
    override fun close() = delegate.close()
}
