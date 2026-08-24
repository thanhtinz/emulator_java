package com.mobicore.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Turns a picture the user picked into cover art the library will accept.
 *
 * The picker hands back whatever the camera or the gallery holds — usually
 * JPEG or HEIC — and the emulator decodes only PNG, because that is the one
 * format it can read on every platform it runs on, MIDP included. So the
 * picture is decoded here, once, and re-encoded.
 *
 * It is also squared off and bounded: a cover is shown at ninety pixels and a
 * modern photo is several thousand across, so storing it whole would waste
 * megabytes per game to no visible benefit.
 */
object Artwork {

    /** Longest edge kept. Twice what the largest tile shows, for sharpness. */
    private const val MAX_EDGE = 256

    fun pngFrom(context: Context, uri: Uri): ByteArray? = runCatching {
        val decoded = context.contentResolver.openInputStream(uri).use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null
        val squared = centreCrop(decoded)
        val scaled = if (squared.width > MAX_EDGE) {
            Bitmap.createScaledBitmap(squared, MAX_EDGE, MAX_EDGE, true)
        } else {
            squared
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }.getOrNull()

    /** The middle square of the picture, which is what a tile shows anyway. */
    private fun centreCrop(source: Bitmap): Bitmap {
        val edge = minOf(source.width, source.height)
        if (edge == source.width && edge == source.height) return source
        return Bitmap.createBitmap(
            source,
            (source.width - edge) / 2,
            (source.height - edge) / 2,
            edge,
            edge,
        )
    }
}
