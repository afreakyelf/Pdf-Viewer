package com.rajat.pdfviewer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.rajat.pdfviewer.util.DownloadStatus

class PdfViewerViewModel : ViewModel() {

    private val _downloadStatus = MutableLiveData<DownloadStatus>()
    val downloadStatus: LiveData<DownloadStatus> = _downloadStatus

}
