package com.rajat.pdfviewer

import com.rajat.pdfviewer.util.CacheHelper
import com.rajat.pdfviewer.util.CachePolicy
import com.rajat.pdfviewer.util.CacheStrategy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CacheRetentionTest {

    @Test
    fun `minimize cache keeps only the current document folder`() {
        val root = Files.createTempDirectory("pdf-cache-min").toFile()
        try {
            createCacheDir(root, "oldest", 1L)
            val current = createCacheDir(root, "current", 2L)
            createCacheDir(root, "newest", 3L)

            CacheHelper.applyDocumentRetention(
                origin = "test",
                cacheDir = current,
                cachePolicy = CachePolicy.from(CacheStrategy.MINIMIZE_CACHE)
            )

            assertTrue(current.exists())
            assertFalse(File(root, "oldest").exists())
            assertFalse(File(root, "newest").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `maximize performance evicts only the oldest overflow folders`() {
        val root = Files.createTempDirectory("pdf-cache-max").toFile()
        try {
            createCacheDir(root, "oldest", 1L)
            createCacheDir(root, "middle", 2L)
            createCacheDir(root, "recent", 3L)
            val current = createCacheDir(root, "current", 4L)

            CacheHelper.applyDocumentRetention(
                origin = "test",
                cacheDir = current,
                cachePolicy = CachePolicy.from(
                    CacheStrategy.MAXIMIZE_PERFORMANCE,
                    maxRetainedDocuments = 3
                )
            )

            assertFalse(File(root, "oldest").exists())
            assertTrue(File(root, "middle").exists())
            assertTrue(File(root, "recent").exists())
            assertTrue(current.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `cleanup transient document removes file and empty session folder`() {
        val root = Files.createTempDirectory("pdf-cache-cleanup").toFile()
        try {
            val sessionDir = File(root, "session").apply { mkdirs() }
            val pdfFile = File(sessionDir, "sample.pdf").apply {
                writeText("pdf")
            }

            CacheHelper.cleanupTransientDocument(pdfFile)

            assertFalse(pdfFile.exists())
            assertFalse(sessionDir.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createCacheDir(root: File, name: String, modifiedAt: Long): File {
        return File(root, name).apply {
            mkdirs()
            setLastModified(modifiedAt)
        }
    }
}
