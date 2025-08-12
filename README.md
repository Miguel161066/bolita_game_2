    # Bolita - Android (Kotlin + Jetpack Compose)


    ## Qué contiene

- Proyecto Android listo para compilar en modo `debug`.
- Un `GitHub Actions` workflow (`.github/workflows/android-build.yml`) que compila el APK en la nube y sube el `.apk` como artefacto.

## Cómo obtener el APK sin instalar Android Studio (pasos sencillos)

1. Crea una cuenta en GitHub si no tienes una.
2. Crea un nuevo repositorio (público o privado) en GitHub.
3. Sube **todo** el contenido de este ZIP al repositorio (puedes subir el ZIP y descomprimirlo en la interfaz web o usar `git` si sabes).
4. Ve a la pestaña **Actions** del repositorio en GitHub. Verás un workflow llamado **Build APK (debug)**.
5. Ejecuta el workflow manualmente (botón _Run workflow_) o haz `git push` a la rama `main` para dispararlo.
6. Espera (GitHub Actions descargará herramientas y compilará). Cuando termine, ve a la corrida del workflow y descarga el artefacto `app-debug-apk`.
7. En tu móvil, habilita **instalación desde orígenes desconocidos** o usa `adb install` para instalar el `app-debug.apk`.

## Instalación rápida en Android (si no quieres GitHub Actions)

Si prefieres que te compile yo el APK por ahora, puedo guiarte en los pasos para que me pases acceso temporal a un repositorio y yo suba el proyecto y lance la Action (o, si prefieres, te doy instrucciones detalladas para hacerlo tú). Pero no puedo compilar el APK directamente desde este chat.

---

**Notas técnicas:** El proyecto está pensado para ser compilado por la Action incluida. Si encuentras errores en la CI, pega aquí el log y lo soluciono.

