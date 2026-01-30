package com.mathsoft.cgraphicsapp;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class StoragePermissionHelper {

    public static final int REQUEST_CODE_READ_STORAGE = 100;
    public static final int REQUEST_CODE_WRITE_STORAGE = 101;
    public static final int REQUEST_CODE_MANAGE_STORAGE = 102;

    /**
     * Verifica si se tiene permiso de lectura de almacenamiento
     */
    public static boolean hasReadPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ con Scoped Storage
            // Para archivos propios de la app no se necesita permiso
            // Para acceso amplio se usa MANAGE_EXTERNAL_STORAGE
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0 - 10
            return ContextCompat.checkSelfPermission(context, 
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            // API < 23, permisos concedidos en instalación
            return true;
        }
    }

    /**
     * Verifica si se tiene permiso de escritura de almacenamiento
     */
    public static boolean hasWritePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ con Scoped Storage
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0 - 10
            return ContextCompat.checkSelfPermission(context, 
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            // API < 23
            return true;
        }
    }

    /**
     * Solicita permiso de lectura de almacenamiento
     */
    public static void requestReadPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Solicitar MANAGE_EXTERNAL_STORAGE
            requestManageStoragePermission(activity);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0 - 10
            ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                REQUEST_CODE_READ_STORAGE);
        }
        // API < 23 no requiere solicitud en tiempo de ejecución
    }

    /**
     * Solicita permiso de escritura de almacenamiento
     */
    public static void requestWritePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Solicitar MANAGE_EXTERNAL_STORAGE
            requestManageStoragePermission(activity);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0 - 10
            ActivityCompat.requestPermissions(activity,
                new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                REQUEST_CODE_WRITE_STORAGE);
        }
        // API < 23 no requiere solicitud en tiempo de ejecución
    }

    /**
     * Solicita permiso de administración de almacenamiento (Android 11+)
     */
    private static void requestManageStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE);
            }
        }
    }

    /**
     * Verifica si se debe mostrar un mensaje explicativo
     */
    public static boolean shouldShowRationale(Activity activity, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
        }
        return false;
    }

    /**
     * Verifica el resultado de la solicitud de permisos
     */
    public static boolean isPermissionGranted(int[] grantResults) {
        return grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }
}