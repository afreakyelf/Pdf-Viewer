package com.rajat.pdfviewer.compose

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        factory = { context -> PdfRendererView(context) },
        update = { pdfRendererView ->
            statusCallBack?.let { pdfRendererView.statusListener = it }
            zoomListener?.let { pdfRendererView.zoomListener = it }

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
        update = { pdfRendererView ->
            statusCallBack?.let { pdfRendererView.statusListener = it }
            zoomListener?.let { pdfRendererView.zoomListener = it }

            pdfRendererView.initWithFile(file, cacheStrategy)
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
        update = { pdfRendererView ->
            statusCallBack?.let { pdfRendererView.statusListener = it }
            zoomListener?.let { pdfRendererView.zoomListener = it }

            // Safely offload to background if needed
            pdfRendererView.initWithUri(uri)
        },
        modifier = modifier,
    )
}

@Composable
fun PdfRendererViewComposeFromAsset(
    assetFileName: String,
    modifier: Modifier = Modifier,
    cacheStrategy: CacheStrategy = CacheStrategy.MAXIMIZE_PERFORMANCE,
    statusCallBack: PdfRendererView.StatusCallBack? = null,
    zoomListener: PdfRendererView.ZoomListener? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember(assetFileName) { mutableStateOf<File?>(null) }

    LaunchedEffect(assetFileName) {
        scope.launch(Dispatchers.IO) {
            file = fileFromAsset(context, assetFileName)
        }
    }

    file?.let { readyFile ->
        AndroidView(
            factory = { PdfRendererView(it) },
            update = { pdfRendererView ->
                statusCallBack?.let { pdfRendererView.statusListener = it }
                zoomListener?.let { pdfRendererView.zoomListener = it }
                pdfRendererView.initWithFile(readyFile, cacheStrategy)
            },
            modifier = modifier,
        )
    }
}