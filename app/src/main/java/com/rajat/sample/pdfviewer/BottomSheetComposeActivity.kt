package com.rajat.sample.pdfviewer

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rajat.pdfviewer.compose.PdfRendererViewCompose
import com.rajat.pdfviewer.util.PdfSource
import com.rajat.sample.pdfviewer.ui.theme.AndroidpdfviewerTheme
import java.io.File

class BottomSheetComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pdfFile = createBottomSheetSamplePdf()

        setContent {
            AndroidpdfviewerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BottomSheetPdfViewerScreen(pdfFile)
                }
            }
        }
    }

    private fun createBottomSheetSamplePdf(): File {
        val file = File(cacheDir, "bottom_sheet_manual_test.pdf")
        if (file.exists()) return file

        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            isAntiAlias = true
        }

        val document = PdfDocument()
        repeat(8) { index ->
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
            val page = document.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawText("Bottom Sheet Test Page ${index + 1}", 72f, 120f, paint)
            page.canvas.drawText("Try dragging inside the PDF to scroll pages.", 72f, 180f, paint)
            page.canvas.drawText("Then drag outside it to move the sheet.", 72f, 240f, paint)
            document.finishPage(page)
        }

        file.outputStream().use { output ->
            document.writeTo(output)
        }
        document.close()
        return file
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetPdfViewerScreen(pdfFile: File) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    Box(modifier = Modifier.fillMaxSize()) {
        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = sheetState,
            dragHandle = {
                Text(
                    text = "Bottom sheet PDF repro",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            PdfRendererViewCompose(
                source = remember(pdfFile) { PdfSource.LocalFile(pdfFile) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
            )
        }
    }
}
