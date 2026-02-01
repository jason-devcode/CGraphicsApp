package com.mathsoft.cgraphicsapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;

public class FileManager {
    private static final String TAG = "FileManager";
    private static final int REQUEST_CODE_PICK_FILE = 200;

    private Activity activity;
    private File selectedSourceFile;
    private Uri selectedSourceUri;
    private String selectedFileName;
    private boolean fileHasChanged;

    public interface FileLoadCallback {
        void onFileLoaded(File file, String content);
        void onFileLoadError(String error);
    }

    public interface FileSaveCallback {
        void onFileSaved(boolean success);
    }

    public FileManager(Activity activity) {
        this.activity = activity;
    }

    public void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"text/plain", "text/x-c", "text/x-csrc"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        activity.startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
    }

    public void loadFile(Uri uri, FileLoadCallback callback) {
        selectedSourceUri = uri;
        
        new AsyncTask<Void, Void, LoadResult>() {
            @Override
            protected LoadResult doInBackground(Void... voids) {
                File file = copyUriToCache(uri);
                if (file == null) return new LoadResult(null, null, "Error copiando archivo");
                
                String content = readFileContent(file);
                if (content == null) return new LoadResult(null, null, "Error leyendo contenido");
                
                return new LoadResult(file, content, null);
            }

            @Override
            protected void onPostExecute(LoadResult result) {
                if (result.error != null) {
                    callback.onFileLoadError(result.error);
                } else {
                    selectedSourceFile = result.file;
                    selectedFileName = result.file.getName();
                    fileHasChanged = false;
                    callback.onFileLoaded(result.file, result.content);
                }
            }
        }.execute();
    }

    public void reloadFromStorage(FileLoadCallback callback) {
        if (selectedSourceUri == null) {
            callback.onFileLoadError("No hay URI seleccionado");
            return;
        }

        new AsyncTask<Void, Void, LoadResult>() {
            @Override
            protected LoadResult doInBackground(Void... voids) {
                cleanupAllCache();
                File file = copyUriToCache(selectedSourceUri);
                if (file == null) return new LoadResult(null, null, "Error copiando");
                
                String content = readFileContent(file);
                return new LoadResult(file, content, null);
            }

            @Override
            protected void onPostExecute(LoadResult result) {
                if (result.error != null) {
                    callback.onFileLoadError(result.error);
                } else {
                    selectedSourceFile = result.file;
                    fileHasChanged = false;
                    callback.onFileLoaded(result.file, result.content);
                }
            }
        }.execute();
    }

    public void reloadFromUri(Uri uri, FileLoadCallback callback) {
        new AsyncTask<Void, Void, LoadResult>() {
            @Override
            protected LoadResult doInBackground(Void... voids) {
                if (selectedSourceFile != null && selectedSourceFile.exists()) {
                    selectedSourceFile.delete();
                }
                
                File file = copyUriToCache(uri);
                if (file == null) return new LoadResult(null, null, "Error copiando");
                
                String content = readFileContent(file);
                return new LoadResult(file, content, null);
            }

            @Override
            protected void onPostExecute(LoadResult result) {
                if (result.error != null) {
                    callback.onFileLoadError(result.error);
                } else {
                    selectedSourceFile = result.file;
                    callback.onFileLoaded(result.file, result.content);
                }
            }
        }.execute();
    }

    /**
     * Guarda el contenido tanto en el archivo de cache como en el almacenamiento externo
     */
    public void saveContent(String content, FileSaveCallback callback) {
        if (selectedSourceFile == null || selectedSourceUri == null) {
            callback.onFileSaved(false);
            return;
        }

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                try {
                    // PASO 1: Guardar en cache
                    Log.d(TAG, "Guardando en cache: " + selectedSourceFile.getAbsolutePath());
                    FileOutputStream cacheFos = new FileOutputStream(selectedSourceFile);
                    cacheFos.write(content.getBytes());
                    cacheFos.close();
                    
                    // PASO 2: Guardar en almacenamiento externo (URI original)
                    Log.d(TAG, "Guardando en almacenamiento externo: " + selectedSourceUri.toString());
                    OutputStream externalOs = activity.getContentResolver().openOutputStream(selectedSourceUri, "wt");
                    if (externalOs == null) {
                        Log.e(TAG, "No se pudo abrir OutputStream para URI");
                        return false;
                    }
                    
                    externalOs.write(content.getBytes());
                    externalOs.flush();
                    externalOs.close();
                    
                    Log.d(TAG, "Archivo guardado exitosamente en ambas ubicaciones");
                    return true;
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error guardando archivo", e);
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                callback.onFileSaved(success);
            }
        }.execute();
    }

    public String readFileContent(File file) {
        if (file == null || !file.exists()) return null;
        
        try {
            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            return content.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error leyendo archivo", e);
            return null;
        }
    }

    public void saveContentToFile(File file, String content) {
        if (file == null) return;
        
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(content.getBytes());
            fos.close();
            Log.d(TAG, "Contenido guardado en: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error guardando contenido", e);
        }
    }

    private File copyUriToCache(Uri uri) {
        try {
            InputStream inputStream = activity.getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            String fileName = getFileNameFromUri(uri);
            File tempFile = new File(activity.getCacheDir(), fileName);
            OutputStream outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();
            return tempFile;
        } catch (Exception e) {
            Log.e(TAG, "Error copiando desde URI", e);
            return null;
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = "temp_source.c";
        try {
            String[] projection = {android.provider.OpenableColumns.DISPLAY_NAME};
            android.database.Cursor cursor = activity.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error obteniendo nombre", e);
        }
        return fileName;
    }

    public void cleanup() {
        File cacheDir = activity.getCacheDir();
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".so") || file.getName().startsWith("temp_")) {
                    file.delete();
                }
            }
        }
    }

    private void cleanupAllCache() {
        File cacheDir = activity.getCacheDir();
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) file.delete();
            }
        }
    }

    public void handlePermissionResult(int requestCode, int resultCode, Intent data) {
        if (StoragePermissionHelper.hasReadPermission(activity)) {
            Toast.makeText(activity, "Permiso concedido", Toast.LENGTH_SHORT).show();
            openFilePicker();
        } else {
            Toast.makeText(activity, "Permiso denegado", Toast.LENGTH_SHORT).show();
        }
    }

    // Getters
    public File getSelectedSourceFile() { return selectedSourceFile; }
    public Uri getSelectedSourceUri() { return selectedSourceUri; }
    public String getSelectedFileName() { return selectedFileName; }
    public boolean hasFileChanged() { return fileHasChanged; }
    
    // Setters (para restaurar estado después de rotación)
    public void setSelectedSourceUri(Uri uri) { this.selectedSourceUri = uri; }
    public void setSelectedFileName(String name) { this.selectedFileName = name; }
    public void setFileChanged(boolean changed) { this.fileHasChanged = changed; }
    public void resetChangeFlag() { this.fileHasChanged = false; }

    private static class LoadResult {
        File file;
        String content;
        String error;
        
        LoadResult(File file, String content, String error) {
            this.file = file;
            this.content = content;
            this.error = error;
        }
    }
}