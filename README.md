# CGraphicsApp

**CGraphicsApp** es una aplicación Android orientada a la **compilación y ejecución dinámica de código nativo en C** directamente en el dispositivo. Permite seleccionar archivos fuente (`.c`), compilarlos en tiempo de ejecución mediante **Clang**, generar bibliotecas compartidas (`.so`) y cargarlas dinámicamente para ejecutar un **motor gráfico nativo** sobre un `SurfaceView`.

El proyecto está diseñado como **herramienta experimental / educativa**.

---

## Objetivo del proyecto

* Proveer un **entorno autosuficiente** en Android para:

  * Compilar código C nativo en el propio dispositivo.
  * Generar bibliotecas compartidas (`.so`) sin depender de ADB ni de un host externo.
  * Cargar dinámicamente dichas bibliotecas usando `System.load`.
  * Ejecutar lógica gráfica nativa mediante JNI y `Surface`.

* Explorar:

  * Carga dinámica de código nativo.
  * Interacción Java ↔ JNI ↔ OpenGL/EGL (lado nativo).
  * Gestión de permisos de almacenamiento en Android moderno.
  * Instalación “embebida” de toolchains (Clang) desde assets.

---

## Arquitectura general

La aplicación se divide en **tres responsabilidades principales**, cada una encapsulada en una `Activity`:

```
MainActivity
 └── CompilerActivity
      └── RenderActivity
```

### Flujo de ejecución

1. **MainActivity**

   * Verifica si el compilador Clang está instalado.
   * Instala el compilador copiándolo desde assets si es necesario.
   * Muestra información del sistema y del entorno de compilación.

2. **CompilerActivity**

   * Permite seleccionar un archivo fuente en C desde el almacenamiento.
   * Compila el archivo usando Clang.
   * Genera una librería `.so` en almacenamiento interno o externo.
   * Expone la salida del compilador (stdout / stderr).
   * Habilita la ejecución del motor gráfico compilado.

3. **RenderActivity**

   * Carga dinámicamente la librería `.so`.
   * Inicializa el motor gráfico nativo.
   * Proporciona un `Surface` válido para rendering.
   * Controla el ciclo de vida del rendering.

---

## Componentes principales

### MainActivity

Responsabilidad:

* Bootstrap del entorno de compilación.

Funciones clave:

* Detección del ABI del dispositivo.
* Instalación del compilador mediante `ClangCompilerManager`.
* Visualización de la estructura de archivos del compilador.
* Control de estado de instalación.

Aspectos técnicos relevantes:

* Uso de `AsyncTask` para operaciones I/O intensivas.
* Copia progresiva con callback para feedback visual.

---

### CompilerActivity

Responsabilidad:

* Compilación de código fuente C.

Funciones clave:

* Selección de archivos mediante `ACTION_OPEN_DOCUMENT`.
* Copia segura del archivo seleccionado a almacenamiento interno temporal.
* Invocación del compilador nativo.
* Manejo de permisos de lectura/escritura.
* Presentación detallada del resultado de compilación.

Salida de compilación:

* Estado (éxito / error).
* Ruta absoluta del `.so` generado.
* Tamaño del binario.
* Salida textual del compilador.

Decisiones técnicas:

* El archivo fuente nunca se compila directamente desde un `Uri`.
* El `.so` puede almacenarse:

  * En almacenamiento interno privado (default).
  * En almacenamiento externo (`/mis_so/`) bajo consentimiento explícito.
* Se desacopla compilación y ejecución (no se ejecuta automáticamente).

---

### RenderActivity

Responsabilidad:

* Ejecución del motor gráfico nativo.

Funciones clave:

* Carga dinámica del `.so` mediante `System.load`.
* Validación explícita de existencia del archivo.
* Creación de UI completamente programática (sin XML).
* Gestión del ciclo de vida del `SurfaceView`.

Interfaz JNI esperada en la librería:

```c
JNIEXPORT void JNICALL
Java_com_mathsoft_cgraphicsapp_RenderActivity_initRendering(JNIEnv* env, jobject obj, jobject jsurface);

JNIEXPORT void JNICALL
Java_com_mathsoft_cgraphicsapp_RenderActivity_stopRendering(JNIEnv* env, jobject obj);
```

Aspectos técnicos:

* El `Surface` se pasa directamente al código nativo.
* No se fuerza el cierre del rendering en `onPause`.
* El rendering se detiene de forma explícita en:

  * `surfaceDestroyed`
  * `onDestroy`
  * `onBackPressed`

---

## Permisos y almacenamiento

### Permisos declarados

```xml
READ_EXTERNAL_STORAGE
WRITE_EXTERNAL_STORAGE
MANAGE_EXTERNAL_STORAGE
```

Justificación:

* Selección de archivos fuente desde almacenamiento externo.
* Escritura opcional del `.so` fuera del sandbox de la app.
* Compatibilidad con Android 11+ **(EXPERIMENTAL)**.

Notas:

* `requestLegacyExternalStorage=true` se utiliza para compatibilidad.
* No es un proyecto orientado a Play Store (restricciones conocidas).

---

## Configuración del proyecto

### SDK

* `minSdk`: 21 (Android 5.0)
* `targetSdk`: 28 (Para evitar restricciones del protocolo W^X de SELinux)
* `compileSdk`: 36

Razonamiento:

* Experimentación con dispositivos antiguos (Android 5.0 en adelante).
* Permitir ABI modernos sin romper compatibilidad (EXPERIMENTAL).
* Evitar restricciones agresivas de almacenamiento introducidas en APIs modernas.

---

### Dependencias

Dependencias mínimas, sin librerías innecesarias:

```gradle
androidx.core:core:1.17.0
```

---

## Limitaciones y posibles problemas de seguridad.

* Uso de `AsyncTask` (API obsoleta, pero funcional para API 21–28).
* No hay sandboxing del código nativo ejecutado.
* La app pide permisos de ficheros de forma masiva.
* Un `.so` malicioso puede:

  * Crashear la app.
  * Bloquear el rendering.
* No hay validación semántica del código C.
* No hay editor de código C.
* Solo se puede compilar un fichero de código fuente en C (Sin ficheros de cabecera que no sean los del toolchain del NDK de android r25c y los del llvm-clang/lib/clang/17/include ).
* No existe aislamiento por proceso (no `isolatedProcess`).

Estas limitaciones son **conscientes y aceptadas** dado el carácter experimental del proyecto.

---

## Casos de uso previstos

* Pruebas de motores gráficos nativos.
* Experimentación con JNI y OpenGL ES.
* Desarrollo educativo de toolchains móviles.
* Prototipos de compiladores embebidos.
* Investigación sobre ejecución dinámica en Android.

---

# LLVM-Clang 17 para Android (Redistribución Optimizada para Android)

Este proyecto hace uso del LLVM-Clang 17 optimizado para Android y uso del sysroot optimizado del NDK r25c.

## Nota importante
Este repositorio NO contiene modificaciones al código fuente de LLVM-Clang o del NDK r25c.
Solo es una redistribución con optimización de espacio.

## Versión base
- LLVM-Clang 17 (oficial)
- Android NDK r25c (Sólo el sysroot)

## Optimizaciones realizadas
- Eliminación de herramientas innecesarias (solo se dejo el clang-17 y ld.lld)
- Eliminación del directorio include redundante
- Limpieza de librerías para arquitectura específica
- Sysroot reducido a API 21 únicamente
- Eliminación de soporte C++ del sysroot

