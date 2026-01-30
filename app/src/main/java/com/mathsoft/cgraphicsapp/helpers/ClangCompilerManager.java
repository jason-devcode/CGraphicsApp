package com.mathsoft.cgraphicsapp;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ClangCompilerManager {

    private static final String TAG = "ClangCompilerManager";
    private static final String COMPILER_DIR = "clang";
    private static final String BIN_DIR = "bin";
    private static final String MARKER_FILE = ".compiler_installed";

    private final Context context;
    private CopyCallback callback;

    public ClangCompilerManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Registra un callback para recibir eventos del proceso de copiado
     */
    public void setCopyCallback(CopyCallback callback) {
        this.callback = callback;
    }

    /**
     * Verifica si el compilador ya está instalado en el espacio privado de la app
     */
    public boolean isCompilerInstalled() {
        File compilerDir = new File(context.getFilesDir(), COMPILER_DIR);
        File markerFile = new File(compilerDir, MARKER_FILE);
        return compilerDir.exists() && markerFile.exists();
    }

    /**
     * Copia el compilador de assets al espacio privado si no existe
     * @return true si se copió exitosamente o ya existía, false en caso de error
     */
    public boolean installCompiler() {
        if (isCompilerInstalled()) {
            Log.d(TAG, "Compiler already installed");
            return true;
        }

        String abi = getDeviceABI();
        String assetPath = abi + "/" + COMPILER_DIR;
        
        Log.d(TAG, "Installing compiler for ABI: " + abi);
        
        if (callback != null) {
            callback.onCopyStarted();
        }

        try {
            File destDir = new File(context.getFilesDir(), COMPILER_DIR);
            
            // Copiar el árbol de directorios
            copyAssetFolder(context.getAssets(), assetPath, destDir);
            
            // Establecer permisos de ejecución en binarios
            setExecutablePermissions(destDir);
            
            // Crear archivo marcador
            File markerFile = new File(destDir, MARKER_FILE);
            markerFile.createNewFile();
            
            if (callback != null) {
                callback.onCopyCompleted(true);
            }
            
            Log.d(TAG, "Compiler installed successfully");
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "Error installing compiler", e);
            if (callback != null) {
                callback.onCopyCompleted(false);
            }
            return false;
        }
    }

    /**
     * Obtiene la ABI del dispositivo
     */
    private String getDeviceABI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Build.SUPPORTED_ABIS[0];
        } else {
            // Fallback para API < 21 (aunque especificaste API 21 como mínimo)
            return Build.CPU_ABI;
        }
    }

    /**
     * Copia recursivamente una carpeta de assets al sistema de archivos
     */
    private void copyAssetFolder(AssetManager assets, String assetPath, File destDir) throws IOException {
        String[] files = assets.list(assetPath);
        
        if (files == null || files.length == 0) {
            // Es un archivo, no un directorio
            copyAssetFile(assets, assetPath, destDir);
            return;
        }

        // Es un directorio
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + destDir.getAbsolutePath());
        }

        // Copiar cada elemento del directorio
        for (String filename : files) {
            String assetFilePath = assetPath + "/" + filename;
            File destFile = new File(destDir, filename);
            
            // Verificar si es un subdirectorio
            String[] subFiles = assets.list(assetFilePath);
            if (subFiles != null && subFiles.length > 0) {
                // Es un subdirectorio
                copyAssetFolder(assets, assetFilePath, destFile);
            } else {
                // Es un archivo
                copyAssetFile(assets, assetFilePath, destFile);
            }
            
            // Notificar progreso
            if (callback != null) {
                callback.onCopyProgress(assetFilePath);
            }
        }
    }

    /**
     * Copia un archivo individual de assets al sistema de archivos
     */
    private void copyAssetFile(AssetManager assets, String assetPath, File destFile) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        
        try {
            in = assets.open(assetPath);
            
            // Crear directorio padre si no existe
            File parentDir = destFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            out = new FileOutputStream(destFile);
            
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing input stream", e);
                }
            }
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output stream", e);
                }
            }
        }
    }

    /**
     * Establece permisos de ejecución en los binarios del directorio bin
     */
    private void setExecutablePermissions(File compilerDir) {
        File binDir = new File(compilerDir, BIN_DIR);
        
        if (!binDir.exists() || !binDir.isDirectory()) {
            Log.w(TAG, "Bin directory not found: " + binDir.getAbsolutePath());
            return;
        }

        File[] binaries = binDir.listFiles();
        if (binaries == null) {
            return;
        }

        for (File binary : binaries) {
            if (binary.isFile()) {
                boolean success = binary.setExecutable(true, false);
                if (success) {
                    Log.d(TAG, "Set executable permission for: " + binary.getName());
                } else {
                    Log.w(TAG, "Failed to set executable permission for: " + binary.getName());
                }
            }
        }
    }

    /**
     * Obtiene la ruta al directorio del compilador en el espacio privado
     */
    public File getCompilerDirectory() {
        return new File(context.getFilesDir(), COMPILER_DIR);
    }

    /**
     * Elimina el compilador del espacio privado (útil para reinstalación o limpieza)
     */
    public boolean uninstallCompiler() {
        File compilerDir = new File(context.getFilesDir(), COMPILER_DIR);
        return deleteRecursive(compilerDir);
    }

    /**
     * Elimina recursivamente un directorio y su contenido
     */
    private boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return fileOrDirectory.delete();
    }

    /**
     * Interface para callbacks del proceso de copiado
     */
    public interface CopyCallback {
        /**
         * Se llama cuando comienza el proceso de copiado
         */
        void onCopyStarted();

        /**
         * Se llama durante el proceso de copiado con el archivo actual
         * @param currentFile Ruta del archivo que se está copiando
         */
        void onCopyProgress(String currentFile);

        /**
         * Se llama cuando termina el proceso de copiado
         * @param success true si se completó exitosamente, false si hubo error
         */
        void onCopyCompleted(boolean success);
    }
}