package com.mathsoft.cgraphicsapp;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

public class RenderService extends Service {
    private static final String TAG = "RenderService";

    private long nativeHandle = 0;
    private boolean libraryLoaded = false;
    private Surface currentSurface = null;

    // Métodos nativos
    private native long nativeInitRendering(Surface surface);
    private native void nativeStopRendering(long handle);

    private final IRenderService.Stub binder = new IRenderService.Stub() {
        @Override
        public void loadLibrary(String path) throws RemoteException {
            try {
                Log.d(TAG, "Cargando librería en proceso aislado (PID: " + 
                    android.os.Process.myPid() + "): " + path);
                System.load(path);
                libraryLoaded = true;
                Log.d(TAG, "✓ Librería cargada exitosamente en proceso separado");
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "Error al cargar librería: " + e.getMessage(), e);
                throw new RemoteException("Error cargando librería: " + e.getMessage());
            }
        }

        @Override
        public void initRendering(Surface surface) throws RemoteException {
            if (!libraryLoaded) {
                throw new RemoteException("Librería no cargada");
            }
            
            if (surface == null) {
                throw new RemoteException("Surface es null");
            }

            try {
                // El Surface es Parcelable, así que puede cruzar IPC
                // Pero necesitamos verificar que sea válido DESPUÉS de IPC
                Log.d(TAG, "Surface recibido vía IPC - isValid: " + surface.isValid());
                
                if (!surface.isValid()) {
                    throw new RemoteException("Surface inválido después de IPC");
                }
                
                currentSurface = surface;
                Log.d(TAG, "Iniciando rendering nativo...");
                nativeHandle = nativeInitRendering(surface);
                Log.d(TAG, "✓ Rendering iniciado - handle: " + nativeHandle);
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "Error en método nativo: " + e.getMessage(), e);
                throw new RemoteException("Error nativo: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Error iniciando rendering: " + e.getMessage(), e);
                throw new RemoteException("Error: " + e.getMessage());
            }
        }

        @Override
        public void stopRendering() throws RemoteException {
            if (nativeHandle != 0) {
                try {
                    Log.d(TAG, "Deteniendo rendering...");
                    nativeStopRendering(nativeHandle);
                    nativeHandle = 0;
                    
                    // Liberar referencia al Surface
                    if (currentSurface != null) {
                        currentSurface.release();
                        currentSurface = null;
                    }
                    
                    Log.d(TAG, "✓ Rendering detenido");
                } catch (Exception e) {
                    Log.e(TAG, "Error al detener rendering: " + e.getMessage(), e);
                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind - PID: " + android.os.Process.myPid());
        return binder;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy - Matando proceso...");
        
        if (nativeHandle != 0) {
            try {
                nativeStopRendering(nativeHandle);
                nativeHandle = 0;
            } catch (Exception e) {
                Log.e(TAG, "Error en onDestroy", e);
            }
        }
        
        if (currentSurface != null) {
            currentSurface.release();
            currentSurface = null;
        }
        
        super.onDestroy();
        
        // ⚠️ FORZAR MUERTE DEL PROCESO
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}