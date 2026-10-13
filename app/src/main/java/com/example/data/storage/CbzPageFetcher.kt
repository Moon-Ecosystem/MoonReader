package com.example.data.storage

import android.webkit.MimeTypeMap
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import com.example.data.CbzPage
import okio.Buffer
import java.io.IOException
import java.util.Locale

/** Lets Coil load a [CbzPage] by reading its bytes straight out of the chapter archive. */
class CbzPageFetcher(private val page: CbzPage, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val buffer = OpenArchives.readPage(options.context, page) { Buffer().readFrom(it) }
            ?: throw IOException("Cannot open archive ${page.archiveUri}")
        val extension = page.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return SourceResult(
            source = ImageSource(buffer, options.context),
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension),
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<CbzPage> {
        override fun create(data: CbzPage, options: Options, imageLoader: ImageLoader): Fetcher =
            CbzPageFetcher(data, options)
    }
}

/** Without a key Coil skips its memory cache, so turning back to a recent page would re-read and re-decode it. */
class CbzPageKeyer : Keyer<CbzPage> {
    override fun key(data: CbzPage, options: Options): String = "cbz:${data.archiveUri}#${data.entryName}"
}
