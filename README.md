# CGraphicsApp

**CGraphicsApp** is an Android application focused on the **dynamic compilation and execution of native C code** directly on the device. It allows selecting source files (`.c`), compiling them at runtime using **Clang**, generating shared libraries (`.so`), and dynamically loading them to run a **native graphics engine** on a `SurfaceView`.

The project is designed as an **experimental/educational tool**.

---

## Project Objective

* Provide a **self-contained environment** on Android for:

  * Compiling native C code on the device itself.
  * Generating shared libraries (`.so`) without depending on ADB or an external host.
  * Dynamically loading these libraries using `System.load`.
  * Executing native graphics logic through JNI and `Surface`.

* Explore:

  * Dynamic loading of native code.
  * Java ↔ JNI ↔ OpenGL/EGL interaction (native side).
  * Storage permission management on modern Android.
  * "Embedded" installation of toolchains (Clang) from assets.

---

## General Architecture

The application is divided into **three main responsibilities**, each encapsulated in an `Activity`:

```
MainActivity
 └── CompilerActivity
      └── RenderActivity
```

### Execution Flow

1. **MainActivity**

   * Checks if the Clang compiler is installed.
   * Installs the compiler by copying it from assets if necessary.
   * Displays system and compilation environment information.

2. **CompilerActivity**

   * Allows selecting a C source file from storage.
   * Compiles the file using Clang.
   * Generates a `.so` library in internal or external storage.
   * Exposes compiler output (stdout/stderr).
   * Enables execution of the compiled graphics engine.

3. **RenderActivity**

   * Dynamically loads the `.so` library.
   * Initializes the native graphics engine.
   * Provides a valid `Surface` for rendering.
   * Controls the rendering lifecycle.

---

## Main Components

### MainActivity

Responsibility:

* Compilation environment bootstrap.

Key Functions:

* Device ABI detection.
* Compiler installation via `ClangCompilerManager`.
* Visualization of compiler file structure.
* Installation status control.

Relevant Technical Aspects:

* Use of `AsyncTask` for I/O-intensive operations.
* Progressive copying with callback for visual feedback.

---

### CompilerActivity

Responsibility:

* C source code compilation.

Key Functions:

* File selection via `ACTION_OPEN_DOCUMENT`.
* Safe copying of selected file to temporary internal storage.
* Native compiler invocation.
* Read/write permission handling.
* Detailed presentation of compilation results.

Compilation Output:

* Status (success/error).
* Absolute path of generated `.so`.
* Binary size.
* Compiler text output.

Technical Decisions:

* Source file is never compiled directly from a `Uri`.
* The `.so` can be stored:

  * In private internal storage (default).
  * In external storage (`/mis_so/`) with explicit consent.
* Compilation and execution are decoupled (not executed automatically).

---

### RenderActivity

Responsibility:

* Native graphics engine execution.

Key Functions:

* Dynamic loading of `.so` via `System.load`.
* Explicit validation of file existence.
* Fully programmatic UI creation (no XML).
* `SurfaceView` lifecycle management.

Expected JNI Interface in Library:

```c
JNIEXPORT void JNICALL
Java_com_mathsoft_cgraphicsapp_RenderActivity_initRendering(JNIEnv* env, jobject obj, jobject jsurface);

JNIEXPORT void JNICALL
Java_com_mathsoft_cgraphicsapp_RenderActivity_stopRendering(JNIEnv* env, jobject obj);
```

Technical Aspects:

* The `Surface` is passed directly to native code.
* Rendering closure is not forced in `onPause`.
* Rendering stops explicitly in:

  * `surfaceDestroyed`
  * `onDestroy`
  * `onBackPressed`

---

## Permissions and Storage

### Declared Permissions

```xml
READ_EXTERNAL_STORAGE
WRITE_EXTERNAL_STORAGE
MANAGE_EXTERNAL_STORAGE
```

Justification:

* Selection of source files from external storage.
* Optional writing of `.so` outside the app sandbox.
* Compatibility with Android 11+ **(EXPERIMENTAL)**.

Notes:

* `requestLegacyExternalStorage=true` is used for compatibility.
* Not a Play Store-oriented project (known restrictions).

---

## Project Configuration

### SDK

* `minSdk`: 21 (Android 5.0)
* `targetSdk`: 28 (To avoid SELinux W^X protocol restrictions)
* `compileSdk`: 36

Rationale:

* Experimentation with older devices (Android 5.0 onwards).
* Allow modern ABIs without breaking compatibility (EXPERIMENTAL).
* Avoid aggressive storage restrictions introduced in modern APIs.

---

### Dependencies

Minimal dependencies, no unnecessary libraries:

```gradle
androidx.core:core:1.17.0
```

---

## Limitations and Potential Security Issues

* Use of `AsyncTask` (deprecated API, but functional for API 21–28).
* No sandboxing of executed native code.
* The app requests file permissions broadly.
* A malicious `.so` can:

  * Crash the app.
  * Block rendering.
* No semantic validation of C code.
* No C code editor.
* Only one C source file can be compiled (without header files other than those from Android NDK r25c toolchain and llvm-clang/lib/clang/17/include).
* No process isolation (no `isolatedProcess`).

These limitations are **consciously accepted** given the experimental nature of the project.

---

## Intended Use Cases

* Native graphics engine testing.
* Experimentation with JNI and OpenGL ES.
* Educational development of mobile toolchains.
* Embedded compiler prototypes.
* Research on dynamic execution in Android.

---

# LLVM-Clang 17 for Android (Optimized Redistribution for Android)

This project uses LLVM-Clang 17 optimized for Android and the optimized sysroot from NDK r25c.

## Important Note
This repository does NOT contain modifications to the LLVM-Clang or NDK r25c source code.
It is only a redistribution with space optimization.

## Base Version
- LLVM-Clang 17 (official)
- Android NDK r25c (sysroot only)

## Optimizations Performed
- Removal of unnecessary tools (only clang-17 and ld.lld were kept)
- Removal of redundant include directory
- Library cleanup for specific architecture
- Sysroot reduced to API 21 only
- Removal of C++ support from sysroot
