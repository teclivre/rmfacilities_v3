package br.com.rmfacilities.funcionarioapp

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class PontoMapaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val EXTRA_HORA = "hora"
        const val EXTRA_TIPO = "tipo"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ponto_mapa)

        val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
        val lon = intent.getDoubleExtra(EXTRA_LON, 0.0)
        val hora = intent.getStringExtra(EXTRA_HORA) ?: "--:--"
        val tipo = intent.getStringExtra(EXTRA_TIPO) ?: "Marcação"

        findViewById<TextView>(R.id.btnVoltarMapa).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvMapaTitulo).text = "$tipo — $hora"
        findViewById<TextView>(R.id.tvMapaSubtitulo).text = "%.6f, %.6f".format(lat, lon)

        findViewById<MaterialButton>(R.id.btnAbrirGoogleMaps).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:$lat,$lon?q=$lat,$lon($tipo)&z=17")))
            } catch (_: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://maps.google.com/?q=$lat,$lon")))
            }
        }

        val webView = findViewById<WebView>(R.id.webViewMapa)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.webViewClient = WebViewClient()

        // Embed OSM via iframe (permitido pela política de uso do OSM para embeds)
        // bbox: lon_min, lat_min, lon_max, lat_max
        val delta = 0.002
        val bboxLonMin = lon - delta
        val bboxLatMin = lat - delta
        val bboxLonMax = lon + delta
        val bboxLatMax = lat + delta
        val embedUrl = "https://www.openstreetmap.org/export/embed.html" +
            "?bbox=$bboxLonMin,$bboxLatMin,$bboxLonMax,$bboxLatMax" +
            "&layer=mapnik&marker=$lat,$lon"

        webView.loadUrl(embedUrl)
    }
}
