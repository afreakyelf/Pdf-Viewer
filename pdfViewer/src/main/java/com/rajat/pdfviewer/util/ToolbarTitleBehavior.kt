package com.rajat.pdfviewer.util

import android.text.TextUtils

enum class ToolbarTitleBehavior(
    val isSingleLine: Boolean,
    val maxLines: Int,
    val ellipsize: TextUtils.TruncateAt?
) {
    SINGLE_LINE_ELLIPSIS(true, 1, TextUtils.TruncateAt.END),    // Truncate long titles
    SINGLE_LINE_SCROLLABLE(true, 1, TextUtils.TruncateAt.MARQUEE),  // Scrolls title
    MULTI_LINE_ELLIPSIS(false, 2, TextUtils.TruncateAt.END),   // 2 lines max, truncates if needed
    MULTI_LINE_WRAP(false, Int.MAX_VALUE, null);  // **Default: Wraps fully without truncation**

    companion object {
        // Convert XML enum value to `ToolbarTitleBehavior`
        fun fromXmlValue(value: Int): ToolbarTitleBehavior {
            return when (value) {
                0 -> SINGLE_LINE_ELLIPSIS
                1 -> SINGLE_LINE_SCROLLABLE
                2 -> MULTI_LINE_ELLIPSIS
                3 -> MULTI_LINE_WRAP
                else -> MULTI_LINE_WRAP
            }
        }
    }
}
