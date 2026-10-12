package com.lgcns.codeengine.data.repository.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaRepository {

    override suspend fun saveToPublicMedia(
        source: ByteArray, relativePath: String, filename: String
    ): String? {
        try {
            val mediaType = filename.getMediaType()
            if (mediaType.isNullOrEmpty()) {
                throw IllegalArgumentException("Must be include extension in the filename.")
            }
            val destUri = withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToPublicMediaApi29(source, relativePath, filename, mediaType)
                } else {
                    saveToPublicMediaApi8to28(source, relativePath, filename, mediaType)
                }
            }
            return destUri?.toString()
        } catch (e: IOException) {
            Timber.e("Failed to write to public pictures. ${e.message}")
            return null
        }
    }

    override suspend fun saveToAppCache(
        source: ByteArray, relativePath: String, filename: String
    ): File? {
        return try {
            val mediaType = filename.getMediaType()
            if (mediaType.isNullOrEmpty()) {
                throw IllegalArgumentException("Must be include extension in the filename.")
            }

            val destDir = context.cacheDir.absolutePath.appendRelativePath(relativePath)
            val destFile = File(destDir, filename)
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(source)

            destFile
        } catch (e: IOException) {
            Timber.e("Failed to write to app cache. ${e.message}")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToPublicMediaApi29(
        source: ByteArray, relativePath: String, filename: String, mediaType: String,
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mediaType)

            if (mediaType.startsWith("video/")) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES.appendRelativePath(relativePath)
                )
            } else if (mediaType.startsWith("image/")) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES.appendRelativePath(relativePath)
                )
            } else {
                Timber.e("Unsupported mediaType: $mediaType")
                return null
            }
        }
        var destUri: Uri? = null
        if (mediaType.startsWith("video/")) destUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        else if (mediaType.startsWith("image/")) destUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        destUri?.writeBytes(source)

        Timber.d("Write to public pictures api 29. uri=$destUri")
        return destUri
    }

    private suspend fun saveToPublicMediaApi8to28(
        source: ByteArray, relativePath: String, filename: String, mediaType: String,
    ): Uri? {

        var saveLocation: String? = null
        if (mediaType.startsWith("video/")) saveLocation = Environment.DIRECTORY_MOVIES
        else if (mediaType.startsWith("image/")) saveLocation = Environment.DIRECTORY_PICTURES

        @Suppress("DEPRECATION") // API level 28 까지만 사용
        val destDir = Environment.getExternalStoragePublicDirectory(
            saveLocation?.appendRelativePath(relativePath)
        )
        val destFile = newNonDuplicateFile(destDir, filename)
        destFile.parentFile?.mkdirs()
        destFile.writeBytes(source)
        return scanFileSync(destFile.toString(), mediaType).also {
            Timber.d("Write to public pictures api 8~28. uri=$it, file=${destFile}")
        }
    }

    private fun String.appendRelativePath(source: String) =
        if (source.isNotEmpty()) "$this/$source" else this

    private fun String.getMediaType(): String? {
        val encoded: String = try {
            URLEncoder.encode(this, "UTF-8").replace("+", "%20")
        } catch (e: Exception) {
            this
        }

        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            MimeTypeMap.getFileExtensionFromUrl(encoded)
        )
    }

    private fun Uri.writeBytes(source: ByteArray) =
        context.contentResolver.openOutputStream(this)?.use { it.write(source) }

    private suspend fun scanFileSync(path: String, mediaType: String) =
        suspendCoroutine<Uri?> { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mediaType)) { _, uri ->
                cont.resume(uri)
            }
        }

    private fun newNonDuplicateFile(destDir: File, filename: String): File {
        var resultFile = File(destDir, filename)
        val name = resultFile.nameWithoutExtension
        val extension = resultFile.extension
        var seed = 0
        while (resultFile.exists()) {
            seed++
            resultFile = File(destDir, name + "_$seed.$extension")
        }
        return resultFile
    }

}