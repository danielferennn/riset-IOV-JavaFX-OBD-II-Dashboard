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
- Unit test untuk parser respons OBD-II.

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
    |       |-- css/dashboard.css
    |       `-- view/dashboard.fxml
    `-- test
        `-- java/com/example/obd2dashboard/parser
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