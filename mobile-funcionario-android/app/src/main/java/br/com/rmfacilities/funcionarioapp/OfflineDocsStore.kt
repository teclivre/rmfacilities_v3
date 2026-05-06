package br.com.rmfacilities.funcionarioapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.io.File

data class OfflineDocEntry(
    val id: Int,
    val nome: String,
    val path: String,
    val salvoEm: Long,
    val categoria: String? = null,
    val ano: String? = null
)

class OfflineDocsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("rm_funcionario_offline_docs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "docs"
    private val keyAlias = "rm_offline_docs_key"
    private val ivSize = 12

    private fun baseDir(): File {
        val dir = File(context.filesDir, "offline_docs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun load(): MutableList<OfflineDocEntry> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<MutableList<OfflineDocEntry>>() {}.type
            gson.fromJson(raw, type) ?: mutableListOf()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun save(list: List<OfflineDocEntry>) {
        prefs.edit().putString(key, gson.toJson(list)).apply()
    }

    fun list(): List<OfflineDocEntry> = load().sortedByDescending { it.salvoEm }

    fun saveDownloaded(item: DocumentoItem, bytes: ByteArray): File {
        val dir = baseDir()
        val safeName = (item.nome_arquivo ?: "documento_${item.id}.pdf")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(dir, "${item.id}_${System.currentTimeMillis()}_$safeName.enc")
        file.writeBytes(encrypt(bytes))

        val all = load().filterNot { it.id == item.id }.toMutableList()
        all.add(
            OfflineDocEntry(
                id = item.id,
                nome = item.nome_arquivo ?: "Documento ${item.id}",
                path = file.absolutePath,
                salvoEm = System.currentTimeMillis(),
                categoria = item.categoria_label ?: item.categoria,
                ano = item.ano
            )
        )
        save(all)
        return materializeTempDecrypted(file)
    }

    fun findById(id: Int): OfflineDocEntry? = load().firstOrNull { it.id == id }

    fun openDecrypted(entry: OfflineDocEntry): File? {
        val enc = File(entry.path)
        if (!enc.exists()) return null
        if (!enc.name.lowercase().endsWith(".enc")) {
            // Compatibilidade com documentos offline legados salvos sem criptografia.
            return enc
        }
        return try {
            materializeTempDecrypted(enc)
        } catch (_: Exception) {
            null
        }
    }

    fun clearAll() {
        val all = load()
        all.forEach { entry ->
            try { File(entry.path).delete() } catch (_: Exception) {}
        }
        save(emptyList())
    }

    fun toDocumentoItems(): List<DocumentoItem> {
        return list().map {
            DocumentoItem(
                id = it.id,
                categoria = it.categoria,
                categoria_label = it.categoria,
                ano = it.ano,
                nome_arquivo = it.nome,
                criado_fmt = "Offline",
                app_download_url = "offline://${it.id}",
                can_assinar = false
            )
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(keyAlias, null)
        if (existing is SecretKey) return existing

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
        kg.init(spec)
        return kg.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv ?: ByteArray(ivSize).also { SecureRandom().nextBytes(it) }
        val ciphertext = cipher.doFinal(plain)
        return iv + ciphertext
    }

    private fun decrypt(enc: ByteArray): ByteArray {
        require(enc.size > ivSize) { "Arquivo criptografado inválido" }
        val iv = enc.copyOfRange(0, ivSize)
        val payload = enc.copyOfRange(ivSize, enc.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(payload)
    }

    private fun materializeTempDecrypted(encFile: File): File {
        if (!encFile.name.lowercase().endsWith(".enc")) {
            return encFile
        }
        val plain = decrypt(encFile.readBytes())
        val baseName = encFile.name.removeSuffix(".enc").ifBlank { "documento.pdf" }
        val out = File(context.cacheDir, "tmp_${System.currentTimeMillis()}_$baseName")
        out.writeBytes(plain)
        return out
    }
}
