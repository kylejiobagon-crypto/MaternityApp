package com.example.alagwaapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class MaternityWaveView extends View {

    private Paint wavePaint;
    private Paint progressPaint;
    private Paint beaconPaint;
    private Path wavePath;
    private Paint glowPaint;
    private Paint fillPaint;
    private Paint specularPaint;
    private Paint targetPaint; // For birth point
    
    private float progress = 0.6f; // 0.0 to 1.0 (e.g. 24/40 weeks)
    
    public MaternityWaveView(Context context) {
        super(context);
        init();
    }

    public MaternityWaveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(12f);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setColor(0x18FFFFFF); // Faint background path

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(16f);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(35f);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setAlpha(55);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        specularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        specularPaint.setStyle(Paint.Style.STROKE);
        specularPaint.setStrokeWidth(3f);
        specularPaint.setStrokeCap(Paint.Cap.ROUND);
        specularPaint.setColor(0x90FFFFFF);

        beaconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        beaconPaint.setStyle(Paint.Style.FILL);

        targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetPaint.setStyle(Paint.Style.STROKE);
        targetPaint.setStrokeWidth(4f);
        targetPaint.setColor(0xFFFFFFFF);
        
        setLayerType(LAYER_TYPE_SOFTWARE, null); 

        wavePath = new Path();
    }

    public void setProgress(float progress) {
        this.progress = Math.min(1.0f, Math.max(0.0f, progress));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        float w = getWidth();
        float h = getHeight();
        float centerY = h / 1.25f;
        
        // --- 1. DEFINE GRADIENTS ---
        int[] progressColors = { 0xFFFF1F7D, 0xFFFF70A6 };
        LinearGradient mainGrad = new LinearGradient(0, centerY, w, centerY, progressColors, null, Shader.TileMode.CLAMP);
        progressPaint.setShader(mainGrad);
        
        // --- 2. DRAW THE ENTIRE BACKGROUND WAVE ---
        drawWavePath(canvas, wavePaint, 0, 1.0f, false);
        
        // --- 3. DRAW BIRTH POINT (Target at 100%) ---
        float targetX = w * 0.98f;
        float targetY = calculateWaveY(targetX, w, h);
        targetPaint.setShadowLayer(40f, 0, 0, 0xFF00F2FF); // Electric Cyan Target
        canvas.drawCircle(targetX, targetY, 15f, targetPaint);
        
        targetPaint.setStyle(Paint.Style.FILL);
        targetPaint.setColor(0x4000F2FF);
        canvas.drawCircle(targetX, targetY, 8f, targetPaint);
        targetPaint.setStyle(Paint.Style.STROKE);
        targetPaint.setColor(0xFFFFFFFF);

        // --- 4. DRAW 3D VOLUME FALLOFF (Better than vertical cut) ---
        // We draw the fill under the whole wave but mask its alpha by progress
        int[] fillColors = { 0x30FF1F7D, 0x00FF1F7D };
        LinearGradient fillGrad = new LinearGradient(0, centerY - 40, 0, h, fillColors, null, Shader.TileMode.CLAMP);
        fillPaint.setShader(fillGrad);
        drawWavePath(canvas, fillPaint, 0, progress, true);
        
        // --- 5. DRAW NEON GLOW ---
        glowPaint.setShader(mainGrad);
        glowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(20f, android.graphics.BlurMaskFilter.Blur.NORMAL));
        drawWavePath(canvas, glowPaint, 0, progress, false);

        // --- 6. MAIN PROGRESS PATH ---
        drawWavePath(canvas, progressPaint, 0, progress, false);
        
        // --- 7. BEACON (Current Week) ---
        float beaconX = w * progress;
        float beaconY = calculateWaveY(beaconX, w, h);
        
        beaconPaint.setColor(0xFFFF1F7D);
        beaconPaint.setAlpha(255);
        beaconPaint.setShadowLayer(35f, 0, 0, 0xFFFF1F7D);
        canvas.drawCircle(beaconX, beaconY, 14f, beaconPaint);
        
        beaconPaint.setColor(0xFFFFFFFF);
        beaconPaint.setShadowLayer(0, 0, 0, 0);
        canvas.drawCircle(beaconX, beaconY, 6f, beaconPaint);
    }
    
    private void drawWavePath(Canvas canvas, Paint paint, float start, float end, boolean isFill) {
        float w = getWidth();
        float h = getHeight();
        
        wavePath.reset();
        float startY = calculateWaveY(w * start, w, h);
        wavePath.moveTo(w * start, startY);
        
        int steps = 120;
        for (int i = 1; i <= steps; i++) {
            float t = start + (end - start) * (float) i / steps;
            float x = w * t;
            float y = calculateWaveY(x, w, h);
            wavePath.lineTo(x, y);
        }
        
        if (isFill) {
            wavePath.lineTo(w * end, h);
            wavePath.lineTo(w * start, h);
            wavePath.close();
            canvas.drawPath(wavePath, paint);
        } else {
            canvas.drawPath(wavePath, paint);
        }
    }
    
    private float calculateWaveY(float x, float w, float h) {
        float centerY = h / 1.25f; // Deep shift
        return centerY + (float) Math.sin((x / w) * Math.PI * 2.5) * 28f 
                    + (float) Math.cos((x / w) * Math.PI * 6.0) * 6f;
    }
}
