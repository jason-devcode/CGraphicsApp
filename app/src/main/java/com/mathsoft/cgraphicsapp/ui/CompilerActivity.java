package com.mathsoft.cgraphicsapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class CompilerActivity extends Activity {

    private static final String TAG = "CompilerActivity";
    private static final int REQUEST_CODE_PICK_FILE = 200;

    private TextView selectedFileText;
    private TextView outputText;
    private TextView changeStatusText;
    private Button selectFileButton;
    private Button compileButton;
    private Button executeButton;
    private CheckBox saveToExternalCheckBox;
    private ProgressBar progressBar;
    
    private File selectedSourceFile;
    private Uri selectedSourceUri; // URI del archivo original
    private String selectedFileName;
    private NativeCompiler compiler;
    private String lastCompiledSoPath = null;
    private String lastCompiledSoName = null;
    private boolean lastWasExternal = false;
    
    // Detector de cambios en archivos
    private FileChangeDetector fileChangeDetector;
    private boolean fileHasChanged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.compiler_activity);

        selectedFileText = findViewById(R.id.selected_file_text);
        outputText = findViewById(R.id.output_text);
        changeStatusText = findViewById(R.id.change_status_text);
        selectFileButton = findViewById(R.id.select_file_button);
        compileButton = findViewById(R.id.compile_button);
        executeButton = findViewById(R.id.execute_button);
        saveToExternalCheckBox = findViewById(R.id.save_external_checkbox);
        progressBar = findViewById(R.id.compile_progress_bar);

        compiler = new NativeCompiler(this);
        fileChangeDetector = new FileChangeDetector();

        compileButton.setEnabled(false);
        executeButton.setEnabled(false);

        selectFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionsAndSelectFile();
            }
        });

        compileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedSourceFile != null) {
                    compileSourceFile();
                }
            }
        });

        executeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastCompiledSoPath != null) {
                    executeCompiledLibrary();
                }
            }
        });
        
        // Limpiar archivos temporales al iniciar
        cleanupTempSoFiles();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recargar archivo desde el URI si hay uno seleccionado
        if (selectedSourceUri != null) {
            Log.d(TAG, "onResume - Limpiando cache completo y recargando archivo desde almacenamiento");
            
            // Recargar el archivo desde el URI al cache
            new AsyncTask<Void, Void, File>() {
                @Override
                protected File doInBackground(Void... voids) {
                    try {
                        // PASO 1: Limpiar TODO el cache (archivos .c y .so)
                        Log.d(TAG, "onResume - Limpiando cache completo...");
                        cleanupAllCacheFiles();
                        
                        // PASO 2: Copiar nuevamente desde el URI original
                        Log.d(TAG, "onResume - Copiando archivo desde URI original...");
                        return copyUriToTempFile(selectedSourceUri);
                    } catch (Exception e) {
                        Log.e(TAG, "onResume - Error recargando archivo", e);
                        return null;
                    }
                }

                @Override
                protected void onPostExecute(File file) {
                    if (file != null) {
                        selectedSourceFile = file;
                        Log.d(TAG, "onResume - Archivo recargado desde almacenamiento: " + file.getAbsolutePath());
                        
                        // Resetear estado de compilación ya que eliminamos los .so
                        lastCompiledSoPath = null;
                        lastCompiledSoName = null;
                        lastWasExternal = false;
                        executeButton.setEnabled(false);
                        
                        // Actualizar UI
                        selectedFileText.setText("Archivo seleccionado:\n" + selectedFileName + 
                            "\n\n🔄 Recargado desde almacenamiento");
                        outputText.setText("Archivo recargado desde almacenamiento.\n" +
                                         "Compilaciones anteriores eliminadas.\n" +
                                         "Listo para compilar versión actualizada.");
                        
                        // Resetear estado de cambios
                        fileHasChanged = false;
                        changeStatusText.setVisibility(View.GONE);
                        
                        // Ahora iniciar el monitoreo con el hash del archivo recién cargado
                        startFileMonitoring();
                        
                        // Mostrar notificación
                        Toast.makeText(CompilerActivity.this, 
                            "✓ Archivo y cache sincronizados", 
                            Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "onResume - Error al recargar archivo");
                        Toast.makeText(CompilerActivity.this, 
                            "✗ Error al sincronizar archivo", 
                            Toast.LENGTH_SHORT).show();
                    }
                }
            }.execute();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pausar monitoreo para ahorrar recursos
        if (fileChangeDetector != null) {
            Log.d(TAG, "onPause - Pausando monitoreo");
            fileChangeDetector.stopMonitoring();
        }
    }

    @Override
    protected void onDestroy() {
        // Detener monitoreo
        if (fileChangeDetector != null) {
            fileChangeDetector.stopMonitoring();
        }
        
        // Limpiar archivos temporales al cerrar la actividad
        cleanupTempSoFiles();
        super.onDestroy();
    }

    /**
     * Inicia el monitoreo del archivo a través del URI
     */
    private void startFileMonitoring() {
        if (selectedSourceUri == null) {
            Log.w(TAG, "No hay URI para monitorear");
            return;
        }

        Log.d(TAG, "Iniciando monitoreo del URI: " + selectedSourceUri.toString());

        fileChangeDetector.startMonitoring(selectedSourceUri, getContentResolver(), 
            new FileChangeDetector.FileChangeListener() {
                @Override
                public void onFileChanged(Uri uri, String newHash) {
                    Log.d(TAG, "¡Cambio detectado en el archivo!");
                    Log.d(TAG, "URI: " + uri.toString());
                    Log.d(TAG, "Nuevo hash: " + newHash);
                    
                    fileHasChanged = true;
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Actualizar UI para indicar que el archivo ha cambiado
                            changeStatusText.setVisibility(View.VISIBLE);
                            changeStatusText.setText("⚠️ CAMBIOS DETECTADOS - Recompila para actualizar");
                            changeStatusText.setTextColor(0xFFFF9800); // Color naranja
                            
                            selectedFileText.setText("Archivo seleccionado:\n" + selectedFileName + 
                                "\n\n⚠️ ARCHIVO MODIFICADO");
                            
                            // Mostrar notificación
                            Toast.makeText(CompilerActivity.this, 
                                "⚠️ Cambios detectados en: " + selectedFileName, 
                                Toast.LENGTH_SHORT).show();
                            
                            // Actualizar texto de salida si es necesario
                            String currentOutput = outputText.getText().toString();
                            if (currentOutput.contains("Listo para compilar") ||
                                currentOutput.contains("Compilado exitosamente")) {
                                outputText.setText("⚠️ El archivo ha sido modificado\n" +
                                                 "Recompila para aplicar los cambios");
                            }
                            
                            // Recargar archivo modificado desde el URI al cache
                            reloadFileFromUri();
                        }
                    });
                }
            });
    }

    /**
     * Recarga el archivo desde el URI al cache
     */
    private void reloadFileFromUri() {
        if (selectedSourceUri == null) {
            Log.e(TAG, "No hay URI para recargar");
            return;
        }

        Log.d(TAG, "Recargando archivo desde URI al cache...");

        new AsyncTask<Void, Void, File>() {
            @Override
            protected File doInBackground(Void... voids) {
                try {
                    // Eliminar archivo en cache si existe
                    if (selectedSourceFile != null && selectedSourceFile.exists()) {
                        selectedSourceFile.delete();
                        Log.d(TAG, "Archivo en cache eliminado: " + selectedSourceFile.getAbsolutePath());
                    }
                    
                    // Copiar nuevamente desde el URI
                    return copyUriToTempFile(selectedSourceUri);
                } catch (Exception e) {
                    Log.e(TAG, "Error recargando archivo", e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(File file) {
                if (file != null) {
                    selectedSourceFile = file;
                    Log.d(TAG, "Archivo recargado en cache: " + file.getAbsolutePath());
                    Toast.makeText(CompilerActivity.this, 
                        "✓ Archivo actualizado en memoria", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "Error al recargar archivo");
                    Toast.makeText(CompilerActivity.this, 
                        "✗ Error al actualizar archivo", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute();
    }

    /**
     * Limpia todos los archivos .so y .c temporales del directorio de cache
     */
    private void cleanupTempSoFiles() {
        File cacheDir = getCacheDir();
        File[] files = cacheDir.listFiles();
        
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".so") || name.startsWith("temp_")) {
                    if (file.delete()) {
                        Log.d(TAG, "Archivo temporal eliminado: " + file.getName());
                    }
                }
            }
        }
    }

    /**
     * Limpia TODOS los archivos del directorio de cache (método más agresivo)
     * Se usa en onResume para garantizar una recarga completa desde almacenamiento
     */
    private void cleanupAllCacheFiles() {
        File cacheDir = getCacheDir();
        File[] files = cacheDir.listFiles();
        
        if (files != null) {
            int deletedCount = 0;
            for (File file : files) {
                if (file.isFile()) {
                    if (file.delete()) {
                        deletedCount++;
                        Log.d(TAG, "Cache eliminado: " + file.getName());
                    }
                }
            }
            Log.d(TAG, "Total de archivos eliminados del cache: " + deletedCount);
        }
    }

    private void checkPermissionsAndSelectFile() {
        if (StoragePermissionHelper.hasReadPermission(this)) {
            openFilePicker();
        } else {
            StoragePermissionHelper.requestReadPermission(this);
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        
        String[] mimeTypes = {"text/plain", "text/x-c", "text/x-csrc"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                
                // Tomar persistencia del permiso para el URI
                final int takeFlags = data.getFlags() & 
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                try {
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    Log.d(TAG, "Permiso persistente tomado para URI: " + uri);
                } catch (SecurityException e) {
                    Log.w(TAG, "No se pudo tomar permiso persistente", e);
                }
                
                handleSelectedFile(uri);
            }
        } else if (requestCode == StoragePermissionHelper.REQUEST_CODE_READ_STORAGE ||
                   requestCode == StoragePermissionHelper.REQUEST_CODE_WRITE_STORAGE ||
                   requestCode == StoragePermissionHelper.REQUEST_CODE_MANAGE_STORAGE) {
            if (StoragePermissionHelper.hasReadPermission(this)) {
                Toast.makeText(this, "Permiso concedido", Toast.LENGTH_SHORT).show();
                openFilePicker();
            } else {
                Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == StoragePermissionHelper.REQUEST_CODE_READ_STORAGE ||
            requestCode == StoragePermissionHelper.REQUEST_CODE_WRITE_STORAGE) {
            if (StoragePermissionHelper.isPermissionGranted(grantResults)) {
                Toast.makeText(this, "Permiso concedido", Toast.LENGTH_SHORT).show();
                openFilePicker();
            } else {
                Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleSelectedFile(Uri uri) {
        // Detener monitoreo anterior si existe
        if (fileChangeDetector != null) {
            fileChangeDetector.stopMonitoring();
        }
        
        // Guardar URI original
        selectedSourceUri = uri;
        
        new AsyncTask<Uri, Void, File>() {
            @Override
            protected File doInBackground(Uri... uris) {
                return copyUriToTempFile(uris[0]);
            }

            @Override
            protected void onPostExecute(File file) {
                if (file != null) {
                    selectedSourceFile = file;
                    selectedFileName = file.getName();
                    
                    selectedFileText.setText("Archivo seleccionado:\n" + selectedFileName);
                    compileButton.setEnabled(true);
                    outputText.setText("Listo para compilar");
                    
                    // Resetear estado de cambios
                    fileHasChanged = false;
                    changeStatusText.setVisibility(View.GONE);
                    
                    // Deshabilitar botón ejecutar hasta nueva compilación
                    executeButton.setEnabled(false);
                    lastCompiledSoPath = null;
                    lastCompiledSoName = null;
                    lastWasExternal = false;
                    
                    // Iniciar monitoreo del archivo a través del URI
                    Log.d(TAG, "Archivo copiado a cache, iniciando monitoreo del URI");
                    startFileMonitoring();
                } else {
                    Toast.makeText(CompilerActivity.this, 
                        "Error al leer el archivo", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute(uri);
    }

    private File copyUriToTempFile(Uri uri) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        
        try {
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "No se pudo abrir InputStream para URI: " + uri);
                return null;
            }

            String fileName = "temp_source.c";
            String[] projection = {android.provider.OpenableColumns.DISPLAY_NAME};
            android.database.Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }

            File tempFile = new File(getCacheDir(), fileName);
            outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            Log.d(TAG, "Archivo copiado a: " + tempFile.getAbsolutePath());
            return tempFile;

        } catch (Exception e) {
            Log.e(TAG, "Error copiando archivo desde URI", e);
            return null;
        } finally {
            try {
                if (outputStream != null) outputStream.close();
                if (inputStream != null) inputStream.close();
            } catch (Exception e) {
                Log.e(TAG, "Error cerrando streams", e);
            }
        }
    }

    private void compileSourceFile() {
        final boolean saveToExternal = saveToExternalCheckBox.isChecked();
        
        if (saveToExternal && !StoragePermissionHelper.hasWritePermission(this)) {
            Toast.makeText(this, 
                "Se requieren permisos de escritura para guardar en almacenamiento externo", 
                Toast.LENGTH_LONG).show();
            StoragePermissionHelper.requestWritePermission(this);
            return;
        }

        new AsyncTask<Void, Void, NativeCompiler.CompilationResult>() {
            @Override
            protected void onPreExecute() {
                progressBar.setVisibility(ProgressBar.VISIBLE);
                compileButton.setEnabled(false);
                executeButton.setEnabled(false);
                outputText.setText("Compilando...\n(Eliminando versión anterior si existe)");
            }

            @Override
            protected NativeCompiler.CompilationResult doInBackground(Void... voids) {
                String outputName = selectedSourceFile.getName().replace(".c", "");
                return compiler.compile(selectedSourceFile, outputName, saveToExternal);
            }

            @Override
            protected void onPostExecute(NativeCompiler.CompilationResult result) {
                progressBar.setVisibility(ProgressBar.GONE);
                compileButton.setEnabled(true);

                StringBuilder output = new StringBuilder();
                output.append("═══ RESULTADO DE COMPILACIÓN ═══\n\n");
                // output.append(result.getCommand()).append("\n\n");
                output.append("Estado: ").append(result.isSuccess() ? "✓ ÉXITO" : "✗ ERROR").append("\n");
                output.append("Mensaje: ").append(result.getMessage()).append("\n\n");

                if (result.isSuccess() && result.getOutputPath() != null) {
                    output.append("Archivo generado:\n");
                    output.append(result.getOutputPath()).append("\n\n");
                    
                    File outputFile = new File(result.getOutputPath());
                    output.append("Tamaño: ").append(formatFileSize(outputFile.length())).append("\n");
                    output.append("Ubicación: ").append(saveToExternal ? 
                        "Almacenamiento externo (/mis_so/)" : 
                        "Almacenamiento interno (app privado)").append("\n\n");
                    
                    // Guardar la ruta REAL del .so y si está en almacenamiento externo
                    lastCompiledSoPath = result.getOutputPath();
                    lastCompiledSoName = outputFile.getName();
                    lastWasExternal = saveToExternal;
                    executeButton.setEnabled(true);
                    
                    // Resetear bandera de cambios después de compilar
                    fileHasChanged = false;
                    changeStatusText.setVisibility(View.GONE);
                    
                    // Actualizar texto de archivo seleccionado
                    selectedFileText.setText("Archivo seleccionado:\n" + selectedFileName +
                        "\n✓ Compilado exitosamente");
                    
                    output.append("✓ Presiona 'EJECUTAR MOTOR GRÁFICO' para probarlo\n\n");
                    output.append("ℹ️ La librería se cargará en un proceso separado\n");
                    output.append("que se limpia automáticamente al salir del renderizado.\n");
                }

                if (result.getOutput() != null && !result.getOutput().isEmpty()) {
                    output.append("\n═══ SALIDA DEL COMPILADOR ═══\n");
                    output.append(result.getOutput());
                }

                outputText.setText(output.toString());

                if (result.isSuccess()) {
                    Toast.makeText(CompilerActivity.this, 
                        "¡Compilación exitosa!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(CompilerActivity.this, 
                        "Error en la compilación", Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }

    private void executeCompiledLibrary() {
        if (lastCompiledSoPath == null) {
            Toast.makeText(this, "No hay ninguna librería compilada", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Advertir si el archivo ha cambiado desde la última compilación
        if (fileHasChanged) {
            Toast.makeText(this, 
                "⚠️ Advertencia: El archivo ha sido modificado\nRecompila antes de ejecutar", 
                Toast.LENGTH_LONG).show();
            return;
        }

        File soFile = new File(lastCompiledSoPath);
        if (!soFile.exists()) {
            Toast.makeText(this, "El archivo .so no existe: " + lastCompiledSoPath, 
                Toast.LENGTH_LONG).show();
            return;
        }

        // Si el .so está en almacenamiento externo, copiarlo temporalmente al cache
        if (lastWasExternal) {
            Toast.makeText(this, "Preparando librería desde almacenamiento externo...", 
                Toast.LENGTH_SHORT).show();
            
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... voids) {
                    return copyExternalSoToCache(lastCompiledSoPath, lastCompiledSoName);
                }

                @Override
                protected void onPostExecute(String tempSoPath) {
                    if (tempSoPath != null) {
                        launchRenderActivity(tempSoPath, lastCompiledSoName, true);
                    } else {
                        Toast.makeText(CompilerActivity.this, 
                            "Error al copiar librería al cache", Toast.LENGTH_LONG).show();
                    }
                }
            }.execute();
        } else {
            // Si está en almacenamiento interno, usar directamente
            launchRenderActivity(lastCompiledSoPath, lastCompiledSoName, false);
        }
    }

    /**
     * Copia un archivo .so del almacenamiento externo al cache de la app
     */
    private String copyExternalSoToCache(String externalSoPath, String soName) {
        try {
            File sourceFile = new File(externalSoPath);
            if (!sourceFile.exists()) {
                Log.e(TAG, "Archivo fuente no existe: " + externalSoPath);
                return null;
            }

            // Crear archivo temporal en cache
            File tempSoFile = new File(getCacheDir(), "temp_" + soName);
            
            // Si ya existe, eliminarlo primero
            if (tempSoFile.exists()) {
                tempSoFile.delete();
            }

            Log.d(TAG, "Copiando .so de " + externalSoPath + " a " + tempSoFile.getAbsolutePath());

            // Copiar el archivo
            FileInputStream inputStream = new FileInputStream(sourceFile);
            FileOutputStream outputStream = new FileOutputStream(tempSoFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            outputStream.flush();
            outputStream.close();
            inputStream.close();

            Log.d(TAG, "Copia completada: " + totalBytes + " bytes");
            
            return tempSoFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "Error al copiar .so al cache", e);
            return null;
        }
    }

    /**
     * Lanza RenderActivity con la ruta del .so
     * La librería se cargará en un proceso separado que se limpia automáticamente
     */
    private void launchRenderActivity(String soPath, String soName, boolean isTemporary) {
        Intent intent = new Intent(this, RenderActivity.class);
        intent.putExtra(RenderActivity.EXTRA_SO_PATH, soPath);
        intent.putExtra(RenderActivity.EXTRA_SO_NAME, soName);
        intent.putExtra(RenderActivity.EXTRA_IS_TEMPORARY, isTemporary);
        startActivity(intent);
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }
}