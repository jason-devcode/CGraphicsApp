package com.mathsoft.cgraphicsapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Editor de código optimizado con resaltado de sintaxis asíncrono
 */
public class CodeEditorView extends LinearLayout {
    
    private LineNumberView lineNumberView;
    private CustomEditText codeEditText;
    private ScrollView editorScrollView;
    private HorizontalScrollView lineNumberScrollView;
    
    private SyntaxHighlighter syntaxHighlighter;
    private boolean enableSyntaxHighlighting = true;
    private boolean enableLineNumbers = true;
    
    private TextWatcher syntaxWatcher;
    private Handler highlightHandler;
    private Runnable highlightRunnable;
    private ExecutorService highlightExecutor;
    
    private static final int HIGHLIGHT_DELAY_MS = 300; // Delay antes de aplicar resaltado
    
    public CodeEditorView(Context context) {
        super(context);
        init(context);
    }
    
    public CodeEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public CodeEditorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        setOrientation(HORIZONTAL);
        
        // Inicializar handler y executor para resaltado asíncrono
        highlightHandler = new Handler(Looper.getMainLooper());
        highlightExecutor = Executors.newSingleThreadExecutor();
        
        // Inicializar el resaltador de sintaxis
        syntaxHighlighter = new SyntaxHighlighter();
        setupDefaultSyntaxRules();
        
        // Crear vista de números de línea
        lineNumberView = new LineNumberView(context);
        lineNumberScrollView = new HorizontalScrollView(context);
        lineNumberScrollView.setLayoutParams(new LayoutParams(
            LayoutParams.WRAP_CONTENT, 
            LayoutParams.MATCH_PARENT
        ));
        lineNumberScrollView.addView(lineNumberView);
        lineNumberScrollView.setHorizontalScrollBarEnabled(false);
        lineNumberScrollView.setVerticalScrollBarEnabled(false);
        
        // Crear CustomEditText para el código con cursor personalizado
        codeEditText = new CustomEditText(context);
        codeEditText.setLayoutParams(new LayoutParams(
            LayoutParams.MATCH_PARENT, 
            LayoutParams.WRAP_CONTENT
        ));
        codeEditText.setPadding(16, 12, 16, 12);
        codeEditText.setTextSize(12);
        codeEditText.setTypeface(android.graphics.Typeface.MONOSPACE);
        codeEditText.setTextColor(0xFFFFFFFF);
        codeEditText.setHintTextColor(0xFF757575);
        codeEditText.setBackgroundColor(0xFF263238);
        codeEditText.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        codeEditText.setInputType(
            android.text.InputType.TYPE_CLASS_TEXT | 
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );
        codeEditText.setMinLines(20);
        codeEditText.setVerticalScrollBarEnabled(true);
        codeEditText.setHorizontallyScrolling(true);
        codeEditText.setHighlightColor(0x4D03A9F4);
        
        // Crear ScrollView para el editor
        editorScrollView = new ScrollView(context);
        editorScrollView.setLayoutParams(new LayoutParams(
            0, 
            LayoutParams.MATCH_PARENT, 
            1.0f
        ));
        editorScrollView.addView(codeEditText);
        editorScrollView.setFillViewport(true);
        
        // Agregar vistas al layout
        addView(lineNumberScrollView);
        addView(editorScrollView);
        
        // Configurar sincronización de scroll
        setupScrollSync();
        
        // Configurar resaltado de sintaxis optimizado
        setupSyntaxHighlighting();
    }
    
    private void setupDefaultSyntaxRules() {
        // PRIORIDAD 35: Strings y caracteres
        syntaxHighlighter.addRule(new SyntaxRule(
            "\"(?:[^\"\\\\]|\\\\[\\s\\S])*\"",
            0xFFCDDC39,
            false,
            35
        ));
        
        syntaxHighlighter.addRule(new SyntaxRule(
            "'(?:[^'\\\\]|\\\\[\\s\\S])'",
            0xFFCDDC39,
            false,
            35
        ));
        
        // PRIORIDAD 30: Comentarios
        syntaxHighlighter.addRule(new SyntaxRule(
            "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/",
            0xFF9E9E9E,
            false,
            30
        ));
        
        syntaxHighlighter.addRule(new SyntaxRule(
            "//[^\n]*",
            0xFF9E9E9E,
            false,
            30
        ));
        
        // PRIORIDAD 25: Directivas del preprocesador
        syntaxHighlighter.addRule(new SyntaxRule(
            "^[ \\t]*#[ \\t]*(?:include|define|undef|ifdef|ifndef|if|else|elif|endif|error|pragma|line)\\b[^\n]*",
            0xFFEC407A,
            true,
            25
        ));
        
        // PRIORIDAD 20: Palabras clave y tipos
        syntaxHighlighter.addRule(new SyntaxRule(
            "\\b(?:auto|break|case|char|const|continue|default|do|double|else|enum|extern|" +
            "float|for|goto|if|inline|int|long|register|restrict|return|short|signed|" +
            "sizeof|static|struct|switch|typedef|union|unsigned|void|volatile|while)\\b",
            0xFFFF9800,
            false,
            20
        ));
        
        syntaxHighlighter.addRule(new SyntaxRule(
            "\\b(?:GLuint|GLint|GLfloat|GLdouble|GLboolean|GLchar|GLbyte|GLubyte|GLshort|GLushort|" +
            "GLenum|GLbitfield|GLsizei|GLintptr|GLsizeiptr|GLvoid|GLclampf|GLclampd|GLsync|" +
            "GLuint64|GLint64|EGLDisplay|EGLSurface|EGLContext|EGLConfig|pthread_t)\\b",
            0xFF66BB6A,
            false,
            20
        ));
        
        syntaxHighlighter.addRule(new SyntaxRule(
            "\\b(?:bool|_Bool|_Complex|_Imaginary|size_t|ptrdiff_t|wchar_t|uint8_t|uint16_t|" +
            "uint32_t|uint64_t|int8_t|int16_t|int32_t|int64_t)\\b",
            0xFF66BB6A,
            false,
            20
        ));
        
        // PRIORIDAD 18: Constantes
        syntaxHighlighter.addRule(new SyntaxRule(
            "\\b[A-Z_][A-Z0-9_]{2,}\\b",
            0xFFFDD835,
            false,
            18
        ));
        
        // PRIORIDAD 16: Funciones
        syntaxHighlighter.addRule(new SyntaxRule(
            "\\b(?:gl|egl)[A-Z][a-zA-Z0-9_]*(?=\\s*\\()",
            0xFF26C6DA,
            false,
            16
        ));
        
        syntaxHighlighter.addRule(new SyntaxRule(
            "\\b[a-zA-Z_][a-zA-Z0-9_]*(?=\\s*\\()",
            0xFF26C6DA,
            false,
            16
        ));
        
        // PRIORIDAD 10: Números
        syntaxHighlighter.addRule(new SyntaxRule(
            "\\b0[xX][0-9a-fA-F]+[lLuU]*\\b",
            0xFF42A5F5,
            false,
            10
        ));
        
        syntaxHighlighter.addRule(new SyntaxRule(
            "\\b\\d+\\.\\d+(?:[eE][+-]?\\d+)?[fFlL]*\\b",
            0xFF42A5F5,
            false,
            10
        ));
        
        syntaxHighlighter.addRule(new SyntaxRule(
            "\\b\\d+(?:[eE][+-]?\\d+)?[fFlLuU]*\\b",
            0xFF42A5F5,
            false,
            10
        ));
        
        // PRIORIDAD 8: Operadores
        syntaxHighlighter.addRule(new SyntaxRule(
            "[+\\-*/%=<>!&|^~?:;,.]",
            0xFFFFFFFF,
            false,
            8
        ));
    }
    
    private void setupScrollSync() {
        editorScrollView.setOnScrollChangeListener(new OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                lineNumberView.scrollTo(0, scrollY);
                
                // Resaltar área visible cuando el usuario hace scroll
                if (enableSyntaxHighlighting && Math.abs(scrollY - oldScrollY) > 100) {
                    // Solo resaltar si el scroll fue significativo
                    if (highlightRunnable != null) {
                        highlightHandler.removeCallbacks(highlightRunnable);
                    }
                    
                    highlightRunnable = () -> scheduleHighlighting();
                    highlightHandler.postDelayed(highlightRunnable, 150); // Delay más corto para scroll
                }
            }
        });
    }
    
    private void setupSyntaxHighlighting() {
        syntaxWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
            
            @Override
            public void afterTextChanged(Editable s) {
                updateLineNumbers();
                
                if (enableSyntaxHighlighting) {
                    // Cancelar resaltado pendiente
                    if (highlightRunnable != null) {
                        highlightHandler.removeCallbacks(highlightRunnable);
                    }
                    
                    // Programar nuevo resaltado con delay
                    highlightRunnable = () -> scheduleHighlighting();
                    highlightHandler.postDelayed(highlightRunnable, HIGHLIGHT_DELAY_MS);
                }
            }
        };
        
        codeEditText.addTextChangedListener(syntaxWatcher);
    }
    
    private void scheduleHighlighting() {
        // Calcular región visible
        final int scrollY = editorScrollView.getScrollY();
        final int viewportHeight = editorScrollView.getHeight();
        
        // Calcular índices de inicio y fin basados en el scroll
        android.text.Layout layout = codeEditText.getLayout();
        if (layout == null) {
            // Si no hay layout aún, resaltar todo (primera vez)
            scheduleFullHighlighting();
            return;
        }
        
        // Calcular líneas visibles con margen
        final int MARGIN_LINES = 50; // Margen de 50 líneas arriba y abajo
        
        int firstVisibleLine = layout.getLineForVertical(Math.max(0, scrollY));
        int lastVisibleLine = layout.getLineForVertical(scrollY + viewportHeight);
        
        // Agregar margen
        int startLine = Math.max(0, firstVisibleLine - MARGIN_LINES);
        int endLine = Math.min(layout.getLineCount() - 1, lastVisibleLine + MARGIN_LINES);
        
        // Convertir líneas a offsets de caracteres
        final int startOffset = layout.getLineStart(startLine);
        final int endOffset = layout.getLineEnd(endLine);
        
        final String fullText = codeEditText.getText().toString();
        final String textToHighlight = fullText.substring(
            Math.max(0, startOffset), 
            Math.min(fullText.length(), endOffset)
        );
        
        final int selectionStart = codeEditText.getSelectionStart();
        final int selectionEnd = codeEditText.getSelectionEnd();
        
        // Ejecutar resaltado en thread de background
        highlightExecutor.execute(() -> {
            try {
                // Crear un Editable temporal para aplicar spans solo a la región visible
                final android.text.SpannableStringBuilder builder = 
                    new android.text.SpannableStringBuilder(textToHighlight);
                
                // Aplicar resaltado
                syntaxHighlighter.highlight(builder);
                
                // Aplicar cambios en el hilo principal
                highlightHandler.post(() -> {
                    try {
                        codeEditText.removeTextChangedListener(syntaxWatcher);
                        
                        Editable editable = codeEditText.getText();
                        
                        // Limpiar SOLO los spans de la región visible
                        Object[] spans = editable.getSpans(startOffset, endOffset, Object.class);
                        for (Object span : spans) {
                            if (span instanceof android.text.style.ForegroundColorSpan) {
                                editable.removeSpan(span);
                            }
                        }
                        
                        // Copiar spans del builder al editable en la región correcta
                        Object[] newSpans = builder.getSpans(0, builder.length(), Object.class);
                        for (Object span : newSpans) {
                            if (span instanceof android.text.style.ForegroundColorSpan) {
                                int relativeStart = builder.getSpanStart(span);
                                int relativeEnd = builder.getSpanEnd(span);
                                int flags = builder.getSpanFlags(span);
                                
                                // Convertir a offsets absolutos
                                int absoluteStart = startOffset + relativeStart;
                                int absoluteEnd = startOffset + relativeEnd;
                                
                                if (absoluteStart >= 0 && absoluteEnd <= editable.length() && absoluteStart < absoluteEnd) {
                                    android.text.style.ForegroundColorSpan newSpan = 
                                        new android.text.style.ForegroundColorSpan(
                                            ((android.text.style.ForegroundColorSpan)span).getForegroundColor()
                                        );
                                    editable.setSpan(newSpan, absoluteStart, absoluteEnd, flags);
                                }
                            }
                        }
                        
                        // Restaurar selección
                        if (selectionStart >= 0 && selectionEnd >= 0 && 
                            selectionStart <= editable.length() && selectionEnd <= editable.length()) {
                            codeEditText.setSelection(selectionStart, selectionEnd);
                        }
                        
                        codeEditText.addTextChangedListener(syntaxWatcher);
                    } catch (Exception e) {
                        android.util.Log.e("CodeEditorView", "Error aplicando resaltado", e);
                        codeEditText.addTextChangedListener(syntaxWatcher);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("CodeEditorView", "Error en resaltado asíncrono", e);
            }
        });
    }
    
    /**
     * Resaltado completo (usado solo la primera vez)
     */
    private void scheduleFullHighlighting() {
        final String textToHighlight = codeEditText.getText().toString();
        final int selectionStart = codeEditText.getSelectionStart();
        final int selectionEnd = codeEditText.getSelectionEnd();
        
        highlightExecutor.execute(() -> {
            try {
                final android.text.SpannableStringBuilder builder = 
                    new android.text.SpannableStringBuilder(textToHighlight);
                
                syntaxHighlighter.highlight(builder);
                
                highlightHandler.post(() -> {
                    try {
                        codeEditText.removeTextChangedListener(syntaxWatcher);
                        
                        Editable editable = codeEditText.getText();
                        
                        // Limpiar spans existentes
                        Object[] spans = editable.getSpans(0, editable.length(), Object.class);
                        for (Object span : spans) {
                            if (span instanceof android.text.style.ForegroundColorSpan) {
                                editable.removeSpan(span);
                            }
                        }
                        
                        // Copiar spans
                        Object[] newSpans = builder.getSpans(0, builder.length(), Object.class);
                        for (Object span : newSpans) {
                            if (span instanceof android.text.style.ForegroundColorSpan) {
                                int start = builder.getSpanStart(span);
                                int end = builder.getSpanEnd(span);
                                int flags = builder.getSpanFlags(span);
                                
                                if (start >= 0 && end <= editable.length() && start < end) {
                                    android.text.style.ForegroundColorSpan newSpan = 
                                        new android.text.style.ForegroundColorSpan(
                                            ((android.text.style.ForegroundColorSpan)span).getForegroundColor()
                                        );
                                    editable.setSpan(newSpan, start, end, flags);
                                }
                            }
                        }
                        
                        if (selectionStart >= 0 && selectionEnd >= 0 && 
                            selectionStart <= editable.length() && selectionEnd <= editable.length()) {
                            codeEditText.setSelection(selectionStart, selectionEnd);
                        }
                        
                        codeEditText.addTextChangedListener(syntaxWatcher);
                    } catch (Exception e) {
                        android.util.Log.e("CodeEditorView", "Error aplicando resaltado completo", e);
                        codeEditText.addTextChangedListener(syntaxWatcher);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("CodeEditorView", "Error en resaltado completo", e);
            }
        });
    }
    
    private void updateLineNumbers() {
        String text = codeEditText.getText().toString();
        int lineCount = text.isEmpty() ? 1 : text.split("\n", -1).length;
        lineNumberView.setLineCount(lineCount);
    }
    
    // Métodos públicos
    
    public void setText(String text) {
        codeEditText.setText(text);
        updateLineNumbers();
        
        if (enableSyntaxHighlighting && text != null && !text.isEmpty()) {
            postDelayed(() -> scheduleHighlighting(), 100);
        }
    }
    
    public String getText() {
        return codeEditText.getText().toString();
    }
    
    public Editable getEditableText() {
        return codeEditText.getText();
    }
    
    public void setHint(String hint) {
        codeEditText.setHint(hint);
    }
    
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        codeEditText.setEnabled(enabled);
    }
    
    public void setSyntaxHighlightingEnabled(boolean enabled) {
        this.enableSyntaxHighlighting = enabled;
        if (!enabled) {
            Editable text = codeEditText.getText();
            Object[] spans = text.getSpans(0, text.length(), Object.class);
            for (Object span : spans) {
                if (span instanceof android.text.style.ForegroundColorSpan) {
                    text.removeSpan(span);
                }
            }
        }
    }
    
    public void refreshSyntaxHighlighting() {
        if (enableSyntaxHighlighting) {
            scheduleHighlighting();
        }
    }
    
    public void setLineNumbersEnabled(boolean enabled) {
        this.enableLineNumbers = enabled;
        lineNumberScrollView.setVisibility(enabled ? VISIBLE : GONE);
    }
    
    public SyntaxHighlighter getSyntaxHighlighter() {
        return syntaxHighlighter;
    }
    
    public void addSyntaxRule(String pattern, int color) {
        syntaxHighlighter.addRule(new SyntaxRule(pattern, color, false, 10));
    }
    
    public void addSyntaxRule(String pattern, int color, boolean multiline) {
        syntaxHighlighter.addRule(new SyntaxRule(pattern, color, multiline, 10));
    }
    
    public void addSyntaxRule(String pattern, int color, boolean multiline, int priority) {
        syntaxHighlighter.addRule(new SyntaxRule(pattern, color, multiline, priority));
    }
    
    public void clearSyntaxRules() {
        syntaxHighlighter.clearRules();
    }
    
    public EditText getEditText() {
        return codeEditText;
    }
    
    public void setTextSize(float size) {
        codeEditText.setTextSize(size);
        lineNumberView.setTextSize(size);
        codeEditText.setCursorHeight(size);
    }
    
    public void setTextColor(int color) {
        codeEditText.setTextColor(color);
    }
    
    public void setBackgroundColor(int color) {
        codeEditText.setBackgroundColor(color);
        lineNumberView.setBackgroundColor(darkenColor(color));
    }
    
    private int darkenColor(int color) {
        float factor = 0.85f;
        int a = android.graphics.Color.alpha(color);
        int r = Math.round(android.graphics.Color.red(color) * factor);
        int g = Math.round(android.graphics.Color.green(color) * factor);
        int b = Math.round(android.graphics.Color.blue(color) * factor);
        return android.graphics.Color.argb(a, r, g, b);
    }
    
    public void cleanup() {
        if (highlightExecutor != null && !highlightExecutor.isShutdown()) {
            highlightExecutor.shutdown();
        }
        if (highlightHandler != null && highlightRunnable != null) {
            highlightHandler.removeCallbacks(highlightRunnable);
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cleanup();
    }
    
    /**
     * EditText personalizado con cursor visible
     */
    private class CustomEditText extends EditText {
        private Paint cursorPaint;
        private Runnable cursorBlink;
        private boolean cursorVisible = true;
        
        public CustomEditText(Context context) {
            super(context);
            init();
        }
        
        private void init() {
            cursorPaint = new Paint();
            cursorPaint.setColor(0xFFFFFFFF);
            cursorPaint.setStrokeWidth(2);
            cursorPaint.setStyle(Paint.Style.FILL);
            
            cursorBlink = new Runnable() {
                @Override
                public void run() {
                    cursorVisible = !cursorVisible;
                    invalidate();
                    if (isFocused()) {
                        postDelayed(this, 500);
                    }
                }
            };
        }
        
        public void setCursorHeight(float textSize) {
            // No usado pero mantenido por compatibilidad
        }
        
        @Override
        protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
            super.onFocusChanged(focused, direction, previouslyFocusedRect);
            if (focused) {
                cursorVisible = true;
                removeCallbacks(cursorBlink);
                postDelayed(cursorBlink, 500);
            } else {
                removeCallbacks(cursorBlink);
            }
        }
        
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            
            if (isFocused() && cursorVisible && getSelectionStart() == getSelectionEnd()) {
                int cursorPos = getSelectionStart();
                if (cursorPos >= 0) {
                    try {
                        android.text.Layout layout = getLayout();
                        if (layout != null) {
                            int line = layout.getLineForOffset(cursorPos);
                            float x = layout.getPrimaryHorizontal(cursorPos);
                            float y = layout.getLineTop(line);
                            float bottom = layout.getLineBottom(line);
                            
                            canvas.drawRect(
                                x + getPaddingLeft() - getScrollX(),
                                y + getPaddingTop() - getScrollY(),
                                x + getPaddingLeft() - getScrollX() + 2,
                                bottom + getPaddingTop() - getScrollY(),
                                cursorPaint
                            );
                        }
                    } catch (Exception e) {
                        // Ignorar
                    }
                }
            }
        }
        
        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeCallbacks(cursorBlink);
        }
    }
    
    /**
     * Vista de números de línea
     */
    private class LineNumberView extends View {
        private Paint textPaint;
        private Paint backgroundPaint;
        private int lineCount = 1;
        private float textSize = 12;
        private Rect bounds = new Rect();
        
        public LineNumberView(Context context) {
            super(context);
            init();
        }
        
        private void init() {
            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(0xFF90A4AE);
            textPaint.setTextSize(textSize * getResources().getDisplayMetrics().scaledDensity);
            textPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
            
            backgroundPaint = new Paint();
            backgroundPaint.setColor(0xFF1E2428);
            backgroundPaint.setStyle(Paint.Style.FILL);
            
            setWillNotDraw(false);
            setPadding(16, 12, 16, 12);
        }
        
        public void setLineCount(int count) {
            if (this.lineCount != count) {
                this.lineCount = count;
                requestLayout();
                invalidate();
            }
        }
        
        public void setTextSize(float size) {
            this.textSize = size;
            textPaint.setTextSize(size * getResources().getDisplayMetrics().scaledDensity);
            requestLayout();
            invalidate();
        }
        
        @Override
        public void setBackgroundColor(int color) {
            backgroundPaint.setColor(color);
            invalidate();
        }
        
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            String maxLineNumber = String.valueOf(lineCount);
            textPaint.getTextBounds(maxLineNumber, 0, maxLineNumber.length(), bounds);
            
            int width = bounds.width() + getPaddingLeft() + getPaddingRight();
            int height = MeasureSpec.getSize(heightMeasureSpec);
            
            setMeasuredDimension(width, height);
        }
        
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            
            canvas.drawRect(0, 0, getWidth(), getHeight() * 1000, backgroundPaint);
            
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float lineHeight = metrics.descent - metrics.ascent;
            
            for (int i = 1; i <= lineCount; i++) {
                String lineNumber = String.valueOf(i);
                float y = getPaddingTop() + (i * lineHeight) - metrics.descent;
                canvas.drawText(lineNumber, getPaddingLeft(), y, textPaint);
            }
        }
    }
}