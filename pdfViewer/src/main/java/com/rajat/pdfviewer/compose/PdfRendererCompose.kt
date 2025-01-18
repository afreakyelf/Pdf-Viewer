package com.rajat.pdfviewer.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.rajat.pdfviewer.HeaderData
import com.rajat.pdfviewer.PdfRendererView
import com.rajat.pdfviewer.PdfSource

@Composable
fun PdfRendererViewCompose(
    source: PdfSource,
    modifier: Modifier = Modifier,
    headers: HeaderData = HeaderData(),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    statusCallBack: PdfRendererView.StatusCallBack? = null
) {
    AndroidView(
        factory = { context -> PdfRendererView(context) },
        update = { view ->
            with(view) {
                if (statusCallBack != null) {
                    statusListener = statusCallBack
                }

                when (source) {
                    is PdfSource.LocalFile -> initWithFile(source.file)
                    is PdfSource.LocalUri -> initWithUri(source.uri)
                    is PdfSource.Remote -> initWithUrl(
                        source.url,
                        headers,
                        lifecycleOwner.lifecycleScope,
                        lifecycleOwner.lifecycle
                    )
                }
            }
        },
        modifier = modifier
    )
}
