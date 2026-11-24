# SMRTA.APK — SMRTASYS PORTAL

Aplikasi Android native berbasis **Kotlin**, **AndroidX**, **Material Design**, dan **WebView** untuk mengakses portal **SMRTASYS PORTAL / SAMARATA SYSTEM** (`https://smrtasys.web.id/`) dengan dukungan fallback aman ke `https://143.20.199.236/`.

## Struktur Proyek

```text
SMRTA-APK/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/wrapper/gradle-wrapper.properties
├── build_windows.ps1
├── build_linux_macos.sh
├── README.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/id/smrtasys/app/
        │   ├── ServerConfig.kt
        │   ├── SplashActivity.kt
        │   └── MainActivity.kt
        └── res/
            ├── anim/
            │   ├── fade_in.xml
            │   └── fade_out.xml
            ├── drawable/
            ├── layout/
            │   ├── activity_splash.xml
            │   └── activity_main.xml
            ├── mipmap-anydpi-v26/
            ├── values/
            │   ├── colors.xml
            │   ├── strings.xml
            │   └── themes.xml
            └── xml/
                ├── data_extraction_rules.xml
                └── network_security_config.xml
```

## Build Otomatis via GitHub Actions

Workflow tersedia di `.github/workflows/build-apk.yml`:
1. Push kode ke branch `main` atau `master`, atau jalankan manual lewat tab **Actions -> Build SMRTA.APK -> Run workflow**.
2. Setelah workflow selesai, unduh APK dari bagian **Artifacts** (`SMRTA-APK-debug` dan `SMRTA-APK-release-unsigned`).

## Prasyarat Build di Laptop

1. **JDK 17** terpasang dan `JAVA_HOME` mengarah ke direktori JDK.
2. **Android SDK** terpasang (`compileSdk = 35`, `minSdk = 24`, `targetSdk = 35`) dengan `ANDROID_HOME` / `ANDROID_SDK_ROOT` mengarah ke folder SDK.
3. Koneksi internet untuk mengunduh dependensi Gradle/AndroidX saat pertama kali melakukan build.

## Cara Build di Windows (PowerShell)

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\build_windows.ps1
```

Hasil APK debug berada di:
`app\build\outputs\apk\debug\app-debug.apk`

## Cara Build di Linux / macOS

```bash
chmod +x build_linux_macos.sh gradlew
./build_linux_macos.sh
```

Hasil APK debug berada di:
`app/build/outputs/apk/debug/app-debug.apk`

## Catatan Arsitektur & Keamanan

- Seluruh autentikasi (`index.php`), sesi (`SAMARATA_SECURE_SESSION`), otorisasi (`auth_middleware.php`), dashboard (`dashboard.php`), dan operasi API (`api.php`) berjalan di sisi server web.
- Tidak ada bypass SSL/TLS certificate validation (`onReceivedSslError` selalu membatalkan koneksi tidak aman).
- Cleartext HTTP dinonaktifkan (`usesCleartextTraffic="false"` dan `cleartextTrafficPermitted="false"`).
