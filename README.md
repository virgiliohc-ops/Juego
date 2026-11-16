# NÚMEN 0.4 — APK de pruebas

Criatura virtual experimental para Android. Incluye cinco fases, cuidados, entrenamiento y evolución secreta.

## Descargar
En **Actions → Compilar APK de NÚMEN**, abre la última ejecución correcta y descarga el artefacto `NUMEN-0-4-Android-APK`.

## Diseño
Esta versión incorpora un icono propio adaptativo con ilustración NÚMEN y una alternativa de bitmap para Android antiguo.

## Instalación y seguridad
Al ser una APK descargada de GitHub fuera de Google Play, Android puede mostrar avisos de instalación desde orígenes desconocidos o solicitar una verificación con Google Play Protect. Es normal y no debe desactivarse Play Protect.

El instalador está firmado con una clave de depuración generada en GitHub Actions. Si la APK anterior estaba firmada con otra clave, Android no permitirá actualizarla encima. Exporta tu partida antes de desinstalar la versión anterior.

## Compilación
Gradle 8.9, Android Gradle Plugin 8.7.3, JDK 17, SDK 35. GitHub Actions ejecuta la tarea `:app:assembleDebug` y valida el APK con `aapt` y `apksigner`.
