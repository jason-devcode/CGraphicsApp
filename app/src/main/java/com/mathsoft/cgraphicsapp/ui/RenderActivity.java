package com.mathsoft.cgraphicsapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import java.io.File;

public class RenderActivity extends Activity {

    private static final String TAG = "RenderActivity";
    public static final String EXTRA_SO_PATH = "so_path";
    public static final String EXTRA_SO_NAME = "so_name";

    private SurfaceView surfaceView;
    private TextView infoText;
    private Button backButton;
    
    private String soPath;
    private String soName;
    private boolean libraryLoaded = false;
    private boolean renderingActive = false;

    // Métodos nativos
    private native void initRendering(Surface surface);
    private native void stopRendering();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Obtener datos del Intent
        Intent intent = getIntent();
        soPath = intent.getStringExtra(EXTRA_SO_PATH);
        soName = intent.getStringExtra(EXTRA_SO_NAME);

        if (soPath == null || soName == null) {
            Toast.makeText(this, "Error: No se especificó la librería", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupUI();
        loadNativeLibrary();
    }

    private void setupUI() {
        // Layout principal
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(0xFF1A1A1A);

        // Texto de información
        infoText = new TextView(this);
        infoText.setText("Cargando motor gráfico...\n" + soName);
        infoText.setTextColor(0xFFFFFFFF);
        infoText.setPadding(16, 16, 16, 16);
        infoText.setTextSize(14);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        infoText.setLayoutParams(infoParams);

        // SurfaceView para el renderizado
        surfaceView = new SurfaceView(this);
        surfaceView.setZOrderOnTop(false);
        LinearLayout.LayoutParams surfaceParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f  // weight para que ocupe todo el espacio disponible
        );
        surfaceView.setLayoutParams(surfaceParams);

        // Botón para volver
        backButton = new Button(this);
        backButton.setText("⬅ VOLVER AL COMPILADOR");
        backButton.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(16, 16, 16, 16);
        backButton.setLayoutParams(buttonParams);

        // Agregar vistas al layout
        mainLayout.addView(infoText);
        mainLayout.addView(surfaceView);
        mainLayout.addView(backButton);

        setContentView(mainLayout);
    }

    private void loadNativeLibrary() {
        try {
            Log.d(TAG, "Intentando cargar librería: " + soPath);
            infoText.setText("Cargando: " + soName + "\n" + soPath);

            // Verificar que el archivo existe
            File soFile = new File(soPath);
            if (!soFile.exists()) {
                throw new Exception("El archivo .so no existe");
            }

            // Cargar la librería
            System.load(soPath);
            libraryLoaded = true;

            Log.d(TAG, "Librería cargada exitosamente");
            infoText.setText("✓ Librería cargada: " + soName + "\nInicializando rendering...");

            // Configurar el SurfaceView
            setupSurface();

        } catch (UnsatisfiedLinkError e) {
            String error = "Error al cargar librería: " + e.getMessage();
            Log.e(TAG, error, e);
            infoText.setText("✗ " + error);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            libraryLoaded = false;
        } catch (Exception e) {
            String error = "Error: " + e.getMessage();
            Log.e(TAG, error, e);
            infoText.setText("✗ " + error);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            libraryLoaded = false;
        }
    }

    private void setupSurface() {
        if (!libraryLoaded) {
            return;
        }

        // Configurar formato de píxeles
        surfaceView.getHolder().setFormat(android.graphics.PixelFormat.RGBA_8888);

        // Configurar callback del surface
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "surfaceCreated");
                
                if (holder.getSurface().isValid() && libraryLoaded && !renderingActive) {
                    startRendering(holder.getSurface());
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "surfaceChanged: " + width + "x" + height);
                infoText.setText("✓ Rendering activo\n" + 
                               "Resolución: " + width + "x" + height + "\n" +
                               "Librería: " + soName);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "surfaceDestroyed");
                stopRenderingIfActive();
            }
        });

        // Si el surface ya está disponible, iniciar inmediatamente
        SurfaceHolder holder = surfaceView.getHolder();
        if (holder.getSurface() != null && holder.getSurface().isValid()) {
            Log.d(TAG, "Surface ya disponible, iniciando rendering directamente");
            startRendering(holder.getSurface());
        }
    }

    private void startRendering(Surface surface) {
        if (!libraryLoaded || renderingActive) {
            return;
        }

        try {
            Log.d(TAG, "Iniciando rendering...");
            initRendering(surface);
            renderingActive = true;
            Log.d(TAG, "Rendering iniciado exitosamente");
            
            runOnUiThread(() -> {
                infoText.setText("✓ Motor gráfico en ejecución\n" + soName);
                Toast.makeText(this, "Rendering iniciado", Toast.LENGTH_SHORT).show();
            });

        } catch (UnsatisfiedLinkError e) {
            String error = "Error: Método initRendering no encontrado en el .so";
            Log.e(TAG, error, e);
            runOnUiThread(() -> {
                infoText.setText("✗ " + error);
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            });
            renderingActive = false;
        } catch (Exception e) {
            String error = "Error al iniciar rendering: " + e.getMessage();
            Log.e(TAG, error, e);
            runOnUiThread(() -> {
                infoText.setText("✗ " + error);
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            });
            renderingActive = false;
        }
    }

    private void stopRenderingIfActive() {
        if (!renderingActive) {
            return;
        }

        try {
            Log.d(TAG, "Deteniendo rendering...");
            stopRendering();
            renderingActive = false;
            Log.d(TAG, "Rendering detenido");

        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Método stopRendering no encontrado", e);
        } catch (Exception e) {
            Log.e(TAG, "Error al detener rendering", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        // No detenemos el rendering en onPause para que continúe en background
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        stopRenderingIfActive();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Detener rendering antes de salir
        stopRenderingIfActive();
        super.onBackPressed();
    }
}