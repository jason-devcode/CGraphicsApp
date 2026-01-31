package com.mathsoft.cgraphicsapp;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Detecta cambios en archivos monitoreando su hash MD5 vía ContentResolver
 * Compatible con Storage Access Framework (SAF)
 */
public class FileChangeDetector {

    private static final String TAG = "FileChangeDetector";
    private static final long CHECK_INTERVAL_MS = 2000; // Verificar cada 2 segundos

    private Uri monitoredUri;
    private ContentResolver contentResolver;
    private String lastHash;
    private boolean isMonitoring;
    private Handler handler;
    private Runnable checkRunnable;
    private FileChangeListener listener;

    public interface FileChangeListener {
        /**
         * Se llama cuando se detecta un cambio en el archivo
         * @param uri El URI del archivo que cambió
         * @param newHash El nuevo hash MD5 del archivo
         */
        void onFileChanged(Uri uri, String newHash);
    }

    public FileChangeDetector() {
        this.handler = new Handler(Looper.getMainLooper());
    }

    /**
     * Comienza a monitorear un archivo vía URI
     * @param uri URI del archivo a monitorear
     * @param resolver ContentResolver para acceder al contenido
     * @param listener Listener para notificar cambios
     */
    public void startMonitoring(Uri uri, ContentResolver resolver, FileChangeListener listener) {
        if (uri == null || resolver == null) {
            Log.e(TAG, "URI o ContentResolver es null");
            return;
        }

        this.monitoredUri = uri;
        this.contentResolver = resolver;
        this.listener = listener;
        this.lastHash = calculateMD5(uri, resolver);
        this.isMonitoring = true;

        Log.d(TAG, "Iniciando monitoreo de URI: " + uri.toString());
        Log.d(TAG, "Hash inicial: " + lastHash);

        // Crear runnable para verificación periódica
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMonitoring) {
                    checkForChanges();
                    handler.postDelayed(this, CHECK_INTERVAL_MS);
                }
            }
        };

        // Iniciar verificación
        handler.post(checkRunnable);
    }

    /**
     * Detiene el monitoreo del archivo
     */
    public void stopMonitoring() {
        Log.d(TAG, "Deteniendo monitoreo");
        isMonitoring = false;
        if (checkRunnable != null) {
            handler.removeCallbacks(checkRunnable);
        }
        monitoredUri = null;
        contentResolver = null;
        listener = null;
        lastHash = null;
    }

    /**
     * Verifica si hay cambios en el archivo
     */
    private void checkForChanges() {
        if (monitoredUri == null || contentResolver == null) {
            Log.w(TAG, "URI o ContentResolver es null");
            stopMonitoring();
            return;
        }

        // Calcular hash actual
        String currentHash = calculateMD5(monitoredUri, contentResolver);
        
        if (currentHash == null) {
            Log.e(TAG, "Error calculando hash actual");
            return;
        }
        
        // Comparar con hash anterior
        if (!currentHash.equals(lastHash)) {
            Log.d(TAG, "Hash cambió de " + lastHash + " a " + currentHash);
            Log.d(TAG, "¡ARCHIVO MODIFICADO DETECTADO!");
            
            lastHash = currentHash;
            
            // Notificar al listener
            if (listener != null) {
                listener.onFileChanged(monitoredUri, currentHash);
            }
        }
    }

    /**
     * Calcula el hash MD5 de un archivo vía URI
     * @param uri URI del archivo
     * @param resolver ContentResolver para acceder al archivo
     * @return Hash MD5 en formato hexadecimal, o null si hay error
     */
    private String calculateMD5(Uri uri, ContentResolver resolver) {
        if (uri == null || resolver == null) {
            return null;
        }

        InputStream inputStream = null;
        try {
            inputStream = resolver.openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "No se pudo abrir InputStream para URI: " + uri);
                return null;
            }

            MessageDigest md = MessageDigest.getInstance("MD5");
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            
            // Convertir hash a hexadecimal
            byte[] digest = md.digest();
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error calculando MD5 para URI: " + uri, e);
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error cerrando InputStream", e);
                }
            }
        }
    }

    /**
     * Obtiene el URI actualmente monitoreado
     */
    public Uri getMonitoredUri() {
        return monitoredUri;
    }

    /**
     * Verifica si está monitoreando actualmente
     */
    public boolean isMonitoring() {
        return isMonitoring;
    }

    /**
     * Obtiene el hash actual del archivo monitoreado
     */
    public String getCurrentHash() {
        return lastHash;
    }
}