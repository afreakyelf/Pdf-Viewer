package com.rajat.sample.pdfviewer

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rajat.pdfviewer.PdfRendererView
import com.rajat.pdfviewer.compose.PdfRendererViewCompose
import com.rajat.pdfviewer.util.PdfSource
import com.rajat.sample.pdfviewer.ui.theme.AndroidpdfviewerTheme
import java.io.File

class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidpdfviewerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MyPdfScreenWithZoomControls(
                        modifier = Modifier.systemBarsPadding(),
                        url = "https://source.android.com/docs/compatibility/5.0/android-5.0-cdd.pdf"
                    )
                }
            }
        }
    }
}

@Composable
private fun MyPdfScreenWithZoomControls(url: String, modifier: Modifier = Modifier) {
    var pdfRendererView by remember { mutableStateOf<PdfRendererView?>(null) }
    var currentScale by remember { mutableFloatStateOf(1f) }
    var isZoomedIn by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        PdfRendererViewCompose(
            source = remember(url) { PdfSource.Remote(url) },
            modifier = Modifier.fillMaxSize(),
            onReady = { view ->
                pdfRendererView = view
            },
            statusCallBack = remember {
                object : PdfRendererView.StatusCallBack {
                    override fun onPdfLoadSuccess(absolutePath: String) {
                        Log.i("PDF", "Loaded: $absolutePath")
                    }
                    override fun onError(error: Throwable) {
                        Log.e("PDF", "Error: ${error.message}")
                    }
                }
            },
            zoomListener = remember {
                object : PdfRendererView.ZoomListener {
                    override fun onZoomChanged(zoomed: Boolean, scale: Float) {
                        isZoomedIn = zoomed
                        currentScale = scale
                        Log.i("PDF Zoom", "Zoomed in: $zoomed, Scale: $scale")
                    }
                }
            }
        )

        // Floating Action Buttons for Zoom
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zoom In
            FloatingActionButton(
                onClick = { pdfRendererView?.zoomIn() },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            // Zoom Out
            FloatingActionButton(
                onClick = { pdfRendererView?.zoomOut() },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                // Using a custom painter or a representative icon for Zoom Out
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                    contentDescription = "Zoom Out",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Reset Zoom (Visible only when zoomed)
            if (isZoomedIn) {
                FloatingActionButton(
                    onClick = { pdfRendererView?.resetZoom() },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Zoom")
                }
            }
        }

        // Optional: Display current scale
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Scale: ${"%.2f".format(currentScale)}x",
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun MyPdfScreenFromUrl(url: String, modifier: Modifier = Modifier) {
    PdfRendererViewCompose(
        source = remember(url) { PdfSource.Remote(url) },
        modifier = modifier,
        jumpToPage = 4,
        statusCallBack = remember {
            object : PdfRendererView.StatusCallBack {
                override fun onPdfLoadStart() {
                    Log.i("statusCallBack", "onPdfLoadStart")
                }

                override fun onPdfLoadProgress(
                    progress: Int,
                    downloadedBytes: Long,
                    totalBytes: Long?
                ) {
                    Log.i("statusCallBack", "onPdfLoadProgress: $progress")
                }

                override fun onPdfLoadSuccess(absolutePath: String) {
                    Log.i("statusCallBack", "onPdfLoadSuccess: $absolutePath")
                }

                override fun onError(error: Throwable) {
                    Log.e("statusCallBack", "onError: ${error.message}")
                }

                override fun onPageChanged(currentPage: Int, totalPage: Int) {
                    Log.i("statusCallBack", "onPageChanged: $currentPage / $totalPage")
                }

                override fun onPdfRenderStart() {
                    Log.i("statusCallBack", "onPdfRenderStart")
                }

                override fun onPdfRenderSuccess() {
                    Log.i("statusCallBack", "onPdfRenderSuccess")
                }
            }
        },
        zoomListener = remember {
            object : PdfRendererView.ZoomListener {
                override fun onZoomChanged(isZoomedIn: Boolean, scale: Float) {
                    Log.i("PDF Zoom", "Zoomed in: $isZoomedIn, Scale: $scale")
                }
            }
        }
    )
}

@Composable
private fun MyPdfScreenFromUri(modifier: Modifier = Modifier) {
    val (uri, setUri) = remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        setUri(it)
    }

    Column(modifier = modifier) {
        AnimatedContent(
            targetState = uri,
            label = "",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { selectedUri ->
            selectedUri?.let {
                PdfRendererViewCompose(
                    source = PdfSource.LocalUri(it),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Button(
            onClick = { launcher.launch(arrayOf("application/pdf")) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text("Pick PDF File")
        }
    }
}

@Composable
private fun MyPdfScreenFromAsset(modifier: Modifier = Modifier) {
    PdfRendererViewCompose(
        source = PdfSource.PdfSourceFromAsset("quote.pdf"),
        modifier = modifier
    )
}

@Composable
private fun MyPdfScreenFromFile() {
    val pdfFile = File("path/to/your/file.pdf") // Replace with actual path
    PdfRendererViewCompose(
        source = PdfSource.LocalFile(pdfFile)
    )
}

@Preview(showBackground = true)
@Composable
private fun MyPdfScreenFromUrlPreview() {
    AndroidpdfviewerTheme {
        MyPdfScreenWithZoomControls("https://css4.pub/2015/textbook/somatosensory.pdf")
    }
}
