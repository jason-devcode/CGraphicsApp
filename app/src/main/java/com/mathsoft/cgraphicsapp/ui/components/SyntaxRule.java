package com.mathsoft.cgraphicsapp;

import java.util.regex.Pattern;

/**
 * Representa una regla de resaltado de sintaxis
 */
public class SyntaxRule {
    
    private String patternString;
    private Pattern pattern;
    private int color;
    private boolean multiline;
    private int priority;
    
    /**
     * Constructor básico
     * @param patternString Expresión regular para el patrón
     * @param color Color en formato ARGB (0xAARRGGBB)
     */
    public SyntaxRule(String patternString, int color) {
        this(patternString, color, false, 0);
    }
    
    /**
     * Constructor con soporte multilinea
     * @param patternString Expresión regular para el patrón
     * @param color Color en formato ARGB (0xAARRGGBB)
     * @param multiline Si el patrón debe coincidir en múltiples líneas
     */
    public SyntaxRule(String patternString, int color, boolean multiline) {
        this(patternString, color, multiline, 0);
    }
    
    /**
     * Constructor completo
     * @param patternString Expresión regular para el patrón
     * @param color Color en formato ARGB (0xAARRGGBB)
     * @param multiline Si el patrón debe coincidir en múltiples líneas
     * @param priority Prioridad de la regla (mayor = se aplica después)
     */
    public SyntaxRule(String patternString, int color, boolean multiline, int priority) {
        this.patternString = patternString;
        this.color = color;
        this.multiline = multiline;
        this.priority = priority;
        
        compilePattern();
    }
    
    /**
     * Compila el patrón de expresión regular
     */
    private void compilePattern() {
        int flags = 0;  // Sin flags especiales por defecto (case sensitive es el comportamiento por defecto)
        
        if (multiline) {
            flags = Pattern.MULTILINE | Pattern.DOTALL;
        }
        
        try {
            this.pattern = Pattern.compile(patternString, flags);
        } catch (Exception e) {
            android.util.Log.e("SyntaxRule", 
                "Error compilando patrón: " + patternString, e);
            // Usar patrón que nunca coincide en caso de error
            this.pattern = Pattern.compile("(?!)");
        }
    }
    
    // Getters
    
    public String getPatternString() {
        return patternString;
    }
    
    public Pattern getPattern() {
        return pattern;
    }
    
    public int getColor() {
        return color;
    }
    
    public boolean isMultiline() {
        return multiline;
    }
    
    public int getPriority() {
        return priority;
    }
    
    // Setters
    
    public void setPatternString(String patternString) {
        this.patternString = patternString;
        compilePattern();
    }
    
    public void setColor(int color) {
        this.color = color;
    }
    
    public void setMultiline(boolean multiline) {
        if (this.multiline != multiline) {
            this.multiline = multiline;
            compilePattern();
        }
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    @Override
    public String toString() {
        return "SyntaxRule{" +
                "pattern='" + patternString + '\'' +
                ", color=" + String.format("0x%08X", color) +
                ", multiline=" + multiline +
                ", priority=" + priority +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        SyntaxRule that = (SyntaxRule) o;
        
        if (color != that.color) return false;
        if (multiline != that.multiline) return false;
        if (priority != that.priority) return false;
        return patternString.equals(that.patternString);
    }
    
    @Override
    public int hashCode() {
        int result = patternString.hashCode();
        result = 31 * result + color;
        result = 31 * result + (multiline ? 1 : 0);
        result = 31 * result + priority;
        return result;
    }
}