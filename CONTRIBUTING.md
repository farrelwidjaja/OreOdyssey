# 🤝 Panduan Kontribusi (Contributing Guide)

Terima kasih atas ketertarikan Anda untuk berkontribusi pada pengembangan **Ore Odyssey**! Panduan ini dibuat untuk membantu rekan pengembang / kolaborator memahami alur kerja dan standar pengembangan game ini.

---

## 📌 Alur Kerja Kontribusi (Workflow)

### 1. Untuk Kolaborator (Direct Collaborator)
Jika Anda telah ditambahkan sebagai **Collaborator** di repositori ini:
1. Clone repositori ke komputer Anda:
   ```bash
   git clone https://github.com/farrelwidjaja/OreOdyssey.git
   cd OreOdyssey
   ```
2. Buat branch baru dari `main` sesuai fitur/issue yang akan dikerjakan:
   ```bash
   git checkout -b feature/nama-fitur
   # atau untuk bugfix:
   git checkout -b fix/deskripsi-bug
   ```
3. Lakukan perubahan dan commit dengan pesan yang deskriptif.
4. Push branch Anda ke GitHub:
   ```bash
   git push -u origin feature/nama-fitur
   ```
5. Buka Pull Request (PR) ke branch `main` untuk direview.

---

### 2. Untuk Kontributor Umum (Fork & Pull Request)
1. Lakukan **Fork** pada repositori ini ke akun GitHub Anda.
2. Clone hasil fork:
   ```bash
   git clone https://github.com/USERNAME-ANDA/OreOdyssey.git
   ```
3. Buat branch fitur baru (`git checkout -b feature/...`).
4. Lakukan commit dan push ke repo fork Anda.
5. Buat **Pull Request (PR)** dari repo fork Anda ke branch `main` di `farrelwidjaja/OreOdyssey`.

---

## 📋 Mengerjakan Issue yang Tersedia

Lihat daftar tugas di [GitHub Issues](https://github.com/farrelwidjaja/OreOdyssey/issues):
- Pilih issue yang ingin dikerjakan (misal: `#1` untuk optimasi AI Companion, `#2` untuk procedural generation, `#3` untuk combat, dll).
- Tulis komentar di issue tersebut bahwa Anda sedang mengerjakannya.
- Saat membuat Pull Request, cantumkan kata kunci penutup issue, misalnya:
  > *Closes #1* atau *Fixes #3*

---

## 💡 Konvensi Commit

Gunakan format [Conventional Commits](https://www.conventionalcommits.org/):
- `feat: Menambahkan varian monster baru`
- `fix: Memperbaiki memory leak pada TileRenderer`
- `perf: Mengoptimalkan rendering loop di GameView`
- `docs: Memperbarui panduan setup README`
- `refactor: Merestrukturisasi package data profile`

---

## ⚙️ Menjalankan & Menguji Proyek

1. Buka folder proyek di **Android Studio** (disarankan versi terbaru).
2. Konfigurasikan API key jika diperlukan di `local.properties`:
   ```properties
   GEMINI_API_KEY=YOUR_GEMINI_KEY
   ```
3. Sinkronkan Gradle dan jalankan di perangkat Android / Emulator (Min SDK 24 / Android 7.0+).
