package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * A liquid-glass card behind an app icon (glass_icons): a clear body that is brighter at the top, the app's own
 * colour refracted up from the bottom edge, a specular highlight across the upper half, and a rim that catches
 * the light at the top-left and fades at the bottom-right. Shaders are built once per size, not per frame.
 */
final class GlassTile extends Drawable {

    private final float radius, rimWidth;
    private final int tint;
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG),
            shine = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    GlassTile(float radius, float density, int tint) {
        this.radius = radius;
        this.rimWidth = 1.5f * density;
        this.tint = tint;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(rimWidth);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        box.set(b.left + rimWidth / 2, b.top + rimWidth / 2, b.right - rimWidth / 2, b.bottom - rimWidth / 2);
        body.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0x38FFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP));
        int c = tint & 0xFFFFFF;
        glow.setShader(new RadialGradient(b.exactCenterX(), b.bottom, b.width() * 0.75f,
                new int[]{c | 0x8C000000, c | 0x26000000, c}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        // The specular band: bright across the top, gone by the middle.
        shine.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.5f,
                new int[]{0x55FFFFFF, 0x22FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[]{0xD9FFFFFF, 0x33FFFFFF, 0x14FFFFFF, 0x80FFFFFF}, new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        // Every layer is a round rect filled with a gradient: no clipPath, which the GPU renderer rebuilds as a
        // mask per tile per frame, and that cost every animated frame behind a live background.
        canvas.drawRoundRect(box, radius, radius, body);
        canvas.drawRoundRect(box, radius, radius, glow);
        canvas.drawRoundRect(box, radius, radius, shine);
        canvas.drawRoundRect(box, radius, radius, rim);
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
