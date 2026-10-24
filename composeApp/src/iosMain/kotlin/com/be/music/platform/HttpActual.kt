package com.be.music.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

actual object SimpleHttp {
    private val client = HttpClient(Darwin) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 20000
        }
        followRedirects = true
        expectSuccess = false
    }

    actual suspend fun get(url: String, timeoutMs: Long): String? = try {
        val resp = client.get(url) {
            header(HttpHeaders.UserAgent, "Mozilla/5.0")
            timeout { requestTimeoutMillis = timeoutMs }
        }
        if (resp.status.value in 200..299) resp.bodyAsText() else null
    } catch (e: Exception) {
        null
    }

    actual suspend fun post(url: String, body: String, contentType: String, timeoutMs: Long): String? = try {
        val resp = client.post(url) {
            header(HttpHeaders.UserAgent, "Mozilla/5.0")
            header(HttpHeaders.ContentType, contentType)
            setBody(body)
            timeout { requestTimeoutMillis = timeoutMs }
        }
        if (resp.status.value in 200..299) resp.bodyAsText() else null
    } catch (e: Exception) {
        null
    }

    actual suspend fun getWithFollowRedirects(url: String, timeoutMs: Long): String? = try {
        val resp = client.get(url) {
            header(HttpHeaders.UserAgent, "Mozilla/5.0")
            timeout { requestTimeoutMillis = timeoutMs }
        }
        if (resp.status.value in 200..299) resp.bodyAsText() else null
    } catch (e: Exception) {
        null
    }
}
