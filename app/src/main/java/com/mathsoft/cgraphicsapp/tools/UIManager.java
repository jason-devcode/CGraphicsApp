package com.mathsoft.cgraphicsapp;

import android.app.Activity;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TabHost;
import android.view.View;

import java.io.File;

public class UIManager {
    private Activity activity;
    private EditText codeEditor;
    private TextView consoleOutput;
    private TextView changeStatusText;
    private Button compileButton;
    private Button executeButton;
    private Button saveButton;
    private ProgressBar progressBar;
    private ProgressBar loadingIndicator;
    private TabHost tabHost;

    public UIManager(Activity activity) {
        this.activity = activity;
    }

    public void setUIComponents(EditText editor, TextView console, TextView changeStatus,
                                Button compile, Button execute, Button save, 
                                ProgressBar progress, ProgressBar loading, TabHost tabs) {
        this.codeEditor = editor;
        this.consoleOutput = console;
        this.changeStatusText = changeStatus;
        this.compileButton = compile;
        this.executeButton = execute;
        this.saveButton = save;
        this.progressBar = progress;
        this.loadingIndicator = loading;
        this.tabHost = tabs;
    }

    public void showLoadingIndicator(boolean show) {
        activity.runOnUiThread(() -> {
            loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
            codeEditor.setEnabled(!show);
            if (show) {
                codeEditor.setText("Cargando...");
            }
        });
    }

    public void showSavingIndicator(boolean saving) {
        activity.runOnUiThread(() -> {
            saveButton.setEnabled(!saving);
            saveButton.setText(saving ? "⏳" : "💾");
        });
    }

    public void updateFileName(String fileName) {
        // El TabWidget no se puede modificar fácilmente después de creado
        // Actualizamos el texto del editor como hint alternativo
    }

    public void setFileReady() {
        activity.runOnUiThread(() -> {
            changeStatusText.setVisibility(View.GONE);
            consoleOutput.setText("Listo para compilar");
            saveButton.setEnabled(true);
        });
    }

    public void showChangeDetected(String fileName) {
        activity.runOnUiThread(() -> {
            changeStatusText.setVisibility(View.VISIBLE);
            changeStatusText.setText("⚠️ CAMBIOS DETECTADOS - Recompila para actualizar");
            changeStatusText.setTextColor(0xFFFF9800);
        });
    }

    public void showSaveSuccess() {
        activity.runOnUiThread(() -> {
            changeStatusText.setVisibility(View.VISIBLE);
            changeStatusText.setText("✓ Archivo guardado exitosamente");
            changeStatusText.setTextColor(0xFF4CAF50);
            
            // Ocultar después de 2 segundos
            new android.os.Handler().postDelayed(() -> {
                changeStatusText.setVisibility(View.GONE);
            }, 2000);
        });
    }

    public void showSaveError() {
        activity.runOnUiThread(() -> {
            changeStatusText.setVisibility(View.VISIBLE);
            changeStatusText.setText("✗ Error al guardar archivo");
            changeStatusText.setTextColor(0xFFFF0000);
        });
    }

    public void showCompilationStart() {
        activity.runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            compileButton.setEnabled(false);
            executeButton.setEnabled(false);
            consoleOutput.setText("Compilando...\n(Eliminando versión anterior si existe)");
            
            // Cambiar a la pestaña de consola
            tabHost.setCurrentTabByTag("console");
        });
    }

    public void showCompilationResult(CompilationResult result, boolean saveToExternal) {
        activity.runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            compileButton.setEnabled(true);

            // Cambiar a la pestaña de consola
            tabHost.setCurrentTabByTag("console");

            StringBuilder output = new StringBuilder();
            output.append("═══ RESULTADO DE COMPILACIÓN ═══\n\n");
            output.append("Estado: ").append(result.isSuccess ? "✓ ÉXITO" : "✗ ERROR").append("\n");
            output.append("Mensaje: ").append(result.message).append("\n\n");

            if (result.isSuccess && result.outputPath != null) {
                output.append("Archivo generado:\n");
                output.append(result.outputPath).append("\n\n");
                
                File outputFile = new File(result.outputPath);
                output.append("Tamaño: ").append(formatFileSize(outputFile.length())).append("\n");
                output.append("Ubicación: ").append(saveToExternal ? 
                    "Almacenamiento externo (/mis_so/)" : 
                    "Almacenamiento interno").append("\n\n");
                
                output.append("✓ Presiona '▶' para ejecutar\n\n");
            }

            if (result.output != null && !result.output.isEmpty()) {
                output.append("\n═══ SALIDA DEL COMPILADOR ═══\n");
                output.append(result.output);
            }

            consoleOutput.setText(output.toString());
            changeStatusText.setVisibility(View.GONE);
        });
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        else return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }
}