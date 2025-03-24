package com.rajat.pdfviewer.util

import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

object EdgeToEdgeHelper {

    fun applyInsets(
        window: Window,
        rootView: View,
        darkMode: Boolean,
        onInsetsApplied: ((WindowInsetsCompat) -> Unit)? = null
    ) {
        val controller = WindowInsetsControllerCompat(window, rootView).apply {
            isAppearanceLightStatusBars = !darkMode
            isAppearanceLightNavigationBars = !darkMode
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            // Only apply padding if necessary
            if (bars.top > 0 || bars.bottom > 0 || bars.left > 0 || bars.right > 0) {
                v.updatePadding(
                    top = bars.top,
                    bottom = bars.bottom,
                    left = bars.left,
                    right = bars.right
                )
            }

            onInsetsApplied?.invoke(insets)
            WindowInsetsCompat.CONSUMED
        }
    }

    fun isDarkModeEnabled(configUiMode: Int): Boolean {
        return (configUiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
