package br.com.rmfacilities.funcionarioapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class WebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITULO = "titulo"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_view)

        val rawUrl = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        // Item 1: rejeitar URLs não-HTTPS para evitar carregamento de conteúdo não confiável
        if (!rawUrl.startsWith("https://")) { finish(); return }
        val url = rawUrl
        val titulo = intent.getStringExtra(EXTRA_TITULO) ?: "Artigo"

        val tvTitulo = findViewById<TextView>(R.id.tvWebViewTitulo)
        val webView = findViewById<WebView>(R.id.webView)
        val progressBar = findViewById<ProgressBar>(R.id.progressWebView)
        val btnVoltar = findViewById<MaterialButton>(R.id.btnVoltarWebView)

        tvTitulo.text = titulo
        btnVoltar.setOnClickListener { finish() }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            // Item 1: bloquear conteúdo misto (HTTP dentro de HTTPS)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // Item 1: bloquear navegação para URLs não-HTTPS
                val uri = request?.url?.toString() ?: return true
                if (!uri.startsWith("https://")) return true
                return false
            }
        }

        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        val webView = findViewById<WebView>(R.id.webView)
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // Item 5: liberar memória do WebView ao destruir a Activity
    override fun onDestroy() {
        val webView = findViewById<WebView>(R.id.webView)
        webView?.destroy()
        super.onDestroy()
    }
}
