package com.rajat.pdfviewer

import com.rajat.pdfviewer.util.CachePolicy
import com.rajat.pdfviewer.util.CacheStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachePolicyTest {

    @Test
    fun `maximize performance retains remote files and disk bitmap cache`() {
        val policy = CachePolicy.from(CacheStrategy.MAXIMIZE_PERFORMANCE, maxRetainedDocuments = 7)

        assertTrue(policy.reuseRemoteFile)
        assertTrue(policy.persistRemoteFile)
        assertEquals(7, policy.maxRetainedDocuments)
        assertTrue(policy.useMemoryBitmapCache)
        assertTrue(policy.useDiskBitmapCache)
        assertTrue(policy.enablePrefetch)
    }

    @Test
    fun `minimize cache keeps one document and avoids disk bitmap cache`() {
        val policy = CachePolicy.from(CacheStrategy.MINIMIZE_CACHE)

        assertTrue(policy.reuseRemoteFile)
        assertTrue(policy.persistRemoteFile)
        assertEquals(1, policy.maxRetainedDocuments)
        assertTrue(policy.useMemoryBitmapCache)
        assertFalse(policy.useDiskBitmapCache)
        assertTrue(policy.enablePrefetch)
    }

    @Test
    fun `disable cache disables persistence and prefetch`() {
        val policy = CachePolicy.from(CacheStrategy.DISABLE_CACHE)

        assertFalse(policy.reuseRemoteFile)
        assertFalse(policy.persistRemoteFile)
        assertEquals(0, policy.maxRetainedDocuments)
        assertTrue(policy.useMemoryBitmapCache)
        assertFalse(policy.useDiskBitmapCache)
        assertFalse(policy.enablePrefetch)
    }
}
