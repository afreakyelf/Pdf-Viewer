package com.rajat.pdfviewer.util

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.rajat.pdfviewer.R

/**
 * Encapsulates all theming options for the PDF Viewer Toolbar
 */
data class ToolbarStyle(
    val showToolbar: Boolean,
    val toolbarColor: Int,
    val backIcon: android.graphics.drawable.Drawable?,
    val titleTextStyle: Int,
    val titleBehavior: ToolbarTitleBehavior,
    val downloadIconTint: Int
) {
    fun applyTo(toolbar: Toolbar, titleView: TextView) {
        toolbar.setBackgroundColor(toolbarColor)
        toolbar.navigationIcon = backIcon
        toolbar.visibility = if (showToolbar) Toolbar.VISIBLE else Toolbar.GONE

        try {
            TextViewCompat.setTextAppearance(titleView, titleTextStyle)
        } catch (e: Exception) {
            TextViewCompat.setTextAppearance(titleView, R.style.pdfView_titleTextAppearance)
        }

        titleView.apply {
            setSingleLine(titleBehavior.isSingleLine)
            maxLines = titleBehavior.maxLines
            ellipsize = titleBehavior.ellipsize
            if (titleBehavior.ellipsize == TextUtils.TruncateAt.MARQUEE) {
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()
            }
        }
    }

    companion object {
        fun from(context: Context, intent: android.content.Intent): ToolbarStyle {
            val typedArray = context.theme.obtainStyledAttributes(
                R.styleable.PdfRendererView_toolbar
            )

            val showToolbar = ThemeUtils.getBooleanFromTypedArray(
                typedArray, R.styleable.PdfRendererView_toolbar_pdfView_showToolbar, true
            )

            val backIcon = typedArray.getDrawable(
                R.styleable.PdfRendererView_toolbar_pdfView_backIcon
            ) ?: ContextCompat.getDrawable(context, R.drawable.pdf_viewer_ic_arrow_back)

            val toolbarColor = ThemeUtils.getColorFromTypedArray(
                typedArray,
                R.styleable.PdfRendererView_toolbar_pdfView_toolbarColor,
                ContextCompat.getColor(context, R.color.pdf_viewer_primary)
            )

            val titleTextStyle = ThemeUtils.getResIdFromTypedArray(
                typedArray,
                R.styleable.PdfRendererView_toolbar_pdfView_titleTextStyle,
                R.style.pdfView_titleTextAppearance
            )

            val behaviorIndex = intent.getIntExtra("title_behavior", -1).takeIf { it != -1 }
                ?: typedArray.getInt(
                    R.styleable.PdfRendererView_toolbar_pdfView_titleBehavior,
                    ToolbarTitleBehavior.MULTI_LINE_WRAP.ordinal
                )

            val downloadIconTint = ThemeUtils.getColorFromTypedArray(
                typedArray,
                R.styleable.PdfRendererView_toolbar_pdfView_downloadIconTint,
                ContextCompat.getColor(context, android.R.color.white)
            )

            typedArray.recycle()

            return ToolbarStyle(
                showToolbar = showToolbar,
                toolbarColor = toolbarColor,
                backIcon = backIcon,
                titleTextStyle = titleTextStyle,
                titleBehavior = ToolbarTitleBehavior.fromXmlValue(behaviorIndex),
                downloadIconTint = downloadIconTint
            )
        }
    }
}