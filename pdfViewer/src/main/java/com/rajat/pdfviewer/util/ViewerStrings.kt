package com.rajat.pdfviewer.util

import android.content.Context
import com.rajat.pdfviewer.R
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Encapsulates all string resources used in PdfViewerActivity, with theme override support.
 */
data class ViewerStrings(
    val errorPdfCorrupted: String,
    val errorNoInternet: String,
    val fileSavedSuccessfully: String,
    val fileSavedToDownloads: String,
    val fileNotDownloadedYet: String,
    val permissionRequired: String,
    val permissionRequiredTitle: String,
    val genericError: String,
    val retry: String,
    val cancel: String,
    val grant: String,
    val errorDialogTitle: String
) {
    companion object {
        fun from(context: Context): ViewerStrings {
            val typedArray = context.obtainStyledAttributes(
                R.styleable.PdfRendererView_Strings
            )

            fun getString(index: Int, fallback: Int): String =
                typedArray.getString(index) ?: context.getString(fallback)

            val result = ViewerStrings(
                errorPdfCorrupted = getString(
                    R.styleable.PdfRendererView_Strings_error_pdf_corrupted,
                    R.string.error_pdf_corrupted
                ),
                errorNoInternet = getString(
                    R.styleable.PdfRendererView_Strings_error_no_internet_connection,
                    R.string.error_no_internet_connection
                ),
                fileSavedSuccessfully = getString(
                    R.styleable.PdfRendererView_Strings_file_saved_successfully,
                    R.string.file_saved_successfully
                ),
                fileSavedToDownloads = getString(
                    R.styleable.PdfRendererView_Strings_file_saved_to_downloads,
                    R.string.file_saved_to_downloads
                ),
                fileNotDownloadedYet = getString(
                    R.styleable.PdfRendererView_Strings_file_not_downloaded_yet,
                    R.string.file_not_downloaded_yet
                ),
                permissionRequired = getString(
                    R.styleable.PdfRendererView_Strings_permission_required,
                    R.string.permission_required
                ),
                permissionRequiredTitle = getString(
                    R.styleable.PdfRendererView_Strings_permission_required_title,
                    R.string.permission_required_title
                ),
                genericError = getString(
                    R.styleable.PdfRendererView_Strings_pdf_viewer_error,
                    R.string.pdf_viewer_error
                ),
                retry = getString(
                    R.styleable.PdfRendererView_Strings_pdf_viewer_retry,
                    R.string.pdf_viewer_retry
                ),
                cancel = getString(
                    R.styleable.PdfRendererView_Strings_pdf_viewer_cancel,
                    R.string.pdf_viewer_cancel
                ),
                grant = getString(
                    R.styleable.PdfRendererView_Strings_pdf_viewer_grant,
                    R.string.pdf_viewer_grant
                ),
                errorDialogTitle = getString(
                    R.styleable.PdfRendererView_Strings_pdf_viewer_error_dialog_title,
                    R.string.pdf_viewer_error_dialog_title
                )
            )

            typedArray.recycle()
            return result
        }

        fun ViewerStrings.getMessageForError(error: Throwable): String {
            return when {
                error is UnknownHostException -> errorNoInternet
                error is SocketTimeoutException -> "Network timeout! Please check your connection."
                error is FileNotFoundException -> "File not found on the server."
                error.message?.contains("Invalid content type received") == true ->
                    "The server returned a non-PDF file. Please check the URL."
                error.message?.contains("Downloaded file is not a valid PDF") == true ->
                    "The file appears to be corrupted or is not a valid PDF."
                error.message?.contains("Incomplete download") == true ->
                    "The download was incomplete. Please check your internet connection and try again."
                error.message?.contains("Failed to download after") == true ->
                    "Failed to download the PDF after multiple attempts. Please check your internet connection."
                else -> "An unexpected error occurred: ${error.localizedMessage}"
            }
        }

    }
}