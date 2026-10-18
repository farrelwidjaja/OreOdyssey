// app/src/main/java/com/oreodyssey/game/CharacterActivity.kt
package com.oreodyssey.game

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.oreodyssey.game.data.Palettes
import com.oreodyssey.game.data.Profile
import com.oreodyssey.game.data.Session
import com.oreodyssey.game.data.Store
import com.oreodyssey.game.ui.CharacterPreview

class CharacterActivity : AppCompatActivity() {

    private lateinit var profile: Profile
    private lateinit var preview: CharacterPreview
    private lateinit var btnMale: Button
    private lateinit var btnFemale: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character)

        val edit = intent.getBooleanExtra("edit", false)
        profile = Session.profile?.copy() ?: Profile(nick = Session.suggestedNick ?: "")

        preview = findViewById(R.id.preview)
        preview.profile = profile
        val etNick = findViewById<EditText>(R.id.etNick)
        etNick.setText(profile.nick)
        val tvMsg = findViewById<TextView>(R.id.tvMsg)
        btnMale = findViewById(R.id.btnMale)
        btnFemale = findViewById(R.id.btnFemale)

        btnMale.setOnClickListener { profile.female = false; refreshGender() }
        btnFemale.setOnClickListener { profile.female = true; refreshGender() }
        refreshGender()

        val rows = findViewById<LinearLayout>(R.id.rows)
        addRow(rows, "Gaya rambut", Palettes.hairStyleNames.size, { profile.hairStyle }, { profile.hairStyle = it },
            { Palettes.hairStyleNames[it] })
        addRow(rows, "Warna rambut", Palettes.hair.size, { profile.hairColor }, { profile.hairColor = it }, null)
        addRow(rows, "Baju", Palettes.shirt.size, { profile.shirt }, { profile.shirt = it }, null)
        addRow(rows, "Celana / rok", Palettes.pants.size, { profile.pants }, { profile.pants = it }, null)
        addRow(rows, "Warna kulit", Palettes.skin.size, { profile.skin }, { profile.skin = it }, null)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val nick = etNick.text.toString().trim()
            if (nick.isEmpty()) { tvMsg.text = "Isi nickname dulu"; return@setOnClickListener }
            profile.nick = nick
            Session.profile = profile
            val id = Session.accountId
            if (id != null) Store.saveProfile(this, id, profile)     // tamu tidak disimpan
            if (!edit) startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun refreshGender() {
        btnMale.alpha = if (profile.female) 0.4f else 1f
        btnFemale.alpha = if (profile.female) 1f else 0.4f
        preview.invalidate()
    }

    /** Baris pilihan: tombol kiri, label, tombol kanan. */
    private fun addRow(
        parent: LinearLayout, label: String, count: Int,
        get: () -> Int, set: (Int) -> Unit, names: ((Int) -> String)?
    ) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tv = TextView(this).apply {
            gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun refresh() {
            val v = get()
            tv.text = "$label ${v + 1}/$count" + (if (names != null) "\n${names(v)}" else "")
            preview.invalidate()
        }
        val left = Button(this).apply { text = "◀" }
        val right = Button(this).apply { text = "▶" }
        left.setOnClickListener { set((get() - 1 + count) % count); refresh() }
        right.setOnClickListener { set((get() + 1) % count); refresh() }
        row.addView(left); row.addView(tv); row.addView(right)
        parent.addView(row)
        refresh()
    }
}