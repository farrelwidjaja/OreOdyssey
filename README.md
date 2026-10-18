# 💎 Ore Odyssey

**Ore Odyssey** adalah game 2D mining, crafting, dan survival adventure berbasis Android yang dibuat menggunakan Kotlin. Game ini memadukan eksplorasi dunia prosedural (*procedural world generation*), sistem pertarungan, kustomisasi karakter, dan pendamping pintar berbasis **Google Gemini AI** bernama *Crystal Sage*.

---

## 🌟 Fitur Utama

- 🗺️ **Procedural World Generation**: Dunia 2D yang dihasilkan secara prosedural menggunakan algoritma *Simplex Noise (OpenSimplex2)*, lengkap dengan berbagai bioma dan lapisan kedalaman tambang.
- 🧙‍♂️ **AI Companion (Crystal Sage)**: Didukung oleh **Google Gemini AI** (`gemini-1.5-flash`), memberikan petunjuk survival, analisis kondisi pemain, dan saran cerdas secara dinamis. Dilengkapi juga dengan sistem *offline rule-based fallback* saat tanpa koneksi internet.
- ⚔️ **Combat & Entities System**:
  - Pertarungan melawan berbagai monster di kedalaman tambang.
  - Senjata jarak dekat dan proyektil panah (*Arrow physics*).
- 🎒 **Mining, Inventory & Store**:
  - Menambang berbagai bijih mineral langka (ore).
  - Sistem inventaris dan manajemen item.
  - Toko perlengkapan (*Store*) untuk upgrade gear dan item pendukung.
- 🎨 **Character Customization**:
  - Halaman kustomisasi tampilan karakter pemain sebelum memulai petualangan.
  - Rendering animasi sprite real-time menggunakan Canvas Android.
- 🔐 **Modern Authentication & Security**:
  - Autentikasi Google Sign-In menggunakan **Android Credential Manager API**.
  - Penyimpanan kredensial aman dengan **AndroidX Security Crypto**.

---

## 🛠️ Tech Stack & Arsitektur

- **Bahasa Pemrograman**: Kotlin
- **Platform**: Android (Min SDK 24 / Target SDK 34)
- **Engine / Rendering**: Custom Canvas-based Game Loop & Tilemap Renderer (`GameView.kt`, `TileRenderer.kt`)
- **AI Integration**: Google Generative AI SDK (`com.google.ai.client.generativeai`)
- **Noise Algorithm**: OpenSimplex2 (SimplexNoise 2D)
- **Dependency Management**: Gradle Version Catalog (`libs.versions.toml`) & Kotlin DSL (`build.gradle.kts`)

---

## 🚀 Cara Menjalankan Project

### 1. Prasyarat
- Android Studio Ladybug / Koala atau versi terbaru.
- JDK 17 atau yang kompatibel.
- Perangkat Android fisik atau Emulator (Android 7.0 / API 24 ke atas).

### 2. Clone Repository
```bash
git clone https://github.com/USERNAME/OreOdyssey.git
cd OreOdyssey
```

### 3. Konfigurasi Google Gemini API Key
Game ini memiliki fitur AI Companion yang membutuhkan API key dari Google AI Studio:
1. Dapatkan API key gratis di [Google AI Studio](https://aistudio.google.com/).
2. Buka atau buat file `local.properties` di root direktori project.
3. Tambahkan baris berikut:
   ```properties
   GEMINI_API_KEY=your_actual_gemini_api_key_here
   ```
*(Catatan: Jika API key tidak diisi, game tetap dapat dimainkan dengan mode offline rule-based fallback).*

### 4. Build dan Run
1. Buka project di **Android Studio**.
2. Biarkan Gradle melakukan sinkronisasi dependensi.
3. Klik tombol **Run** (`Shift + F10`) pada Android Studio.

---

## 📂 Struktur Project

```text
OreOdyssey/
├── app/
│   ├── src/main/
│   │   ├── java/com/oreodyssey/game/
│   │   │   ├── ai/            # Google Gemini AI Companion & context payload
│   │   │   ├── audio/         # Sound manager & audio effects
│   │   │   ├── core/          # Game state, loop, dan rendering canvas
│   │   │   ├── data/          # Profile, store, dan data layer
│   │   │   ├── entities/      # Player, monster, panah, item, inventory
│   │   │   ├── ui/            # Sprite pool, renderer karakter, dan UI tile
│   │   │   ├── world/         # Procedural world map & Simplex Noise generator
│   │   │   ├── CharacterActivity.kt
│   │   │   ├── LoginActivity.kt
│   │   │   └── MainActivity.kt
│   │   └── res/               # Sprite tilemaps, layout XML, dan sound fx
└── gradle/                    # Gradle wrapper & version catalog
```

---

## 📜 Lisensi & Atribusi

- Dibuat dengan cinta untuk project game Android.
- Aset sprite diadaptasi dari public domain / asset pack tilemap.
- Algoritma SimplexNoise diadaptasi dari implementasi OpenSimplex2 oleh KdotJPG.
