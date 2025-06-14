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
    tapListener: PdfRendererView.TapListener? = null
) {
    val context = LocalContext.current
    val pdfViewRef = remember { mutableStateOf<PdfRendererView?>(null) }

    // Async resolve asset file
    var resolvedFile by remember(source) { mutableStateOf<File?>(null) }
    LaunchedEffect(source) {
        if (source is PdfSource.PdfSourceFromAsset) {
            resolvedFile = fileFromAsset(context, source.assetFileName)
        }
    }

    // Ensure initialization only once
    var hasInit by remember(source) { mutableStateOf(false) }

    // Combine user callback with internal jump logic
    val combinedCallback = remember(statusCallBack, jumpToPage, onReady) {
        object : PdfRendererView.StatusCallBack {
            override fun onPdfLoadStart() {
                statusCallBack?.onPdfLoadStart()
            }

            override fun onPdfLoadProgress(progress: Int, downloadedBytes: Long, totalBytes: Long?) {
                statusCallBack?.onPdfLoadProgress(progress, downloadedBytes, totalBytes)
            }

            override fun onPdfLoadSuccess(absolutePath: String) {
                statusCallBack?.onPdfLoadSuccess(absolutePath)
                pdfViewRef.value?.let { view ->
                    jumpToPage?.let { view.jumpToPage(it) }
                    onReady?.invoke(view)
                }
            }

            override fun onError(error: Throwable) {
                statusCallBack?.onError(error)
            }

            override fun onPageChanged(currentPage: Int, totalPage: Int) {
                statusCallBack?.onPageChanged(currentPage, totalPage)
            }

            override fun onPdfRenderSuccess() {
                statusCallBack?.onPdfRenderSuccess()
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            PdfRendererView(ctx).also { view ->
                view.statusListener = combinedCallback
                view.zoomListener = zoomListener
                view.tapListener = tapListener
                pdfViewRef.value = view
            }
        },
        update = { view ->
            view.statusListener = combinedCallback
            view.zoomListener = zoomListener
            view.tapListener = tapListener

            if (!hasInit) {
                when {
                    source is PdfSource.Remote -> {
                        view.initWithUrl(
                            url = source.url,
                            headers = headers,
                            lifecycleCoroutineScope = lifecycleOwner.lifecycleScope,
                            lifecycle = lifecycleOwner.lifecycle,
                            cacheStrategy = cacheStrategy
                        )
                        hasInit = true
                    }
                    source is PdfSource.LocalFile -> {
                        view.initWithFile(source.file, cacheStrategy)
                        hasInit = true
                    }
                    source is PdfSource.LocalUri -> {
                        view.initWithUri(source.uri)
                        hasInit = true
                    }
                    source is PdfSource.PdfSourceFromAsset && resolvedFile != null -> {
                        view.initWithFile(resolvedFile!!, cacheStrategy)
                        hasInit = true
                    }
                }
            }
        },
        modifier = modifier
    )
}
