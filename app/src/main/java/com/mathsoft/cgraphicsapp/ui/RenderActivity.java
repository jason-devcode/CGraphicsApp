package com.mathsoft.cgraphicsapp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
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
    public static final String EXTRA_IS_TEMPORARY = "is_temporary";

    private SurfaceView surfaceView;
    private TextView infoText;
    private Button backButton;
    
    private String soPath;
    private String soName;
    private boolean isTemporary;

    private IRenderService renderService;
    private boolean bound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            renderService = IRenderService.Stub.asInterface(service);
            bound = true;
            Log.d(TAG, "Servicio binded. Cargando librería...");
            loadLibraryInService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            renderService = null;
            Log.d(TAG, "Servicio disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Obtener datos del Intent
        Intent intent = getIntent();
        soPath = intent.getStringExtra(EXTRA_SO_PATH);
        soName = intent.getStringExtra(EXTRA_SO_NAME);
        isTemporary = intent.getBooleanExtra(EXTRA_IS_TEMPORARY, false);

        if (soPath == null || soName == null) {
            Toast.makeText(this, "Error: No se especificó la librería", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.d(TAG, "SO Path: " + soPath);
        Log.d(TAG, "SO Name: " + soName);
        Log.d(TAG, "Is Temporary: " + isTemporary);

        setupUI();

        // Iniciar y bindear al servicio
        Intent serviceIntent = new Intent(this, RenderService.class);
        serviceIntent.putExtra(EXTRA_SO_PATH, soPath);  // Puedes pasar extras si necesitas, pero usamos AIDL para load
        startService(serviceIntent);  // Inicia el servicio (crea el proceso)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
    }

    private void setupUI() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(0xFF1A1A1A);

        infoText = new TextView(this);
        infoText.setText("Cargando motor gráfico...\n" + soName);
        infoText.setTextColor(0xFFFFFFFF);
        infoText.setPadding(8, 8, 8, 8);  // Padding reducido
        infoText.setTextSize(10);  // Tamaño de texto más pequeño
        infoText.setBackgroundColor(0x40000000);  // Fondo semi-transparente (25% opacidad)
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,  // Ancho ajustado al contenido
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        infoParams.setMargins(8, 8, 8, 8);  // Márgenes pequeños
        infoText.setLayoutParams(infoParams);

        surfaceView = new SurfaceView(this);
        surfaceView.setZOrderOnTop(false);
        LinearLayout.LayoutParams surfaceParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1.0f
        );
        surfaceView.setLayoutParams(surfaceParams);

        backButton = new Button(this);
        backButton.setText("⬅ VOLVER");
        backButton.setTextSize(11);  // Texto del botón más pequeño
        backButton.setPadding(16, 8, 16, 8);  // Padding vertical reducido
        backButton.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,  // Ancho ajustado al contenido
            ViewGroup.LayoutParams.WRAP_CONTENT   // Alto ajustado al contenido
        );
        buttonParams.setMargins(8, 8, 8, 8);  // Márgenes más pequeños
        backButton.setLayoutParams(buttonParams);

        // mainLayout.addView(infoText);
        mainLayout.addView(surfaceView);
        mainLayout.addView(backButton);

        setContentView(mainLayout);
    }

    private void loadLibraryInService() {
        if (!bound) return;
        try {
            // Verificar archivo
            File soFile = new File(soPath);
            if (!soFile.exists()) {
                throw new Exception("El archivo .so no existe");
            }

            infoText.setText("Cargando: " + soName + "\n" + soPath +
                (isTemporary ? "\n(Copia temporal)" : ""));

            renderService.loadLibrary(soPath);
            infoText.setText("✓ Librería cargada en servicio aislado\nInicializando rendering...");

            setupSurface();

        } catch (RemoteException e) {
            Log.e(TAG, "Error en IPC: " + e.getMessage(), e);
            infoText.setText("✗ Error en comunicación con servicio");
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage(), e);
            infoText.setText("✗ " + e.getMessage());
        }
    }

    private void setupSurface() {
        // Configurar formato
        surfaceView.getHolder().setFormat(android.graphics.PixelFormat.RGBA_8888);

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.d(TAG, "surfaceCreated");
                if (holder.getSurface().isValid() && bound) {
                    startRenderingInService(holder.getSurface());
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
                stopRenderingInService();
            }
        });

        SurfaceHolder holder = surfaceView.getHolder();
        if (holder.getSurface() != null && holder.getSurface().isValid()) {
            Log.d(TAG, "Surface ya disponible");
            startRenderingInService(holder.getSurface());
        }
    }

    private void startRenderingInService(Surface surface) {
        if (!bound) return;
        try {
            Log.d(TAG, "Pasando Surface al servicio - isValid: " + surface.isValid());
            
            // Verificar que el Surface sea válido antes de enviar
            if (!surface.isValid()) {
                Log.e(TAG, "Surface no válido antes de IPC");
                return;
            }
            
            renderService.initRendering(surface);
            Log.d(TAG, "initRendering llamado vía IPC");
            Toast.makeText(this, "Rendering iniciado en servicio", Toast.LENGTH_SHORT).show();
        } catch (RemoteException e) {
            Log.e(TAG, "Error al iniciar rendering vía IPC: " + e.getMessage(), e);
            infoText.setText("✗ Error en rendering");
        }
    }

    private void stopRenderingInService() {
        if (!bound) return;
        try {
            renderService.stopRendering();
        } catch (RemoteException e) {
            Log.e(TAG, "Error al detener rendering vía IPC: " + e.getMessage(), e);
        }
    }

    private void deleteTemporarySo() {
        if (isTemporary && soPath != null) {
            File tempFile = new File(soPath);
            if (tempFile.exists()) {
                if (tempFile.delete()) {
                    Log.d(TAG, "Archivo .so temporal eliminado: " + soPath);
                } else {
                    Log.w(TAG, "No se pudo eliminar el archivo .so temporal: " + soPath);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        stopRenderingInService();
        
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        
        // IMPORTANTE: Detener el servicio para MATAR el proceso
        Intent serviceIntent = new Intent(this, RenderService.class);
        stopService(serviceIntent);
        
        // Dar tiempo para que el proceso termine
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        deleteTemporarySo();
        
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        stopRenderingInService();
        deleteTemporarySo();
        super.onBackPressed();
    }
}