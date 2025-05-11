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
    jumpToPage: Int? = null,
    statusCallBack: PdfRendererView.StatusCallBack? = null,
    zoomListener: PdfRendererView.ZoomListener? = null,
    onReady: ((PdfRendererView) -> Unit)? = null,
) {
    val context = LocalContext.current
    var resolvedFile by remember(source) { mutableStateOf<File?>(null) }
    val pdfViewRef = remember { mutableStateOf<PdfRendererView?>(null) }
    var initialized by remember(source) { mutableStateOf(false) }

    val combinedCallback = remember(statusCallBack, jumpToPage) {
        object : PdfRendererView.StatusCallBack {
            override fun onPdfRenderSuccess() {
                statusCallBack?.onPdfRenderSuccess()
                pdfViewRef.value?.let { view ->
                    jumpToPage?.let { view.jumpToPage(it) }
                    onReady?.invoke(view)
                }
            }
            override fun onPdfLoadStart() = statusCallBack?.onPdfLoadStart() ?: Unit
            override fun onPdfLoadProgress(progress: Int, downloadedBytes: Long, totalBytes: Long?) =
                statusCallBack?.onPdfLoadProgress(progress, downloadedBytes, totalBytes) ?: Unit
            override fun onPdfLoadSuccess(absolutePath: String) =
                statusCallBack?.onPdfLoadSuccess(absolutePath) ?: Unit
            override fun onError(error: Throwable) =
                statusCallBack?.onError(error) ?: Unit
            override fun onPageChanged(currentPage: Int, totalPage: Int) =
                statusCallBack?.onPageChanged(currentPage, totalPage) ?: Unit
            override fun onPdfRenderStart() = statusCallBack?.onPdfRenderStart() ?: Unit
        }
    }

    if (source is PdfSource.PdfSourceFromAsset) {
        LaunchedEffect(source.assetFileName) {
            resolvedFile = fileFromAsset(context, source.assetFileName)
        }
    }

    AndroidView(
        factory = { PdfRendererView(it).also { pdfViewRef.value = it } },
        update = { view ->
            view.statusListener = combinedCallback
            view.zoomListener = zoomListener

            if (!initialized) {
                when (source) {
                    is PdfSource.Remote -> view.initWithUrl(
                        url = source.url,
                        headers = headers,
                        lifecycleCoroutineScope = lifecycleOwner.lifecycleScope,
                        lifecycle = lifecycleOwner.lifecycle,
                        cacheStrategy = cacheStrategy
                    )
                    is PdfSource.LocalFile -> view.initWithFile(source.file, cacheStrategy)
                    is PdfSource.LocalUri -> view.initWithUri(source.uri)
                    is PdfSource.PdfSourceFromAsset -> {
                        resolvedFile?.let {
                            view.initWithFile(it, cacheStrategy)
                        }
                    }
                }

                initialized = true
            }

            if (jumpToPage == null) {
                onReady?.invoke(view)
            }
        },
        modifier = modifier,
    )
}

