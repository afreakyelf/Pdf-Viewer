package com.rajat.pdfviewer.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.rajat.pdfviewer.HeaderData
import com.rajat.pdfviewer.PdfRendererView
import com.rajat.pdfviewer.util.CacheStrategy
import com.rajat.pdfviewer.util.FileUtils.fileFromAsset
import com.rajat.pdfviewer.util.PdfSource
import java.io.File

@Composable
fun PdfRendererViewCompose(
    source: PdfSource,
    modifier: Modifier = Modifier,
    headers: HeaderData = HeaderData(),
    cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    statusCallBack: PdfRendererView.StatusCallBack? = null,
    zoomListener: PdfRendererView.ZoomListener? = null,
    onReady: ((PdfRendererView) -> Unit)? = null,
) {
    val context = LocalContext.current
    var resolvedFile by remember(source) { mutableStateOf<File?>(null) }

    // Asset support
    if (source is PdfSource.PdfSourceFromAsset) {
        LaunchedEffect(source.assetFileName) {
            resolvedFile = fileFromAsset(context, source.assetFileName)
        }
    }

    AndroidView(
        factory = { PdfRendererView(it) },
        update = { pdfRendererView ->
            statusCallBack?.let { pdfRendererView.statusListener = it }
            zoomListener?.let { pdfRendererView.zoomListener = it }

            when (source) {
                is PdfSource.Remote -> pdfRendererView.initWithUrl(
                    url = source.url,
                    headers = headers,
                    lifecycleCoroutineScope = lifecycleOwner.lifecycleScope,
                    lifecycle = lifecycleOwner.lifecycle,
                    cacheStrategy = cacheStrategy
                )

                is PdfSource.LocalFile -> pdfRendererView.initWithFile(
                    file = source.file,
                    cacheStrategy = cacheStrategy
                )

                is PdfSource.LocalUri -> pdfRendererView.initWithUri(source.uri)

                is PdfSource.PdfSourceFromAsset -> {
                    resolvedFile?.let {
                        pdfRendererView.initWithFile(it, cacheStrategy)
                    }
                }
            }

            onReady?.invoke(pdfRendererView)
        },
        modifier = modifier,
    )
}
