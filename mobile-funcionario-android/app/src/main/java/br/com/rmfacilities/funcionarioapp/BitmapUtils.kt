package br.com.rmfacilities.funcionarioapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Decodifica um ByteArray para Bitmap usando amostragem (inSampleSize),
 * evitando alocações excessivas de memória para imagens grandes.
 *
 * @param bytes  dados brutos da imagem
 * @param reqWidth  largura alvo em pixels
 * @param reqHeight altura alvo em pixels
 */
fun decodeSampledBitmap(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
    val opts = BitmapFactory.Options()
    // Primeira passagem: apenas dimensões, sem alocar pixels
    opts.inJustDecodeBounds = true
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

    // Calcular inSampleSize para caber dentro de reqWidth x reqHeight
    opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight)
    opts.inJustDecodeBounds = false

    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

private fun calculateInSampleSize(opts: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height, width) = opts.outHeight to opts.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfH = height / 2
        val halfW = width / 2
        while ((halfH / inSampleSize) >= reqHeight && (halfW / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
