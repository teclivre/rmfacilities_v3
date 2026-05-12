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
        webView.webViewClient = WebViewClient()

        // Mapa com CartoDB Voyager (tiles livres para apps)
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
              body { margin:0; padding:0; }
              #map { width:100vw; height:100vh; }
            </style>
            </head>
            <body>
            <div id="map"></div>
            <script>
              var map = L.map('map').setView([$lat, $lon], 17);
              L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                attribution: '&copy; <a href="https://carto.com/">CARTO</a> &copy; <a href="https://openstreetmap.org">OSM</a>',
                subdomains: 'abcd',
                maxZoom: 20
              }).addTo(map);
              var marker = L.marker([$lat, $lon]).addTo(map);
              marker.bindPopup('<b>$tipo</b><br>$hora').openPopup();
            </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://openstreetmap.org", html, "text/html", "UTF-8", null)
    }
}
