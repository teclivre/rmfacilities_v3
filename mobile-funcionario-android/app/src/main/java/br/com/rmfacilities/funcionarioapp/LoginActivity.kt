package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {
    private lateinit var session: SessionManager
    private lateinit var api: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        session = SessionManager(this)
        api = ApiClient(session)

        if (session.accessToken.isNotBlank()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        val etApiUrl = findViewById<TextInputEditText>(R.id.etApiUrl)
        val etCpf = findViewById<TextInputEditText>(R.id.etCpf)
        val etSenha = findViewById<TextInputEditText>(R.id.etSenha)
        val btnEntrar = findViewById<MaterialButton>(R.id.btnEntrar)
        val progress = findViewById<ProgressBar>(R.id.progressLogin)
        val tvErro = findViewById<TextView>(R.id.tvErro)

        etApiUrl.setText(session.apiBaseUrl)

        btnEntrar.setOnClickListener {
            val apiBase = etApiUrl.text?.toString()?.trim().orEmpty()
            val cpf = etCpf.text?.toString()?.replace("\\D".toRegex(), "").orEmpty()
            val senha = etSenha.text?.toString().orEmpty()

            if (apiBase.isBlank() || cpf.isBlank() || senha.isBlank()) {
                tvErro.text = "Preencha URL da API, CPF e senha."
                return@setOnClickListener
            }

            session.apiBaseUrl = apiBase
            progress.visibility = View.VISIBLE
            btnEntrar.isEnabled = false
            tvErro.text = ""

            CoroutineScope(Dispatchers.IO).launch {
                val resp = try {
                    api.login(cpf, senha)
                } catch (e: Exception) {
                    LoginResponse(ok = false, erro = e.message ?: "Erro de conexão")
                }

                withContext(Dispatchers.Main) {
                    progress.visibility = View.GONE
                    btnEntrar.isEnabled = true
                    if (resp.ok && !resp.access_token.isNullOrBlank()) {
                        session.accessToken = resp.access_token
                        session.refreshToken = resp.refresh_token ?: ""
                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                        finish()
                    } else {
                        tvErro.text = resp.erro ?: "Falha no login"
                    }
                }
            }
        }
    }
}
