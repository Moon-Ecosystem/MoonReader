package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.data.storage.CbzPageFetcher
import com.example.data.storage.CbzPageKeyer

class MoonReaderApp : Application(), ImageLoaderFactory {
    // Reader pages are CbzPage refs that Coil reads straight from the chapter archive
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(CbzPageKeyer())
                add(CbzPageFetcher.Factory())
            }
            .build()
}
