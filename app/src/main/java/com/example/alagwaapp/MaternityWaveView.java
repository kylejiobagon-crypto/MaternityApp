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
    
    private float progress = 0.6f; // 0.0 to 1.0 (e.g. 24/40 weeks)
    private int waveCount = 5;
    private float waveHeight = 15f;
    
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

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(12f);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        beaconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        beaconPaint.setStyle(Paint.Style.FILL);
        beaconPaint.setShadowLayer(15f, 0, 0, 0xFFFF3C8E);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // For shadow

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
        float centerY = h / 2f;
        
        // Define 2-Stop Gradient: Ruby Red (Start) -> Rose (End)
        int[] colors = { 0xFFE11D48, 0xFFFB7185 };
        float[] positions = { 0f, 1f };
        
        LinearGradient gradient = new LinearGradient(0, centerY, w, centerY,
                colors, positions, Shader.TileMode.CLAMP);
        progressPaint.setShader(gradient);
        
        // Background Path (Dotted or Faint)
        wavePaint.setColor(0x1A64748B);
        drawWave(canvas, wavePaint, 0, 1.0f);
        
        // Progress Path
        drawWave(canvas, progressPaint, 0, progress);
        
        // Beacon (Glowing marker at current progress)
        float beaconX = w * progress;
        float beaconY = calculateWaveY(beaconX, w, h);
        
        beaconPaint.setColor(0xFFFF0000); // Pure Red
        beaconPaint.setShadowLayer(35f, 0, 0, 0xFFFF0000);
        canvas.drawCircle(beaconX, beaconY, 14f, beaconPaint);
        
        beaconPaint.setColor(0xFFFFFFFF);
        beaconPaint.setShadowLayer(0, 0, 0, 0);
        canvas.drawCircle(beaconX, beaconY, 6f, beaconPaint);
    }
    
    private void drawWave(Canvas canvas, Paint paint, float start, float end) {
        float w = getWidth();
        float h = getHeight();
        float centerY = h / 2f;
        
        wavePath.reset();
        
        float currentX = w * start;
        wavePath.moveTo(currentX, calculateWaveY(currentX, w, h));
        
        int steps = 100;
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            if (t > end) break;
            
            float x = w * t;
            float y = calculateWaveY(x, w, h);
            wavePath.lineTo(x, y);
        }
        
        canvas.drawPath(wavePath, paint);
    }
    
    private float calculateWaveY(float x, float w, float h) {
        float centerY = h / 2f;
        // Bezier-like wave using Sine
        return centerY + (float) Math.sin((x / w) * Math.PI * 3) * 30f;
    }
}
