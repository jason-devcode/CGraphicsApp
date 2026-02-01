package com.mathsoft.cgraphicsapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.widget.TabHost;
import android.widget.EditText;

import java.io.File;

public class CompilerActivity extends Activity {

    private static final String TAG = "CompilerActivity";
    private static final int REQUEST_CODE_PICK_FILE = 200;

    // UI Components
    private TabHost tabHost;
    private EditText codeEditor;
    private TextView consoleOutput;
    private TextView changeStatusText;
    private Button selectFileButton;
    private Button compileButton;
    private Button executeButton;
    private Button saveButton;
    private CheckBox saveToExternalCheckBox;
    private ProgressBar progressBar;
    private ProgressBar loadingIndicator;
    
    // Core Components
    private FileManager fileManager;
    private CompilationManager compilationManager;
    private FileChangeDetector fileChangeDetector;
    private UIManager uiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.compiler_activity);

        initializeComponents();
        setupUI();
        setupListeners();
    }

    private void initializeComponents() {
        fileManager = new FileManager(this);
        compilationManager = new CompilationManager(this);
        fileChangeDetector = new FileChangeDetector();
        uiManager = new UIManager(this);
    }

    private void setupUI() {
        // TabHost
        tabHost = findViewById(R.id.tabHost);
        tabHost.setup();

        // Tab 1: Editor
        TabHost.TabSpec editorTab = tabHost.newTabSpec("editor");
        editorTab.setIndicator("file.c");
        editorTab.setContent(R.id.tab_editor);
        tabHost.addTab(editorTab);

        // Tab 2: Console
        TabHost.TabSpec consoleTab = tabHost.newTabSpec("console");
        consoleTab.setIndicator("console");
        consoleTab.setContent(R.id.tab_console);
        tabHost.addTab(consoleTab);

        // UI Elements
        codeEditor = findViewById(R.id.code_editor);
        consoleOutput = findViewById(R.id.console_output);
        changeStatusText = findViewById(R.id.change_status_text);
        selectFileButton = findViewById(R.id.select_file_button);
        compileButton = findViewById(R.id.compile_button);
        executeButton = findViewById(R.id.execute_button);
        saveButton = findViewById(R.id.save_button);
        saveToExternalCheckBox = findViewById(R.id.save_external_checkbox);
        progressBar = findViewById(R.id.compile_progress_bar);
        loadingIndicator = findViewById(R.id.loading_indicator);

        compileButton.setEnabled(false);
        executeButton.setEnabled(false);
        saveButton.setEnabled(false);

        uiManager.setUIComponents(codeEditor, consoleOutput, changeStatusText, 
            compileButton, executeButton, saveButton, progressBar, loadingIndicator, tabHost);
    }

    private void setupListeners() {
        selectFileButton.setOnClickListener(v -> {
            if (StoragePermissionHelper.hasReadPermission(this)) {
                fileManager.openFilePicker();
            } else {
                StoragePermissionHelper.requestReadPermission(this);
            }
        });

        saveButton.setOnClickListener(v -> {
            saveCurrentFile();
        });

        compileButton.setOnClickListener(v -> {
            File sourceFile = fileManager.getSelectedSourceFile();
            if (sourceFile != null) {
                // Guardar antes de compilar
                saveCurrentFile();
                
                boolean saveToExternal = saveToExternalCheckBox.isChecked();
                
                if (saveToExternal && !StoragePermissionHelper.hasWritePermission(this)) {
                    Toast.makeText(this, 
                        "Se requieren permisos de escritura para guardar en almacenamiento externo", 
                        Toast.LENGTH_LONG).show();
                    StoragePermissionHelper.requestWritePermission(this);
                    return;
                }
                
                uiManager.showCompilationStart();
                
                compilationManager.compile(sourceFile, saveToExternal, 
                    new CompilationManager.CompilationCallback() {
                        @Override
                        public void onCompilationComplete(CompilationResult result) {
                            handleCompilationResult(result, saveToExternal);
                        }
                    });
            }
        });

        executeButton.setOnClickListener(v -> {
            compilationManager.execute(new CompilationManager.ExecutionCallback() {
                @Override
                public void onExecutionReady(String soPath, String soName, boolean isTemporary) {
                    launchRenderActivity(soPath, soName, isTemporary);
                }

                @Override
                public void onExecutionError(String error) {
                    Toast.makeText(CompilerActivity.this, error, Toast.LENGTH_LONG).show();
                }
            });
        });

        // Tab click listener
        tabHost.setOnTabChangedListener(tabId -> {
            if ("editor".equals(tabId)) {
                syncEditorWithFile();
            }
        });
    }

    private void saveCurrentFile() {
        String content = codeEditor.getText().toString();
        
        uiManager.showSavingIndicator(true);
        
        fileManager.saveContent(content, success -> {
            uiManager.showSavingIndicator(false);
            
            if (success) {
                uiManager.showSaveSuccess();
                Toast.makeText(this, "✓ Archivo guardado", Toast.LENGTH_SHORT).show();
            } else {
                uiManager.showSaveError();
                Toast.makeText(this, "✗ Error al guardar", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleCompilationResult(CompilationResult result, boolean saveToExternal) {
        uiManager.showCompilationResult(result, saveToExternal);
        
        if (result.isSuccess) {
            compilationManager.setLastCompilation(result.outputPath, 
                new File(result.outputPath).getName(), saveToExternal);
            executeButton.setEnabled(true);
            fileManager.resetChangeFlag();
        }
    }

    private void syncEditorWithFile() {
        File sourceFile = fileManager.getSelectedSourceFile();
        if (sourceFile != null && sourceFile.exists()) {
            String content = fileManager.readFileContent(sourceFile);
            if (content != null && !content.equals(codeEditor.getText().toString())) {
                codeEditor.setText(content);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        Uri selectedUri = fileManager.getSelectedSourceUri();
        if (selectedUri != null) {
            uiManager.showLoadingIndicator(true);
            
            fileManager.reloadFromStorage(new FileManager.FileLoadCallback() {
                @Override
                public void onFileLoaded(File file, String content) {
                    uiManager.showLoadingIndicator(false);
                    codeEditor.setText(content);
                    codeEditor.setEnabled(true);
                    uiManager.updateFileName(file.getName());
                    compilationManager.reset();
                    executeButton.setEnabled(false);
                    saveButton.setEnabled(true);
                    
                    startFileMonitoring();
                    
                    Toast.makeText(CompilerActivity.this, 
                        "✓ Archivo sincronizado", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFileLoadError(String error) {
                    uiManager.showLoadingIndicator(false);
                    Toast.makeText(CompilerActivity.this, 
                        "✗ Error: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        fileChangeDetector.stopMonitoring();
        
        // Guardar cambios del editor automáticamente
        if (fileManager.getSelectedSourceFile() != null) {
            String content = codeEditor.getText().toString();
            fileManager.saveContentToFile(fileManager.getSelectedSourceFile(), content);
        }
    }

    @Override
    protected void onDestroy() {
        fileChangeDetector.stopMonitoring();
        fileManager.cleanup();
        super.onDestroy();
    }

    private void startFileMonitoring() {
        Uri uri = fileManager.getSelectedSourceUri();
        if (uri == null) return;

        fileChangeDetector.startMonitoring(uri, getContentResolver(), 
            (changedUri, newHash) -> {
                runOnUiThread(() -> {
                    uiManager.showChangeDetected(fileManager.getSelectedFileName());
                    fileManager.setFileChanged(true);
                    
                    // Recargar archivo en el editor
                    fileManager.reloadFromUri(changedUri, new FileManager.FileLoadCallback() {
                        @Override
                        public void onFileLoaded(File file, String content) {
                            codeEditor.setText(content);
                            Toast.makeText(CompilerActivity.this, 
                                "⚠️ Archivo actualizado externamente", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFileLoadError(String error) {
                            Log.e(TAG, "Error recargando archivo: " + error);
                        }
                    });
                });
            });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                
                // Tomar permisos persistentes
                try {
                    final int takeFlags = data.getFlags() & 
                        (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                } catch (SecurityException e) {
                    Log.w(TAG, "No se pudo tomar permiso persistente", e);
                }
                
                handleFileSelected(uri);
            }
        } else {
            fileManager.handlePermissionResult(requestCode, resultCode, data);
        }
    }

    private void handleFileSelected(Uri uri) {
        fileChangeDetector.stopMonitoring();
        
        uiManager.showLoadingIndicator(true);
        
        fileManager.loadFile(uri, new FileManager.FileLoadCallback() {
            @Override
            public void onFileLoaded(File file, String content) {
                uiManager.showLoadingIndicator(false);
                codeEditor.setText(content);
                codeEditor.setEnabled(true);
                uiManager.updateFileName(file.getName());
                uiManager.setFileReady();
                compileButton.setEnabled(true);
                executeButton.setEnabled(false);
                saveButton.setEnabled(true);
                
                compilationManager.reset();
                startFileMonitoring();
            }

            @Override
            public void onFileLoadError(String error) {
                uiManager.showLoadingIndicator(false);
                Toast.makeText(CompilerActivity.this, 
                    "Error al cargar archivo: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        fileManager.handlePermissionResult(requestCode, 0, null);
    }

    private void launchRenderActivity(String soPath, String soName, boolean isTemporary) {
        Intent intent = new Intent(this, RenderActivity.class);
        intent.putExtra(RenderActivity.EXTRA_SO_PATH, soPath);
        intent.putExtra(RenderActivity.EXTRA_SO_NAME, soName);
        intent.putExtra(RenderActivity.EXTRA_IS_TEMPORARY, isTemporary);
        startActivity(intent);
    }
}