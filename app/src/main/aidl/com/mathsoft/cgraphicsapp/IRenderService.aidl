package com.mathsoft.cgraphicsapp;

import android.view.Surface;

interface IRenderService {
    void loadLibrary(String soPath);  // Carga el .so
    void initRendering(in Surface surface);  // Inicia rendering
    void stopRendering();  // Detiene rendering
}