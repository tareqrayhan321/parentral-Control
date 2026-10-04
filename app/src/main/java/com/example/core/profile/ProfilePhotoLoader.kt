package com.example.core.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Loads the signed-in Google account's profile photo.
 *
 * Safety rules: https only, Google-owned image hosts only (also checked after redirects),
 * size-capped download, no credentials sent. Result is cached in memory and on disk so the
 * avatar also shows offline; the cache is wiped on sign-out.
 */
object ProfilePhotoLoader {
    private const val MAX_BYTES = 2L * 1024 * 1024
    private const val TARGET_PX = 256
    private const val DIR = "profile_photo"

    private val allowedHostSuffixes = listOf("googleusercontent.com", "ggpht.com", "gstatic.com")

    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    internal fun isAllowed(url: okhttp3.HttpUrl): Boolean =
        url.isHttps && allowedHostSuffixes.any { url.host == it || url.host.endsWith(".$it") }

    /** Google photo URLs end in "=sNN-c"; ask for a size that is sharp on a 52–64dp avatar. */
    internal fun sized(url: String): String =
        Regex("=s\\d+(-c)?$").replace(url) { "=s$TARGET_PX-c" }

    suspend fun load(context: Context, photoUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val url = sized(photoUrl)
        memory.get(url)?.let { return@withContext it }

        val file = File(File(context.cacheDir, DIR), sha(url))
        if (file.exists()) {
            decode(file.readBytes())?.let { memory.put(url, it); return@withContext it }
        }

        val httpUrl = url.toHttpUrlOrNull() ?: return@withContext null
        if (!isAllowed(httpUrl)) return@withContext null

        try {
            client.newCall(Request.Builder().url(httpUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful || !isAllowed(resp.request.url)) return@withContext null
                val body = resp.body ?: return@withContext null
                if (body.contentLength() > MAX_BYTES) return@withContext null
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_BYTES) return@withContext null
                    }
                }
                val bytes = out.toByteArray()
                val bmp = decode(bytes) ?: return@withContext null
                runCatching {
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                }
                memory.put(url, bmp)
                bmp
            }
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache(context: Context) {
        memory.evictAll()
        runCatching { File(context.cacheDir, DIR).deleteRecursively() }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_PX * 2) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
