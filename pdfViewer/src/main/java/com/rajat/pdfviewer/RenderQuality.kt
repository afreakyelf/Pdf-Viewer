package com.rajat.pdfviewer

enum class RenderQuality(val qualityMultiplier: Float) {
    NORMAL(qualityMultiplier = 1f), HIGH(qualityMultiplier = 2f), ULTRA(qualityMultiplier = 3f)
}