package com.mathsoft.cgraphicsapp;

import android.text.Editable;
import android.text.style.ForegroundColorSpan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Motor de resaltado de sintaxis mejorado con sistema de prioridades y detección de conflictos
 */
public class SyntaxHighlighter {
    
    private List<SyntaxRule> rules;
    private List<SpanInfo> appliedSpans;
    
    public SyntaxHighlighter() {
        this.rules = new ArrayList<>();
        this.appliedSpans = new ArrayList<>();
    }
    
    /**
     * Clase interna para rastrear spans aplicados
     */
    private static class SpanInfo {
        int start;
        int end;
        int priority;
        
        SpanInfo(int start, int end, int priority) {
            this.start = start;
            this.end = end;
            this.priority = priority;
        }
        
        boolean overlaps(int otherStart, int otherEnd) {
            return !(otherEnd <= start || otherStart >= end);
        }
    }
    
    /**
     * Agrega una regla de resaltado
     */
    public void addRule(SyntaxRule rule) {
        rules.add(rule);
        // Ordenar reglas por prioridad (mayor prioridad primero)
        Collections.sort(rules, new Comparator<SyntaxRule>() {
            @Override
            public int compare(SyntaxRule r1, SyntaxRule r2) {
                return Integer.compare(r2.getPriority(), r1.getPriority());
            }
        });
    }
    
    /**
     * Remueve una regla de resaltado
     */
    public void removeRule(SyntaxRule rule) {
        rules.remove(rule);
    }
    
    /**
     * Limpia todas las reglas
     */
    public void clearRules() {
        rules.clear();
    }
    
    /**
     * Obtiene todas las reglas
     */
    public List<SyntaxRule> getRules() {
        return new ArrayList<>(rules);
    }
    
    /**
     * Aplica el resaltado de sintaxis al texto
     */
    public void highlight(Editable editable) {
        if (editable == null || editable.length() == 0) {
            return;
        }
        
        // Limpiar spans existentes
        clearSpans(editable);
        
        // Limpiar lista de spans aplicados
        appliedSpans.clear();
        
        // Aplicar cada regla en orden de prioridad
        String text = editable.toString();
        
        for (SyntaxRule rule : rules) {
            applyRuleWithPriority(editable, text, rule);
        }
    }
    
    /**
     * Aplica una regla específica con control de prioridades
     */
    private void applyRuleWithPriority(Editable editable, String text, SyntaxRule rule) {
        try {
            Pattern pattern = rule.getPattern();
            Matcher matcher = pattern.matcher(text);
            
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                
                // Verificar que los índices sean válidos
                if (start >= 0 && end <= editable.length() && start < end) {
                    // Verificar si esta región ya está cubierta por un span de mayor prioridad
                    if (canApplySpan(start, end, rule.getPriority())) {
                        ForegroundColorSpan span = new ForegroundColorSpan(rule.getColor());
                        editable.setSpan(span, start, end, 
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        
                        // Registrar el span aplicado
                        appliedSpans.add(new SpanInfo(start, end, rule.getPriority()));
                    }
                }
            }
        } catch (Exception e) {
            // Ignorar errores de regex inválidos
            android.util.Log.e("SyntaxHighlighter", 
                "Error aplicando regla: " + rule.getPatternString(), e);
        }
    }
    
    /**
     * Verifica si se puede aplicar un span en la región especificada
     */
    private boolean canApplySpan(int start, int end, int priority) {
        for (SpanInfo spanInfo : appliedSpans) {
            if (spanInfo.overlaps(start, end)) {
                // Si hay overlap, solo permitir si la nueva regla tiene mayor prioridad
                if (priority <= spanInfo.priority) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Limpia todos los spans de color del texto
     */
    private void clearSpans(Editable editable) {
        // Limpiar ForegroundColorSpan
        ForegroundColorSpan[] colorSpans = editable.getSpans(
            0, 
            editable.length(), 
            ForegroundColorSpan.class
        );
        
        for (ForegroundColorSpan span : colorSpans) {
            editable.removeSpan(span);
        }
        
        // También limpiar cualquier otro tipo de span de estilo de texto
        Object[] allSpans = editable.getSpans(0, editable.length(), Object.class);
        for (Object span : allSpans) {
            if (span instanceof android.text.style.CharacterStyle && 
                !(span instanceof android.text.style.UnderlineSpan)) {
                editable.removeSpan(span);
            }
        }
    }
    
    /**
     * Aplica resaltado solo a una región específica (optimización para edición)
     */
    public void highlightRegion(Editable editable, int start, int end) {
        if (editable == null || start < 0 || end > editable.length() || start >= end) {
            return;
        }
        
        // Expandir la región para incluir la línea completa
        String text = editable.toString();
        while (start > 0 && text.charAt(start - 1) != '\n') {
            start--;
        }
        while (end < text.length() && text.charAt(end) != '\n') {
            end++;
        }
        
        // Crear variables finales para usar en lambda
        final int finalStart = start;
        final int finalEnd = end;
        
        // Limpiar spans en la región expandida
        ForegroundColorSpan[] spans = editable.getSpans(finalStart, finalEnd, ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spans) {
            editable.removeSpan(span);
        }
        
        // Limpiar spans aplicados en esta región
        appliedSpans.removeIf(spanInfo -> spanInfo.overlaps(finalStart, finalEnd));
        
        // Aplicar reglas en la región
        for (SyntaxRule rule : rules) {
            applyRuleToRegion(editable, text, rule, finalStart, finalEnd);
        }
    }
    
    /**
     * Aplica una regla a una región específica
     */
    private void applyRuleToRegion(Editable editable, String text, SyntaxRule rule, 
                                   int regionStart, int regionEnd) {
        try {
            Pattern pattern = rule.getPattern();
            Matcher matcher = pattern.matcher(text);
            
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                
                // Solo aplicar si el match está dentro o intersecta la región
                if (start < regionEnd && end > regionStart) {
                    if (start >= 0 && end <= editable.length() && start < end) {
                        if (canApplySpan(start, end, rule.getPriority())) {
                            ForegroundColorSpan span = new ForegroundColorSpan(rule.getColor());
                            editable.setSpan(span, start, end, 
                                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            appliedSpans.add(new SpanInfo(start, end, rule.getPriority()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("SyntaxHighlighter", 
                "Error aplicando regla en región: " + rule.getPatternString(), e);
        }
    }
}