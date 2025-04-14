package com.rajat.pdfviewer.compose

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.rajat.pdfviewer.HeaderData
import com.rajat.pdfviewer.PdfRendererView
import com.rajat.pdfviewer.util.CacheStrategy
import com.rajat.pdfviewer.util.FileUtils.fileFromAsset
import java.io.File

@Composable
fun PdfRendererViewCompose(
    url: String,
    modifier: Modifier = Modifier,
    headers: HeaderData = HeaderData(),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    statusCallBack: PdfRendererView.StatusCallBack? = null,
    zoomListener: PdfRendererView.ZoomListener? = null,
) {
    AndroidView(
        factory = { context: Context -> PdfRendererView(context) },
        update = { pdfRendererView: PdfRendererView ->
            if (statusCallBack != null) {
                pdfRendererView.statusListener = statusCallBack
            }
            if (zoomListener != null) {
                pdfRendererView.zoomListener = zoomListener
            }

            pdfRendererView.initWithUrl(
                url = url,
                headers = headers,
                lifecycleCoroutineScope = lifecycleOwner.lifecycleScope,
                lifecycle = lifecycleOwner.lifecycle,
            )
        },
        modifier = modifier,
    )
}

@Composable
fun PdfRendererViewCompose(
    file: File,
    modifier: Modifier = Modifier,
    cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE,
    statusCallBack: PdfRendererView.StatusCallBack? = null,
    zoomListener: PdfRendererView.ZoomListener? = null,
    ) {
    AndroidView(
        factory = { context -> PdfRendererView(context) },
        update = { pdfRendererView: PdfRendererView ->
            if (statusCallBack != null) {
                pdfRendererView.statusListener = statusCallBack
            }
            if (zoomListener != null) {
                pdfRendererView.zoomListener = zoomListener
            }

            pdfRendererView.initWithFile(file = file, cacheStrategy = cacheStrategy)
        },
        modifier = modifier
    )
}

@Composable
fun PdfRendererViewCompose(
    uri: Uri,
    modifier: Modifier = Modifier,
    statusCallBack: PdfRendererView.StatusCallBack? = null,
    zoomListener: PdfRendererView.ZoomListener? = null,
    ) {
    AndroidView(
        factory = { context -> PdfRendererView(context) },
        update = { pdfRendererView: PdfRendererView ->
            if (statusCallBack != null) {
                pdfRendererView.statusListener = statusCallBack
            }
            if (zoomListener != null) {
                pdfRendererView.zoomListener = zoomListener
            }

            pdfRendererView.initWithUri(uri = uri)
        },
        modifier = modifier,
    )
}

@Composable
fun PdfRendererViewComposeFromAsset(
    assetFileName: String,
    modifier: Modifier = Modifier,
    statusCallBack: PdfRendererView.StatusCallBack? = null,
    zoomListener: PdfRendererView.ZoomListener? = null,
    ) {
    val context = LocalContext.current
    AndroidView(
        factory = { PdfRendererView(it) },
        update = { pdfRendererView ->
            if (statusCallBack != null) {
                pdfRendererView.statusListener = statusCallBack
            }

            if (zoomListener != null) {
                pdfRendererView.zoomListener = zoomListener
            }

            val file = fileFromAsset(context, assetFileName)
            pdfRendererView.initWithFile(file)
        },
        modifier = modifier,
    )
}
