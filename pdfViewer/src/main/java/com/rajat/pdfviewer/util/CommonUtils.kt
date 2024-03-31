package com.rajat.pdfviewer.util

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import java.util.*

class CommonUtils {
    companion object {
        fun getAvailableMemory(context: Context): Long {
            val memoryInfo = ActivityManager.MemoryInfo()
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.getMemoryInfo(memoryInfo)
            return memoryInfo.availMem
        }
        fun estimatePdfComplexity(pdfRenderer: PdfRenderer): Float {
            return pdfRenderer.pageCount.toFloat()
        }
        fun calculateDynamicPrefetchCount(context: Context, pdfRenderer: PdfRenderer): Int {
            val availableMemory = getAvailableMemory(context)
            val pdfComplexity = estimatePdfComplexity(pdfRenderer)
            return when {
                availableMemory > 1024 * 1024 * 1024 && pdfComplexity < 100 -> 10 // 1GB of available memory and less than 100 pages
                availableMemory > 512 * 1024 * 1024 && pdfComplexity < 200 -> 5  // 512MB of available memory and less than 200 pages
                else -> 3 // Default fallback
            }
        }
        object BitmapPool1 {
            private val pool = LinkedList<Bitmap>()
            fun getBitmap(width: Int, height: Int): Bitmap {
                synchronized(pool) {
                    val iterator = pool.iterator()
                    while (iterator.hasNext()) {
                        val bitmap = iterator.next()
                        if (!bitmap.isRecycled && bitmap.width == width && bitmap.height == height) {
                            iterator.remove()
                            return bitmap
                        }
                    }
                }
                return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }
            fun recycleBitmap(bitmap: Bitmap) {
                if (!bitmap.isRecycled) {
                    synchronized(pool) {
                        // Optional: limit the pool size to avoid excessive memory use
                        if (pool.size < 10) {
                            pool.add(bitmap)
                        } else {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }

        object BitmapPool {
            private val pool = LinkedList<Bitmap>()
            private val maxPoolSize: Int
                get() = calculateMaxPoolSize()

            // Dynamically calculates the maximum size of the pool based on available memory.
            private fun calculateMaxPoolSize(): Int {
                // Example: Use 1/20th of the available heap size for the bitmap pool.
                val maxMemory = Runtime.getRuntime().maxMemory() / 1024 // Convert to KB
                return (maxMemory / (1024 * 20)).toInt() // Use 5% of available heap for pooling
            }

            fun getBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
                synchronized(pool) {
                    val iterator = pool.iterator()
                    while (iterator.hasNext()) {
                        val bitmap = iterator.next()
                        if (!bitmap.isRecycled && bitmap.width == width && bitmap.height == height && bitmap.config == config) {
                            iterator.remove()
                            return bitmap
                        }
                    }
                }
                // If no suitable bitmap is found in the pool, create a new one.
                return Bitmap.createBitmap(width, height, config)
            }

            fun recycleBitmap(bitmap: Bitmap) {
                if (!bitmap.isRecycled) {
                    synchronized(pool) {
                        // Limit the pool size to a dynamically calculated value.
                        if (pool.size < maxPoolSize) {
                            pool.add(bitmap)
                        } else {
                            // If the pool is at max capacity, recycle the bitmap to free memory.
                            bitmap.recycle()
                        }
                    }
                }
            }
        }


    }
}