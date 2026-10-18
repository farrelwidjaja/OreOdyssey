// app/src/main/java/com/oreodyssey/game/LoginActivity.kt
package com.oreodyssey.game

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.oreodyssey.game.data.Session
import com.oreodyssey.game.data.Store

class LoginActivity : AppCompatActivity() {

    companion object {
        // Isi dengan "Web client ID" dari Google Cloud Console (lihat panduan)
        private const val WEB_CLIENT_ID = "GANTI_DENGAN_WEB_CLIENT_ID"
    }

    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var tvMsg: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        tvMsg = findViewById(R.id.tvMsg)

        findViewById<Button>(R.id.btnLogin).setOnClickListener { doLogin() }
        findViewById<Button>(R.id.btnRegister).setOnClickListener { doRegister() }
        findViewById<Button>(R.id.btnGoogle).setOnClickListener { googleSignIn() }
        findViewById<Button>(R.id.btnGuest).setOnClickListener { playAsGuest() }
    }

    private fun show(msg: String) { tvMsg.text = msg }

    private fun doLogin() {
        val err = Store.login(this, etUser.text.toString(), etPass.text.toString())
        if (err != null) { show(err); return }        // belum punya akun -> tidak bisa masuk
        enter("u:" + Store.normalize(etUser.text.toString()), null)
    }

    private fun doRegister() {
        val err = Store.register(this, etUser.text.toString(), etPass.text.toString())
        if (err != null) { show(err); return }
        Toast.makeText(this, "Akun dibuat! Sekarang buat karaktermu.", Toast.LENGTH_SHORT).show()
        enter("u:" + Store.normalize(etUser.text.toString()), null)
    }

    private fun playAsGuest() {
        Session.logout()          // tamu: accountId null -> tidak ada penyimpanan
        startActivity(Intent(this, CharacterActivity::class.java))
        finish()
    }

    /** Masuk ke game. Jika akun sudah punya karakter langsung main, jika belum buat karakter dulu. */
    private fun enter(accountId: String, suggestedNick: String?) {
        Session.accountId = accountId
        Session.suggestedNick = suggestedNick
        val pr = Store.loadProfile(this, accountId)
        Session.profile = pr
        startActivity(Intent(this, if (pr != null) MainActivity::class.java else CharacterActivity::class.java))
        finish()
    }

    private fun googleSignIn() {
        if (WEB_CLIENT_ID.startsWith("GANTI")) {
            show("Google Sign-In belum dikonfigurasi. Isi WEB_CLIENT_ID dulu (lihat panduan).")
            return
        }
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        CredentialManager.create(this).getCredentialAsync(
            this, request, null, ContextCompat.getMainExecutor(this),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val cred = result.credential
                    if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        try {
                            val g = GoogleIdTokenCredential.createFrom(cred.data)
                            enter("g:" + g.id.lowercase(), g.displayName)
                        } catch (e: GoogleIdTokenParsingException) {
                            show("Gagal membaca akun Google")
                        }
                    } else show("Jenis kredensial tidak dikenali")
                }

                override fun onError(e: GetCredentialException) {
                    show("Login Google gagal: ${e.message}")
                }
            }
        )
    }
}