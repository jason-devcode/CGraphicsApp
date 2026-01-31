package com.mathsoft.cgraphicsapp;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NativeCompiler {

    private static final String TAG = "NativeCompiler";
    private final Context context;
    private final ClangCompilerManager compilerManager;

    public NativeCompiler(Context context) {
        this.context = context.getApplicationContext();
        this.compilerManager = new ClangCompilerManager(context);
    }

    /**
     * Compila un archivo .c a .so usando Clang
     */
    public CompilationResult compile(File sourceFile, String outputName, boolean saveToExternalStorage) {
        if (!compilerManager.isCompilerInstalled()) {
            return new CompilationResult(false, "Compilador no instalado", "");
        }

        if (!sourceFile.exists() || !sourceFile.canRead()) {
            return new CompilationResult(false, "No se puede leer el archivo fuente", "");
        }

        File compilerDir = compilerManager.getCompilerDirectory();
        
        // Buscar el binario correcto de clang
        File clangBinary = findClangBinary(compilerDir);
        
        if (clangBinary == null || !clangBinary.exists()) {
            String error = "Binario clang no encontrado en: " + 
                          new File(compilerDir, "bin").getAbsolutePath();
            return new CompilationResult(false, error, "");
        }

        Log.d(TAG, "Using clang binary: " + clangBinary.getAbsolutePath());

        // Preparar directorio temporal
        File tmpDir = prepareTempDirectory();
        if (tmpDir == null) {
            return new CompilationResult(false, "No se pudo crear directorio temporal", "");
        }

        // Preparar archivo de salida
        File outputFile = prepareOutputFile(outputName, saveToExternalStorage);
        
        if (outputFile == null) {
            return new CompilationResult(false, 
                "No se pudo crear el directorio de salida. " +
                "Verifica los permisos de almacenamiento.", "");
        }

        // IMPORTANTE: Eliminar archivo .so anterior si existe
        if (outputFile.exists()) {
            Log.d(TAG, "Eliminando .so anterior: " + outputFile.getAbsolutePath());
            if (outputFile.delete()) {
                Log.d(TAG, "Archivo anterior eliminado exitosamente");
            } else {
                Log.w(TAG, "No se pudo eliminar el archivo anterior");
            }
        }

        // Construir comando de compilación
        List<String> command = buildCompileCommand(clangBinary, compilerDir, sourceFile, outputFile);

        Log.d(TAG, "Compile command: " + command.toString());
        Log.d(TAG, "TMPDIR: " + tmpDir.getAbsolutePath());
        Log.d(TAG, "Output file: " + outputFile.getAbsolutePath());

        // Ejecutar compilación con variables de entorno
        return executeCompilation(command, outputFile, compilerDir, tmpDir);
    }

    /**
     * Prepara el directorio temporal para clang
     */
    private File prepareTempDirectory() {
        // Crear directorio tmp en el cache de la app
        File tmpDir = new File(context.getCacheDir(), "clang_tmp");
        
        if (!tmpDir.exists()) {
            if (!tmpDir.mkdirs()) {
                Log.e(TAG, "Failed to create temp directory: " + tmpDir.getAbsolutePath());
                return null;
            }
        }

        // Limpiar archivos temporales antiguos
        cleanTempDirectory(tmpDir);

        Log.d(TAG, "Temp directory ready: " + tmpDir.getAbsolutePath());
        return tmpDir;
    }

    /**
     * Limpia archivos temporales antiguos
     */
    private void cleanTempDirectory(File tmpDir) {
        if (tmpDir.exists() && tmpDir.isDirectory()) {
            File[] files = tmpDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        file.delete();
                    }
                }
            }
        }
    }

    /**
     * Busca el binario de clang en el directorio bin
     */
    private File findClangBinary(File compilerDir) {
        File binDir = new File(compilerDir, "bin");
        
        if (!binDir.exists() || !binDir.isDirectory()) {
            Log.e(TAG, "Bin directory not found: " + binDir.getAbsolutePath());
            return null;
        }

        // Buscar diferentes versiones de clang
        String[] possibleNames = {
            "clang-17",
            "clang-18",
            "clang-16",
            "clang-15",
            "clang"
        };

        for (String name : possibleNames) {
            File binary = new File(binDir, name);
            if (binary.exists() && binary.canExecute()) {
                Log.d(TAG, "Found clang binary: " + name);
                return binary;
            }
        }

        // Si no encontramos ninguno, listar los archivos disponibles
        File[] files = binDir.listFiles();
        if (files != null) {
            Log.d(TAG, "Available files in bin directory:");
            for (File file : files) {
                Log.d(TAG, "  - " + file.getName() + 
                     " (executable: " + file.canExecute() + ")");
                
                // Intentar con cualquier archivo que contenga "clang"
                if (file.getName().contains("clang") && file.canExecute()) {
                    Log.d(TAG, "Using fallback clang binary: " + file.getName());
                    return file;
                }
            }
        }

        return null;
    }

    /**
     * Prepara el archivo de salida en el directorio correcto
     */
    private File prepareOutputFile(String outputName, boolean saveToExternalStorage) {
        String outputFileName = outputName;
        if (!outputFileName.startsWith("lib")) {
            outputFileName = "lib" + outputFileName;
        }
        if (!outputFileName.endsWith(".so")) {
            outputFileName = outputFileName + ".so";
        }

        File outputDir;
        
        if (saveToExternalStorage) {
            // Guardar en almacenamiento externo: /storage/emulated/0/mis_so/
            outputDir = new File(Environment.getExternalStorageDirectory(), "mis_so");
            
            if (!outputDir.exists()) {
                if (!outputDir.mkdirs()) {
                    Log.e(TAG, "Failed to create external directory: " + outputDir.getAbsolutePath());
                    return null;
                }
            }
            
            Log.d(TAG, "Output directory (external): " + outputDir.getAbsolutePath());
        } else {
            // Guardar en almacenamiento interno de la app
            outputDir = new File(context.getFilesDir(), "compiled");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            Log.d(TAG, "Output directory (internal): " + outputDir.getAbsolutePath());
        }

        return new File(outputDir, outputFileName);
    }

    /**
     * Construye el comando de compilación para Clang
     */
    private List<String> buildCompileCommand(File clangBinary, File compilerDir, 
                                             File sourceFile, File outputFile) {
        List<String> command = new ArrayList<>();
        
        command.add(clangBinary.getAbsolutePath());
        
        // Target según ABI del dispositivo
        String target = getTargetTriple();
        command.add("--target=" + target);
        
        // Sysroot
        File sysroot = new File(compilerDir, "sysroot");
        command.add("--sysroot=" + sysroot.getAbsolutePath());
        
        // Flags de compilación
        command.add("-shared");
        command.add("-fPIC");
        command.add("-O2"); // Optimización
        
        // Suprimir warnings comunes
        command.add("-w"); // Deshabilitar warnings
        
        // Archivo fuente
        command.add(sourceFile.getAbsolutePath());
        
        // Archivo de salida
        command.add("-o");
        command.add(outputFile.getAbsolutePath());
        
        // Librerías
        command.add("-lEGL");
        command.add("-lGLESv2");
        command.add("-landroid");
        command.add("-llog");
        
        return command;
    }

    /**
     * Obtiene el target triple según la ABI del dispositivo
     */
    private String getTargetTriple() {
        String abi = getDeviceABI();
        
        // Mapear ABI a target triple
        if (abi.startsWith("arm64") || abi.equals("aarch64")) {
            return "aarch64-linux-android21";
        } else if (abi.startsWith("armeabi")) {
            return "armv7a-linux-androideabi21";
        } else if (abi.equals("x86_64")) {
            return "x86_64-linux-android21";
        } else if (abi.equals("x86")) {
            return "i686-linux-android21";
        } else {
            // Default
            return "aarch64-linux-android21";
        }
    }

    /**
     * Obtiene la ABI del dispositivo
     */
    private String getDeviceABI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Build.SUPPORTED_ABIS[0];
        } else {
            return Build.CPU_ABI;
        }
    }

    /**
     * Ejecuta el comando de compilación con variables de entorno
     */
    private CompilationResult executeCompilation(List<String> command, File outputFile, 
                                                 File compilerDir, File tmpDir) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        // Configurar variables de entorno
        Map<String, String> env = processBuilder.environment();
        
        // TMPDIR: Directorio temporal para archivos intermedios
        env.put("TMPDIR", tmpDir.getAbsolutePath());
        env.put("TEMP", tmpDir.getAbsolutePath());
        env.put("TMP", tmpDir.getAbsolutePath());
        
        // PATH: Incluir el directorio bin del compilador
        File binDir = new File(compilerDir, "bin");
        String path = binDir.getAbsolutePath();
        if (env.containsKey("PATH")) {
            path = path + ":" + env.get("PATH");
        }
        env.put("PATH", path);
        
        // HOME: Directorio home del compilador
        env.put("HOME", compilerDir.getAbsolutePath());
        
        // LD_LIBRARY_PATH: Librerías del compilador
        File libDir = new File(compilerDir, "lib");
        if (libDir.exists()) {
            env.put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
        }

        // Log de variables de entorno
        Log.d(TAG, "Environment variables:");
        Log.d(TAG, "  TMPDIR: " + env.get("TMPDIR"));
        Log.d(TAG, "  PATH: " + env.get("PATH"));
        Log.d(TAG, "  HOME: " + env.get("HOME"));

        StringBuilder output = new StringBuilder();
        boolean success = false;

        try {
            Process process = processBuilder.start();

            // Leer salida del proceso
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                Log.d(TAG, "Clang output: " + line);
            }

            int exitCode = process.waitFor();
            success = (exitCode == 0 && outputFile.exists());

            if (!success) {
                output.append("\nCódigo de salida: ").append(exitCode);
                
                if (!outputFile.exists()) {
                    output.append("\nEl archivo de salida no fue creado");
                }
            } else {
                Log.d(TAG, "Compilation successful. Output: " + outputFile.getAbsolutePath());
            }

            // Limpiar archivos temporales después de compilar
            cleanTempDirectory(tmpDir);

        } catch (IOException e) {
            output.append("Error de IO: ").append(e.getMessage()).append("\n");
            output.append("Stack trace: ").append(Log.getStackTraceString(e));
            Log.e(TAG, "IO Error during compilation", e);
        } catch (InterruptedException e) {
            output.append("Compilación interrumpida: ").append(e.getMessage());
            Log.e(TAG, "Compilation interrupted", e);
            Thread.currentThread().interrupt();
        }

        String message = success ? "Compilación exitosa" : "Error de compilación";
        return new CompilationResult(success, message, output.toString(), 
                                    success ? outputFile.getAbsolutePath() : null);
    }

    /**
     * Clase para representar el resultado de la compilación
     */
    public static class CompilationResult {
        private final boolean success;
        private final String message;
        private final String output;
        private final String outputPath;

        public CompilationResult(boolean success, String message, String output) {
            this(success, message, output, null);
        }

        public CompilationResult(boolean success, String message, String output, String outputPath) {
            this.success = success;
            this.message = message;
            this.output = output;
            this.outputPath = outputPath;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getOutput() {
            return output;
        }

        public String getOutputPath() {
            return outputPath;
        }
    }
}