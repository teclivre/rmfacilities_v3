package br.com.rmfacilities.funcionarioapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class PrivacyPolicyActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "privacy_url"
        const val EXTRA_TITLE = "privacy_title"
    }

    private lateinit var webView: WebView
    private lateinit var tvLoading: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        findViewById<TextView>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnRecarregarPolitica).setOnClickListener { webView.reload() }

        intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }?.let {
            findViewById<TextView>(R.id.tvTituloPrivacy).text = it
        }

        webView = findViewById(R.id.webPrivacy)
        tvLoading = findViewById(R.id.tvLoadingPrivacy)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                tvLoading.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }
        }

        val base = (SessionManager(this).apiBaseUrl.ifBlank { BuildConfig.DEFAULT_API_BASE_URL }).trimEnd('/')
        val url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.startsWith("https://") }
            ?: "$base/politica-de-privacidade"

        tvLoading.visibility = View.VISIBLE
        webView.visibility = View.INVISIBLE
        webView.loadUrl(url)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
