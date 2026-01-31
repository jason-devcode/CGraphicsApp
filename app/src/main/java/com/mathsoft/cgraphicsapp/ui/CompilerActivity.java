package com.mathsoft.cgraphicsapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class CompilerActivity extends Activity {

    private static final int REQUEST_CODE_PICK_FILE = 200;

    private TextView selectedFileText;
    private TextView outputText;
    private Button selectFileButton;
    private Button compileButton;
    private Button executeButton;
    private CheckBox saveToExternalCheckBox;
    private ProgressBar progressBar;
    
    private File selectedSourceFile;
    private NativeCompiler compiler;
    private String lastCompiledSoPath = null;
    private String lastCompiledSoName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.compiler_activity);

        selectedFileText = findViewById(R.id.selected_file_text);
        outputText = findViewById(R.id.output_text);
        selectFileButton = findViewById(R.id.select_file_button);
        compileButton = findViewById(R.id.compile_button);
        executeButton = findViewById(R.id.execute_button);
        saveToExternalCheckBox = findViewById(R.id.save_external_checkbox);
        progressBar = findViewById(R.id.compile_progress_bar);

        compiler = new NativeCompiler(this);

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
        new AsyncTask<Uri, Void, File>() {
            @Override
            protected File doInBackground(Uri... uris) {
                return copyUriToTempFile(uris[0]);
            }

            @Override
            protected void onPostExecute(File file) {
                if (file != null) {
                    selectedSourceFile = file;
                    selectedFileText.setText("Archivo seleccionado:\n" + file.getName());
                    compileButton.setEnabled(true);
                    outputText.setText("Listo para compilar");
                    
                    // Deshabilitar botón ejecutar hasta nueva compilación
                    executeButton.setEnabled(false);
                    lastCompiledSoPath = null;
                    lastCompiledSoName = null;
                } else {
                    Toast.makeText(CompilerActivity.this, 
                        "Error al leer el archivo", Toast.LENGTH_SHORT).show();
                }
            }
        }.execute(uri);
    }

    private File copyUriToTempFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
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
            e.printStackTrace();
            return null;
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
                    
                    // CORRECCIÓN: Guardar la ruta REAL del .so (no asumir ubicación)
                    lastCompiledSoPath = result.getOutputPath();
                    lastCompiledSoName = outputFile.getName();
                    executeButton.setEnabled(true);
                    
                    output.append("✓ Presiona 'EJECUTAR MOTOR GRÁFICO' para probarlo\n\n");
                }

                if (result.getOutput() != null && !result.getOutput().isEmpty()) {
                    output.append("═══ SALIDA DEL COMPILADOR ═══\n");
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

        File soFile = new File(lastCompiledSoPath);
        if (!soFile.exists()) {
            Toast.makeText(this, "El archivo .so no existe: " + lastCompiledSoPath, 
                Toast.LENGTH_LONG).show();
            return;
        }

        // CORRECCIÓN: Pasar la ruta REAL del .so (puede estar en externo o interno)
        Intent intent = new Intent(this, RenderActivity.class);
        intent.putExtra(RenderActivity.EXTRA_SO_PATH, lastCompiledSoPath);
        intent.putExtra(RenderActivity.EXTRA_SO_NAME, lastCompiledSoName != null ? 
            lastCompiledSoName : soFile.getName());
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