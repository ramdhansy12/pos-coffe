# Coffee POS untuk Android

Aplikasi pembungkus (WebView) untuk web Coffee POS Anda: tampil layar penuh tanpa Chrome, dan mencetak struk
**langsung ke printer Bluetooth** (tanpa RawBT, tanpa sambung ulang saat pindah menu atau refresh).

- Alamat web yang dibuka: `app/src/main/res/values/strings.xml` (`home_url`, saat ini `https://poss.appworld.my.id/`)
- Minimal Android 8.0 (tablet Anda Android 10: cocok)
- Izin: internet + Bluetooth. Android 10 tidak menampilkan pertanyaan izin; Android 12 ke atas meminta izin "Perangkat sekitar".

## 1. Membuat APK lewat GitHub Actions
1. Buka github.com > **New repository** > beri nama `coffee-pos-android` (boleh *Private*) > **Create**.
2. Klik **uploading an existing file**, lalu seret **semua isi folder ini** (termasuk folder `.github` dan file `debug.keystore`) > **Commit changes**.
   - Jika folder `.github` tidak ikut terunggah: **Add file > Create new file**, isi nama
     `.github/workflows/build-apk.yml`, tempel isi file tersebut, lalu Commit.
3. Buka tab **Actions**. Jika muncul tombol untuk mengaktifkan, klik. Pilih **Build APK** > **Run workflow**.
   (Setiap kali Anda commit ke cabang `main`, build juga berjalan otomatis.)
4. Tunggu sekitar 5-10 menit sampai tanda centang hijau. Buka run tersebut > bagian **Artifacts** > unduh **CoffeePOS-apk**.
5. Ekstrak zip-nya, hasilnya file `app-release.apk`.

Jika build gagal (tanda silang merah): buka run-nya, klik langkah **Build APK**, salin pesan error, dan kirim ke saya.

## 2. Memasang di tablet
1. Pindahkan `app-release.apk` ke tablet (kabel USB, WhatsApp, Google Drive, dll.).
2. Buka file-nya. Jika diminta, izinkan **Pasang aplikasi tidak dikenal** untuk aplikasi yang Anda pakai membukanya.
3. Pasang, lalu buka **Coffee POS**.
4. Pembaruan berikutnya: pasang APK baru langsung menimpa versi lama (tanda tangannya sama), data dan login tetap aman.

## 3. Menyambungkan printer (sekali saja)
1. Nyalakan printer. Di tablet: **Pengaturan > Bluetooth**, pasangkan printer (PIN umumnya `0000` atau `1234`; lihat buku panduan printer).
2. Buka aplikasi Coffee POS dan login.
3. Klik tombol **Printer** di bar atas > **Tambah Printer**. Jenis: **Aplikasi Android (Bluetooth)**.
4. Pilih printer Anda di daftar (klik **Muat ulang** jika belum muncul; di Android 12+ izinkan akses Bluetooth lebih dulu).
5. Pilih kertas **58 mm** > **Hubungkan & Simpan**. Printer langsung mencetak halaman uji.

Selesai: setiap pembayaran di POS akan mencetak struk otomatis. Koneksi dijaga aplikasi dan tersambung ulang otomatis
bila printer sempat mati lalu dinyalakan lagi.

## Catatan
- Link ke situs lain dibuka di browser, bukan di aplikasi.
- Layar tablet dijaga tetap menyala selama aplikasi terbuka (cocok untuk kasir).
- Unggah foto menu memakai pemilih file Android (galeri/berkas).
- APK ini ditandatangani dengan kunci internal yang ada di repo (`debug.keystore`). Cocok untuk dipakai sendiri/internal,
  bukan untuk dipublikasikan ke Play Store.
