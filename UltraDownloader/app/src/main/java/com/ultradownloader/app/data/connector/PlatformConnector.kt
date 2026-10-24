package com.ultradownloader.app.data.connector

import com.ultradownloader.app.domain.model.MediaAnalysisResult
import com.ultradownloader.app.domain.model.PlatformType
import java.net.URI

/**
 * Modular, independent platform connector contract.
 * Each platform (YouTube, TikTok, Instagram, Direct Media) implements this contract in an isolated class
 * so platform updates never require rewriting the core application.
 */
interface PlatformConnector {
    val platform: PlatformType
    fun supports(uri: URI): Boolean
    suspend fun analyze(uri: URI): MediaAnalysisResult
}
