package com.rajat.sample.pdfviewer

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rajat.pdfviewer.PdfRendererView
import com.rajat.pdfviewer.compose.PdfRendererViewCompose
import com.rajat.sample.pdfviewer.ui.theme.AndroidpdfviewerTheme
import java.io.File

class ComposeActivity : ComponentActivity() {

    private var download_file_url = "https://css4.pub/2015/usenix/example.pdf"
    private var download_file_url1 = "https://css4.pub/2017/newsletter/drylab.pdf"
    private var download_file_url2 = "https://css4.pub/2015/textbook/somatosensory.pdf"
    private var download_file_url3 =
        "https://file-examples.com/storage/fe19e15eac6560f8c936c41/2017/10/file-example_PDF_1MB.pdf"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidpdfviewerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MyPdfScreenFromUri(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun MyPdfScreenFromUri(modifier: Modifier = Modifier) {
    val (uri, setUri) = rememberSaveable {
        mutableStateOf<Uri?>(null)
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = setUri
    )

    Column(modifier = modifier) {
        AnimatedContent(
            targetState = uri,
            label = "",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1F)
        ) {
            if (it != null) {
                MyPdfScreenFromUri(
                    uri = it,
                    modifier = Modifier
                        .fillMaxSize()
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Button(
                onClick = {
                    launcher.launch(arrayOf("application/pdf"))
                }
            ) {
                Text(text = "Pick PDF file")
            }
        }
    }
}

@Composable
fun MyPdfScreenFromUri(uri: Uri, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    PdfRendererViewCompose(
        modifier = modifier,
        uri = uri,
        lifecycleOwner = lifecycleOwner,
        statusCallBack = object : PdfRendererView.StatusCallBack {
            override fun onPdfLoadStart() {
                Log.i("statusCallBack", "onPdfLoadStart")
            }

            override fun onPdfLoadProgress(
                progress: Int,
                downloadedBytes: Long,
                totalBytes: Long?
            ) {
                //Download is in progress
            }

            override fun onPdfLoadSuccess(absolutePath: String) {
                Log.i("statusCallBack", "onPdfLoadSuccess")
            }

            override fun onError(error: Throwable) {
                Log.i("statusCallBack", "onError")
            }

            override fun onPageChanged(currentPage: Int, totalPage: Int) {
                //Page change. Not require
            }
        }
    )
}

@Composable
fun MyPdfScreenFromUrl(url: String, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    PdfRendererViewCompose(
        modifier = modifier,
        url = url,
        lifecycleOwner = lifecycleOwner,
        statusCallBack = object : PdfRendererView.StatusCallBack {
            override fun onPdfLoadStart() {
                Log.i("statusCallBack", "onPdfLoadStart")
            }

            override fun onPdfLoadProgress(
                progress: Int,
                downloadedBytes: Long,
                totalBytes: Long?
            ) {
                //Download is in progress
            }

            override fun onPdfLoadSuccess(absolutePath: String) {
                Log.i("statusCallBack", "onPdfLoadSuccess")
            }

            override fun onError(error: Throwable) {
                Log.i("statusCallBack", "onError")
            }

            override fun onPageChanged(currentPage: Int, totalPage: Int) {
                //Page change. Not require
            }
        }

    )
}

@Composable
fun MyPdfScreenFromFile() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val pdfFile = File("path/to/your/file.pdf")  // Replace with your file path
    PdfRendererViewCompose(
        file = pdfFile,
        lifecycleOwner = lifecycleOwner
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AndroidpdfviewerTheme {
        MyPdfScreenFromUrl("https://css4.pub/2015/textbook/somatosensory.pdf")
    }
}
