package br.com.rmfacilities.funcionarioapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PasswordResetActivity : AppCompatActivity() {
    private lateinit var api: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_reset)
        EdgeToEdgeUtils.apply(this)

        api = ApiClient(SessionManager(this))
        val etCpf = findViewById<TextInputEditText>(R.id.etResetCpf)
        val etPhone = findViewById<TextInputEditText>(R.id.etResetPhone)
        val tvStatus = findViewById<TextView>(R.id.tvResetStatus)
        val progress = findViewById<ProgressBar>(R.id.progressReset)
        val btnSend = findViewById<MaterialButton>(R.id.btnResetSend)

        etCpf.addTextChangedListener(object : TextWatcher {
            private var formatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (formatting || s == null) return
                formatting = true
                val digits = s.toString().filter(Char::isDigit).take(11)
                val formatted = buildString {
                    digits.forEachIndexed { index, char ->
                        if (index == 3 || index == 6) append('.')
                        if (index == 9) append('-')
                        append(char)
                    }
                }
                s.replace(0, s.length, formatted)
                formatting = false
            }
        })

        findViewById<MaterialButton>(R.id.btnResetBack).setOnClickListener { finish() }
        btnSend.setOnClickListener {
            val cpf = etCpf.text?.toString()?.filter(Char::isDigit).orEmpty()
            val phone = etPhone.text?.toString()?.filter(Char::isDigit).orEmpty()
            if (cpf.length != 11) {
                tvStatus.text = "Informe o CPF com 11 dígitos."
                tvStatus.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (phone.length !in 10..15) {
                tvStatus.text = "Informe o celular cadastrado com DDD."
                tvStatus.visibility = View.VISIBLE
                return@setOnClickListener
            }

            tvStatus.visibility = View.GONE
            btnSend.isEnabled = false
            progress.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {
                val response = try {
                    api.solicitarRedefinicaoSenha(cpf, phone)
                } catch (e: Exception) {
                    PasswordResetResponse(
                        ok = false,
                        erro = e.message ?: "Não foi possível conectar ao servidor."
                    )
                }
                withContext(Dispatchers.Main) {
                    btnSend.isEnabled = true
                    progress.visibility = View.GONE
                    tvStatus.text = response.mensagem ?: response.erro
                        ?: "Não foi possível solicitar a redefinição."
                    tvStatus.visibility = View.VISIBLE
                }
            }
        }
    }
}
