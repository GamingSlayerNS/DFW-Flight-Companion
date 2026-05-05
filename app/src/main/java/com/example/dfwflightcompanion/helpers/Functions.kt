package com.example.dfwflightcompanion.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import android.graphics.drawable.Drawable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)

    val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return 6371000.0 * c
}

fun loadVectorToBitmap(context: Context, drawableId: Int, width: Int, height: Int): Bitmap {
    val drawable = ContextCompat.getDrawable(context, drawableId) ?:
        throw IllegalArgumentException("Resource ID $drawableId not found")

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    val canvas = Canvas(bitmap)

    drawable.setBounds(0, 0, canvas.width, canvas.height)

    drawable.draw(canvas)

    return bitmap
}
