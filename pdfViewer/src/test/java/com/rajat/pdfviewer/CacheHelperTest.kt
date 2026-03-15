package com.rajat.pdfviewer

import com.rajat.pdfviewer.util.CacheHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheHelperTest {

    @Test
    fun `same file path produces the same cache key`() {
        val path = "/storage/emulated/0/documents/10.pdf"
        assertEquals(CacheHelper.getCacheKey(path), CacheHelper.getCacheKey(path))
    }

    @Test
    fun `different paths with same filename produce different cache keys`() {
        val path1 = "/storage/emulated/0/dir1/10.pdf"
        val path2 = "/storage/emulated/0/dir2/10.pdf"
        assertNotEquals(CacheHelper.getCacheKey(path1), CacheHelper.getCacheKey(path2))
    }

    @Test
    fun `file prefix is applied to local file paths`() {
        val key = CacheHelper.getCacheKey("/some/path/document.pdf")
        assertTrue("Expected key to start with 'file_' but was: $key", key.startsWith("file_"))
    }

    @Test
    fun `url prefix is applied to http and https urls`() {
        val httpsKey = CacheHelper.getCacheKey("https://example.com/document.pdf")
        val httpKey = CacheHelper.getCacheKey("http://example.com/document.pdf")
        assertTrue("Expected https key to start with 'url_' but was: $httpsKey", httpsKey.startsWith("url_"))
        assertTrue("Expected http key to start with 'url_' but was: $httpKey", httpKey.startsWith("url_"))
    }

    @Test
    fun `different urls produce different cache keys`() {
        val url1 = "https://example.com/dir1/10.pdf"
        val url2 = "https://example.com/dir2/10.pdf"
        assertNotEquals(CacheHelper.getCacheKey(url1), CacheHelper.getCacheKey(url2))
    }
}
