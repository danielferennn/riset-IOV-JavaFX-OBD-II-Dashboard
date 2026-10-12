# OBD2 Telemetry Dashboard

Aplikasi desktop JavaFX untuk menampilkan data telemetri kendaraan dari adaptor **OBD-II ELM327** melalui koneksi serial USB atau Bluetooth SPP.

Project ini juga memiliki **mode simulasi**, sehingga tampilan dashboard dan alur pembacaan data bisa dicoba tanpa adaptor atau kendaraan.

## Fitur Utama

- Pemilihan port serial dan baud rate.
- Koneksi ke adaptor ELM327 menggunakan `jSerialComm`.
- Inisialisasi adaptor dengan command AT dasar.
- Pembacaan telemetry OBD-II secara berkala.
- Mode simulasi untuk testing tanpa perangkat.
- Dashboard JavaFX dengan telemetry card, progress bar, chart, log komunikasi, dan daftar Diagnostic Trouble Codes (DTC).
- Pemilihan bahasa di dalam aplikasi (language selector) dengan pilihan **Indonesia** dan **English**.
- **Bahasa Indonesia** dipakai sebagai default saat aplikasi pertama kali dijalankan, dan pilihan bahasa disimpan untuk peluncuran berikutnya.
- Unit test untuk parser respons OBD-II (termasuk Mode 03), presentasi DTC, localization, dan lifecycle `SimulationService`.

## Data yang Ditampilkan

- Engine RPM
- Vehicle speed
- Coolant temperature
- Engine load
- Throttle position
- Intake air temperature
- Fuel level
- Intake manifold pressure
- Adapter atau battery voltage
- Stored DTC melalui Mode 03

## Teknologi

- Java 21
- Maven
- JavaFX
- jSerialComm
- JUnit 5

## Struktur Project

```text
.
|-- pom.xml
|-- README.md
|-- src
    |-- main
    |   |-- java/com/example/obd2dashboard
    |   |   |-- Main.java
    |   |   |-- config
    |   |   |-- controller
    |   |   |-- exception
    |   |   |-- model
    |   |   |-- parser
    |   |   |-- service
    |   |   `-- util
    |   `-- resources
    |       |-- com/example/obd2dashboard/i18n
    |       |   |-- messages_en.properties
    |       |   `-- messages_id.properties
    |       |-- css/dashboard.css
    |       `-- view/dashboard.fxml
    `-- test
        `-- java/com/example/obd2dashboard
            |-- controller
            |   `-- DtcPresentationTest.java
            |-- parser
            |   `-- ObdResponseParserTest.java
            |-- service
            |   `-- SimulationServiceTest.java
            `-- util
                `-- LocalizationTest.java
```

## Requirement

Pastikan Java dan Maven sudah tersedia:

```bash
java -version
mvn -version
```

Project ini dikonfigurasi untuk **Java 21**. Untuk mode koneksi nyata, dibutuhkan:

- Adaptor OBD-II ELM327.
- Port serial USB atau Bluetooth SPP.
- Kendaraan yang mendukung protokol OBD-II.

## Menjalankan Aplikasi

Dari root project:

```bash
mvn clean javafx:run
```

Mode ini direkomendasikan untuk development karena JavaFX runtime akan ditangani oleh plugin Maven.

## Menjalankan Test

```bash
mvn test
```

## Build JAR

```bash
mvn clean package
```

Output build akan dibuat di folder `target/`.

> Catatan: JAR hasil build belum menjadi paket standalone penuh. Jika ingin menjalankan JAR secara manual, JavaFX runtime tetap perlu tersedia di environment.

## Cara Menggunakan

### Bahasa Aplikasi

1. Saat aplikasi pertama kali dijalankan, bahasa antarmuka default adalah **Bahasa Indonesia**.
2. Gunakan pemilih bahasa (language selector) di dalam aplikasi untuk mengganti bahasa.
3. Pilihan bahasa yang tersedia adalah **Indonesia** dan **English**.
4. Pilihan bahasa akan disimpan dan tetap digunakan pada peluncuran aplikasi berikutnya.

Label tombol dan teks antarmuka mengikuti bahasa yang sedang dipilih. Nama tombol yang disebutkan pada langkah-langkah di bawah ini ditulis dalam bahasa Inggris sesuai label aslinya; saat bahasa Indonesia aktif, tombol tersebut tampil dengan label Indonesia yang setara.

### Mode Simulasi

1. Jalankan aplikasi.
2. Aktifkan tombol `Simulation`.
3. Dashboard akan memperbarui data telemetry secara otomatis.
4. Tekan `Disconnect` untuk menghentikan simulasi.

### Koneksi ELM327

1. Hubungkan adaptor ELM327 ke kendaraan.
2. Nyalakan ignition kendaraan.
3. Hubungkan adaptor ke komputer melalui USB atau Bluetooth.
4. Tekan `Refresh Port`.
5. Pilih port adaptor, misalnya `COM3`.
6. Pilih baud rate. Default yang digunakan adalah `38400`.
7. Tekan `Connect`.

Log command dan respons serial akan tampil pada panel communication log.

## Command OBD yang Didukung

| Command | Data |
| --- | --- |
| `010C` | Engine RPM |
| `010D` | Vehicle speed |
| `0105` | Coolant temperature |
| `0104` | Engine load |
| `0111` | Throttle position |
| `010F` | Intake air temperature |
| `012F` | Fuel level |
| `010B` | Intake manifold pressure |
| `ATRV` | Adapter atau battery voltage |
| `03` | Stored Diagnostic Trouble Codes |

Tidak semua kendaraan mendukung semua PID. Jika kendaraan mengembalikan `NO DATA`, aplikasi akan mencatatnya di log tanpa menghentikan polling sensor lain.

## Catatan

Aplikasi ini dibuat untuk monitoring dan pembelajaran. Hasil pembacaan tidak ditujukan sebagai pengganti alat diagnosis profesional.

Jangan mengoperasikan aplikasi saat sedang mengemudi.

Aplikasi mengingat pilihan bahasa yang terakhir dipakai, sehingga peluncuran berikutnya kembali menggunakan bahasa yang sama dengan sesi sebelumnya.

### Pembacaan DTC (Mode 03)

Respons Mode 03 divalidasi secara struktural, bukan hanya dengan mencari marker `43`:

- Payload kosong (misalnya respons hanya `43`) dianggap **tidak valid** dan memunculkan `ObdParseException`.
- Payload yang seluruh byte-nya nol (misalnya `43 00` atau `43 00 00`) adalah **respons kosong yang valid** dan ditampilkan sebagai `Tidak ada DTC`.
- Payload dengan byte bukan nol harus terdiri atas pasangan byte lengkap (jumlah byte genap). Byte tunggal bukan nol atau sisa byte yang tidak berpasangan membuat respons **tidak valid**.
- Jika ada satu baris bermarker `43` yang terpotong, seluruh respons dianggap gagal — tidak ada hasil parsial yang ditampilkan sebagai sukses.

Saat pengguna memulai pembacaan DTC baru, hasil pembacaan sebelumnya langsung dibersihkan. Jika pembacaan gagal, pesan error beserta detailnya ditampilkan pada communication log.

### Transisi Koneksi dan Simulasi

Setiap producer telemetry (ELM327 maupun simulasi) memakai satu worker thread. Untuk producer/sesi yang aktif, `stop()` menunggu callback telemetry yang sedang berjalan tuntas lebih dulu, lalu memancarkan status `DISCONNECTED` pada barrier dan baru setelah itu menyelesaikan future-nya. Bila simulasi sudah idle atau sudah ditutup, `stop()` tidak memancarkan status apa pun dan langsung mengembalikan future yang sudah selesai (no-op). Kontrak ini memungkinkan controller menunggu `stop()` sebelum mengaktifkan mode berikutnya. Setelah service ditutup (`close()`), service tidak dapat dijalankan kembali.

