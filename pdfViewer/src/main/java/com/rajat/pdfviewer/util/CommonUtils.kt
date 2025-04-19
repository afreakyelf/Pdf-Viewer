package com.rajat.pdfviewer.util

import android.graphics.Bitmap
import java.util.*

class CommonUtils {

    companion object {

        const val MAX_CACHED_PDFS = 5

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