package com.rajat.pdfviewer.util

import android.content.Context
import android.content.res.TypedArray
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat

object ThemeUtils {

    fun getColorFromAttr(
        context: Context,
        @AttrRes attrRes: Int,
        @ColorRes fallbackRes: Int
    ): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(attrRes, typedValue, true)) {
            typedValue.data
        } else {
            ContextCompat.getColor(context, fallbackRes)
        }
    }

    fun getResourceIdFromAttr(
        context: Context,
        @AttrRes attrRes: Int,
        @StyleRes fallbackRes: Int
    ): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(attrRes, typedValue, true)) {
            typedValue.resourceId
        } else {
            fallbackRes
        }
    }

    fun getBooleanFromTypedArray(
        typedArray: TypedArray,
        index: Int,
        default: Boolean = false
    ): Boolean = try {
        typedArray.getBoolean(index, default)
    } catch (e: Exception) {
        default
    }

    fun getColorFromTypedArray(
        typedArray: TypedArray,
        index: Int,
        fallbackColor: Int
    ): Int = try {
        typedArray.getColor(index, fallbackColor)
    } catch (e: Exception) {
        fallbackColor
    }

    fun getResIdFromTypedArray(
        typedArray: TypedArray,
        index: Int,
        fallbackResId: Int
    ): Int = try {
        typedArray.getResourceId(index, fallbackResId)
    } catch (e: Exception) {
        fallbackResId
    }
}
