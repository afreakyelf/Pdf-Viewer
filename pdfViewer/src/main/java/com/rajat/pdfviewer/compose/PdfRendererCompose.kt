package com.rajat.pdfviewer.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.rajat.pdfviewer.HeaderData
import com.rajat.pdfviewer.PdfRendererView
import java.io.File

@Composable
fun PdfRendererViewCompose(
    url: String? = null,
    file: File? = null,
    headers: HeaderData = HeaderData(),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    statusCallBack: PdfRendererView.StatusCallBack? = null
) {
    val lifecycleScope = lifecycleOwner.lifecycleScope

    AndroidView(
        factory = { context ->
            PdfRendererView(context).apply {
                if (statusCallBack != null) {
                    statusListener = statusCallBack
                }
                if (file != null) {
                    initWithFile(file)
                } else if (url != null) {
                    initWithUrl(url, headers, lifecycleScope, lifecycleOwner.lifecycle)
                }
            }
        },
        update = { view ->
            // Update logic if needed
        }
    )
}