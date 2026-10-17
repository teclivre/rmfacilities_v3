package br.com.rmfacilities.funcionarioapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView

/**
 * Decodifica um ByteArray para Bitmap usando amostragem (inSampleSize),
 * evitando alocações excessivas de memória para imagens grandes.
 *
 * @param bytes  dados brutos da imagem
 * @param reqWidth largura de destino em pixels
 * @param reqHeight altura de destino em pixels
 */
fun decodeSampledBitmap(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
    if (reqWidth <= 0 || reqHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null

    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

fun ImageView.bitmapTargetSize(): Pair<Int, Int>? {
    val params = layoutParams ?: return null
    val targetWidth = width.takeIf { it > 0 } ?: params.width.takeIf { it > 0 } ?: return null
    val targetHeight = height.takeIf { it > 0 } ?: params.height.takeIf { it > 0 } ?: return null
    return targetWidth to targetHeight
}

private fun calculateInSampleSize(opts: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1
    if (opts.outHeight > reqHeight || opts.outWidth > reqWidth) {
        while (
            inSampleSize <= Int.MAX_VALUE / 2 &&
            opts.outHeight / (inSampleSize * 2) >= reqHeight &&
            opts.outWidth / (inSampleSize * 2) >= reqWidth
        ) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
