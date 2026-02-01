package com.mathsoft.cgraphicsapp;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class CompilationManager {
    private static final String TAG = "CompilationManager";

    private Context context;
    private NativeCompiler compiler;
    private String lastCompiledSoPath;
    private String lastCompiledSoName;
    private boolean lastWasExternal;

    public interface CompilationCallback {
        void onCompilationComplete(CompilationResult result);
    }

    public interface ExecutionCallback {
        void onExecutionReady(String soPath, String soName, boolean isTemporary);
        void onExecutionError(String error);
    }

    public CompilationManager(Context context) {
        this.context = context;
        this.compiler = new NativeCompiler(context);
    }
    
    private String getUniqueLibraryName(String baseName) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        return baseName + "_" + timestamp;
    }
    

    public void compile(File sourceFile, boolean saveToExternal, CompilationCallback callback) {
        new AsyncTask<Void, Void, NativeCompiler.CompilationResult>() {
            @Override
            protected NativeCompiler.CompilationResult doInBackground(Void... voids) {
                String outputName = sourceFile.getName().replace(".c", "");
                return compiler.compile(sourceFile, getUniqueLibraryName(outputName), saveToExternal);
            }

            @Override
            protected void onPostExecute(NativeCompiler.CompilationResult result) {
                CompilationResult res = new CompilationResult();
                res.isSuccess = result.isSuccess();
                res.message = result.getMessage();
                res.output = result.getOutput();
                res.outputPath = result.getOutputPath();
                res.command = result.getCommand();
                callback.onCompilationComplete(res);
            }
        }.execute();
    }

    public void execute(ExecutionCallback callback) {
        if (lastCompiledSoPath == null) {
            callback.onExecutionError("No hay ninguna librería compilada");
            return;
        }

        File soFile = new File(lastCompiledSoPath);
        if (!soFile.exists()) {
            callback.onExecutionError("El archivo .so no existe");
            return;
        }

        if (lastWasExternal) {
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... voids) {
                    return copyExternalSoToCache(lastCompiledSoPath, lastCompiledSoName);
                }

                @Override
                protected void onPostExecute(String tempSoPath) {
                    if (tempSoPath != null) {
                        callback.onExecutionReady(tempSoPath, lastCompiledSoName, true);
                    } else {
                        callback.onExecutionError("Error al copiar librería");
                    }
                }
            }.execute();
        } else {
            callback.onExecutionReady(lastCompiledSoPath, lastCompiledSoName, false);
        }
    }

    private String copyExternalSoToCache(String externalSoPath, String soName) {
        try {
            File sourceFile = new File(externalSoPath);
            if (!sourceFile.exists()) return null;

            File tempSoFile = new File(context.getCacheDir(), "temp_" + soName);
            if (tempSoFile.exists()) tempSoFile.delete();

            FileInputStream inputStream = new FileInputStream(sourceFile);
            FileOutputStream outputStream = new FileOutputStream(tempSoFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();
            return tempSoFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error copiando .so", e);
            return null;
        }
    }

    public void setLastCompilation(String soPath, String soName, boolean wasExternal) {
        this.lastCompiledSoPath = soPath;
        this.lastCompiledSoName = soName;
        this.lastWasExternal = wasExternal;
    }

    public void reset() {
        lastCompiledSoPath = null;
        lastCompiledSoName = null;
        lastWasExternal = false;
    }
}