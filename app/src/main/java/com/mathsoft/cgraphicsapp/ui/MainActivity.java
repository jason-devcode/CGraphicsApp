package com.mathsoft.cgraphicsapp;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends Activity {

    private ClangCompilerManager compilerManager;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView systemInfo;
    private TextView assetsList;
    private Button compileButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);
        systemInfo = findViewById(R.id.system_info);
        assetsList = findViewById(R.id.assets_list);
        compileButton = findViewById(R.id.compile_button);

        compilerManager = new ClangCompilerManager(this);

        // Mostrar información del sistema
        displaySystemInfo();

        // Configurar botón de compilación
        compileButton.setEnabled(false);
        compileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CompilerActivity.class);
                startActivity(intent);
            }
        });

        // Verificar e instalar el compilador
        if (!compilerManager.isCompilerInstalled()) {
            installCompilerAsync();
        } else {
            statusText.setText("✓ Compilador ya instalado");
            compileButton.setEnabled(true);
            Toast.makeText(this, "Compilador disponible", Toast.LENGTH_SHORT).show();
            displayCompilerFiles();
        }
    }

    private void displaySystemInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Android Version: ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        info.append("Device ABI: ").append(getABI()).append("\n");
        info.append("Device: ").append(Build.MANUFACTURER)
            .append(" ").append(Build.MODEL).append("\n");
        info.append("App Files Dir: ").append(getFilesDir().getAbsolutePath()).append("\n");

        systemInfo.setText(info.toString());
    }

    private String getABI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Build.SUPPORTED_ABIS[0];
        } else {
            return Build.CPU_ABI;
        }
    }

    private void installCompilerAsync() {
        new AsyncTask<Void, String, Boolean>() {
            private int fileCount = 0;

            @Override
            protected void onPreExecute() {
                progressBar.setVisibility(ProgressBar.VISIBLE);
                statusText.setText("Iniciando instalación...");
                compileButton.setEnabled(false);
            }

            @Override
            protected Boolean doInBackground(Void... voids) {
                compilerManager.setCopyCallback(new ClangCompilerManager.CopyCallback() {
                    @Override
                    public void onCopyStarted() {
                        publishProgress("⟳ Copiando compilador...", "0");
                    }

                    @Override
                    public void onCopyProgress(String currentFile) {
                        fileCount++;
                        String fileName = new File(currentFile).getName();
                        publishProgress("⟳ Copiando: " + fileName, String.valueOf(fileCount));
                    }

                    @Override
                    public void onCopyCompleted(boolean success) {
                        if (success) {
                            publishProgress("✓ Instalación completada", String.valueOf(fileCount));
                        } else {
                            publishProgress("✗ Error en la instalación", String.valueOf(fileCount));
                        }
                    }
                });

                return compilerManager.installCompiler();
            }

            @Override
            protected void onProgressUpdate(String... values) {
                statusText.setText(values[0] + " (" + values[1] + " archivos)");
            }

            @Override
            protected void onPostExecute(Boolean success) {
                progressBar.setVisibility(ProgressBar.GONE);
                if (success) {
                    statusText.setText("✓ Compilador instalado correctamente (" + fileCount + " archivos)");
                    Toast.makeText(MainActivity.this, "Instalación exitosa", Toast.LENGTH_SHORT).show();
                    compileButton.setEnabled(true);
                    displayCompilerFiles();
                } else {
                    statusText.setText("✗ Error al instalar compilador");
                    Toast.makeText(MainActivity.this, "Error en la instalación", Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }

    private void displayCompilerFiles() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                File compilerDir = compilerManager.getCompilerDirectory();
                if (compilerDir.exists()) {
                    return "Compilador instalado en:\n" + 
                           compilerDir.getAbsolutePath() + "\n\n" +
                           getFileTreeBranch(compilerDir, 0, 2); // Limitar a 2 niveles
                } else {
                    return "Compilador no instalado";
                }
            }

            @Override
            protected void onPostExecute(String tree) {
                assetsList.setText(tree);
            }
        }.execute();
    }

    private String getFileTreeBranch(File directory, int level, int maxLevel) {
        if (level >= maxLevel) {
            return "";
        }

        StringBuilder tree = new StringBuilder();
        File[] files = directory.listFiles();

        if (files == null) {
            return "";
        }

        String tabs = genTabs(level);
        for (File file : files) {
            tree.append(tabs).append(file.getName());
            if (file.isDirectory()) {
                tree.append("/\n");
                tree.append(getFileTreeBranch(file, level + 1, maxLevel));
            } else {
                long size = file.length();
                String sizeStr = formatFileSize(size);
                tree.append(" (").append(sizeStr).append(")");
                if (file.canExecute()) {
                    tree.append(" [x]");
                }
                tree.append("\n");
            }
        }
        return tree.toString();
    }

    private String genTabs(int tabsCount) {
        StringBuilder tabs = new StringBuilder();
        for (int i = 0; i < tabsCount; ++i) {
            tabs.append("  ");
        }
        return tabs.toString();
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