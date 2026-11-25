package br.com.rmfacilities.funcionarioapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PasswordChangeActivity : BaseActivity() {
    private lateinit var session: SessionManager
    override fun provideSession() = session
    private lateinit var api: ApiClient
    private var mandatory = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_change)

        session = SessionManager(this)
        api = ApiClient(session)
        mandatory = session.mustChangePassword

        if (session.accessToken.isBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        findViewById<TextView>(R.id.tvPasswordChangeTitle).text =
            if (mandatory) "Crie sua nova senha" else "Alterar senha"
        findViewById<TextView>(R.id.tvPasswordChangeMessage).text =
            if (mandatory) {
                "Por segurança, a senha temporária precisa ser trocada antes de usar o aplicativo."
            } else {
                "Informe sua senha atual e escolha uma nova senha com pelo menos 6 caracteres."
            }

        val etCurrent = findViewById<TextInputEditText>(R.id.etPasswordCurrent)
        val etNew = findViewById<TextInputEditText>(R.id.etPasswordNew)
        val etConfirm = findViewById<TextInputEditText>(R.id.etPasswordConfirm)
        val tvError = findViewById<TextView>(R.id.tvPasswordChangeError)
        val btnSave = findViewById<MaterialButton>(R.id.btnPasswordChangeSave)
        val btnCancel = findViewById<MaterialButton>(R.id.btnPasswordChangeCancel)

        etCurrent.visibility = if (mandatory) View.GONE else View.VISIBLE
        btnCancel.visibility = if (mandatory) View.GONE else View.VISIBLE
        btnCancel.setOnClickListener { finish() }
        btnSave.setOnClickListener {
            val current = etCurrent.text?.toString().orEmpty()
            val newPassword = etNew.text?.toString().orEmpty()
            val confirmation = etConfirm.text?.toString().orEmpty()
            val error = when {
                !mandatory && current.isBlank() -> "Informe sua senha atual."
                newPassword.length < 6 -> "A nova senha deve ter ao menos 6 caracteres."
                newPassword != confirmation -> "As senhas novas não conferem."
                !mandatory && newPassword == current -> "Escolha uma senha diferente da atual."
                else -> null
            }
            if (error != null) {
                tvError.text = error
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            tvError.visibility = View.GONE
            btnSave.isEnabled = false
            btnSave.text = "Salvando..."
            lifecycleScope.launch(Dispatchers.IO) {
                val response = try {
                    api.alterarSenha(current, newPassword)
                } catch (e: Exception) {
                    PasswordChangeResponse(ok = false, erro = e.message ?: "Falha de conexão.")
                }
                withContext(Dispatchers.Main) {
                    btnSave.isEnabled = true
                    btnSave.text = "Salvar nova senha"
                    if (!response.ok) {
                        tvError.text = response.erro ?: "Não foi possível alterar a senha."
                        tvError.visibility = View.VISIBLE
                        return@withContext
                    }

                    session.mustChangePassword = false
                    if (mandatory) {
                        session.markLoginSuccess(label = android.os.Build.MODEL)
                        if (session.biometricCpf.isBlank()) {
                            session.biometricCpf = session.lastLoginCpf
                        }
                        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    api.registrarPushToken(token)
                                } catch (e: Exception) {
                                    TelemetryLogger.logHandled(
                                        this@PasswordChangeActivity,
                                        "password_change_register_push",
                                        e
                                    )
                                }
                            }
                        }
                        startActivity(Intent(this@PasswordChangeActivity, PontoActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(
                            this@PasswordChangeActivity,
                            "Senha alterada com sucesso.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            }
        }
    }

    @Deprecated("Use o botão voltar após concluir a troca obrigatória de senha.")
    override fun onBackPressed() {
        if (!mandatory) super.onBackPressed()
    }
}
