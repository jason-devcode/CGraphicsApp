package com.mathsoft.cgraphicsapp;

import android.os.Build;

public final class SystemInfo {
    public static String getABI() {
        return Build.SUPPORTED_ABIS[0];
    }
}
