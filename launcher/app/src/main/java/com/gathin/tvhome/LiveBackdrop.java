package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.time.LocalTime;
import java.util.Random;

/**
 * An animated background drawn on the TV (live_background): "noor" (the Noor app's drifting colour blobs, twinkling
 * stars, a glowing crescent and the odd shooting star), "aurora" (flowing ribbons of light) or "sky" (a sky that
 * follows the time of day, with stars at night and drifting clouds).
 *
 * It is a SurfaceView drawn from its own thread at 20 frames a second: the compositor blends it under Home, so
 * Home's own window is not redrawn for it. As an ordinary View every frame re-issued the whole Home screen, about
 * 30 ms of render-thread time a frame on a Chromecast with Google TV, and D-pad moves lagged. The surface exists only
 * while Home is on screen, so the thread stops with it. With animations off it draws one still frame.
 */
final class LiveBackdrop extends SurfaceView implements SurfaceHolder.Callback {

    private static final long FRAME_MS = 50;
    private static final int STARS = 80;

    private final String style;
    private final boolean animate;
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] starX = new float[STARS], starY = new float[STARS], starR = new float[STARS],
            starPhase = new float[STARS], starSpeed = new float[STARS];
    private final long start = SystemClock.uptimeMillis();
    private volatile Thread renderer;

    LiveBackdrop(Context context, String style, boolean animate, boolean monochrome) {
        super(context);
        this.style = style;
        this.animate = animate;
        density = context.getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        getHolder().addCallback(this);
        if (monochrome) {
            // Home's black-and-white filter is a layer on its window; this surface is outside it, so grey it here.
            ColorMatrix grey = new ColorMatrix();
            grey.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(grey);
            paint.setColorFilter(filter);
            softPaint.setColorFilter(filter);
        }
        Random r = new Random(7);
        for (int i = 0; i < STARS; i++) {
            starX[i] = r.nextFloat();
            starY[i] = r.nextFloat() * ("sky".equals(style) ? 0.6f : 0.75f);
            starR[i] = (0.5f + r.nextFloat() * 1.1f) * density;
            starPhase[i] = r.nextFloat() * 6.28f;
            starSpeed[i] = 0.4f + r.nextFloat() * 1.2f;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Thread t = new Thread(() -> {
            while (renderer == Thread.currentThread()) {
                long began = SystemClock.uptimeMillis();
                Canvas c = null;
                try {
                    c = holder.lockHardwareCanvas();
                    if (c != null) render(c);
                } catch (RuntimeException ignored) {
                    // The surface went away between the check and the lock; surfaceDestroyed stops the loop.
                } finally {
                    if (c != null) {
                        try {
                            holder.unlockCanvasAndPost(c);
                        } catch (RuntimeException ignored) {
                        }
                    }
                }
                if (!animate) break;
                SystemClock.sleep(Math.max(1, FRAME_MS - (SystemClock.uptimeMillis() - began)));
            }
        }, "live-backdrop");
        t.setPriority(Thread.MIN_PRIORITY);
        renderer = t;
        t.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Thread t = renderer;
        renderer = null;
        if (t != null) {
            try {
                t.join(500);   // the surface must not be drawn after this returns
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * The soft layers (blobs, ribbons, sky, clouds) are drawn into a bitmap a twelfth of the screen's size and
     * stretched with filtering: they are blurry by design, so they look the same, for a fraction of the cost.
     * Stars, the moon and the meteor stay crisp at full size.
     */
    private static final int SOFT_SCALE = 12;
    private int frame;
    private Bitmap soft;
    private Canvas softCanvas;
    private final Paint softPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect screen = new Rect();

    private void render(Canvas c) {
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        int w = c.getWidth(), h = c.getHeight();
        if (w <= 0 || h <= 0) return;
        boolean fresh = soft == null || soft.getWidth() != Math.max(1, w / SOFT_SCALE);
        if (fresh) {
            soft = Bitmap.createBitmap(Math.max(1, w / SOFT_SCALE), Math.max(1, h / SOFT_SCALE), Bitmap.Config.ARGB_8888);
            softCanvas = new Canvas(soft);
        }
        // The soft layer drifts slowly: every other frame (10 a second) is enough.
        if (fresh || frame++ % 2 == 0) {
            softCanvas.save();
            softCanvas.scale(1f / SOFT_SCALE, 1f / SOFT_SCALE);
            switch (style) {
                case "aurora": drawAurora(softCanvas, t, w, h); break;
                case "sky": drawSky(softCanvas, t, w, h); break;
                default: drawNoor(softCanvas, t, w, h); break;
            }
            softCanvas.restore();
        }
        screen.set(0, 0, w, h);
        c.drawBitmap(soft, null, screen, softPaint);
        switch (style) {
            case "aurora": drawStars(c, t, w, h, 0.8f); break;
            case "sky": drawSkyFront(c, t, w, h); break;
            default: drawNoorFront(c, t, w, h); break;
        }
    }

    // ---- noor ------------------------------------------------------------------------------------

    private static final int[] NOOR_BLOBS = {0xFF0F8F86, 0xFF3F46B8, 0xFF14506E, 0xFF2B2F7A, 0xFF6B4A2E};

    private void drawNoor(Canvas c, float t, int w, int h) {
        c.drawColor(0xFF060A17);
        // Five soft blobs on slow Lissajous paths, as Noor's .blob layers drift behind its pages.
        float[][] base = {{0.15f, 0.15f}, {0.85f, 0.25f}, {0.18f, 0.9f}, {0.6f, 0.65f}, {0.92f, 0.95f}};
        for (int i = 0; i < NOOR_BLOBS.length; i++) {
            float cx = w * (base[i][0] + 0.12f * (float) Math.sin(t * (0.05f + 0.013f * i) + i * 1.7f));
            float cy = h * (base[i][1] + 0.10f * (float) Math.cos(t * (0.04f + 0.011f * i) + i * 2.3f));
            float rad = Math.max(w, h) * (0.42f + 0.05f * (float) Math.sin(t * 0.07f + i));
            blob(c, cx, cy, rad, NOOR_BLOBS[i], i == 4 ? 0.45f : 0.6f);
        }
        // The crescent's halo is soft too, so it belongs to this layer.
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.6f);
        blob(c, w * 0.43f, h * 0.4f, h * 0.22f, 0xFFFFF0D2, 0.10f + 0.06f * pulse);
    }

    private void drawNoorFront(Canvas c, float t, int w, int h) {
        drawStars(c, t, w, h, 1f);
        // Crescent and eight-point star, as in Noor's icon, breathing gently.
        float mx = w * 0.43f, my = h * 0.4f, mr = h * 0.055f;   // the free space between the clock and the dock
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.6f);
        path.reset();
        path.addCircle(mx, my, mr, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(mx + mr * 0.45f, my - mr * 0.12f, mr * 0.88f, Path.Direction.CW);
        path.op(cut, Path.Op.DIFFERENCE);
        paint.setShader(null);
        paint.setColor(0xFFFFF4DE);
        paint.setAlpha(220);
        c.drawPath(path, paint);
        drawSparkle(c, mx + mr * 1.6f, my - mr * 0.5f, mr * 0.32f * (0.85f + 0.3f * pulse), 0xFFFFC44D);
        shootingStar(c, t, w, h);
    }

    private void blob(Canvas c, float cx, float cy, float r, int color, float alpha) {
        int a = Math.round(255 * alpha);
        paint.setShader(new RadialGradient(cx, cy, r,
                new int[]{(color & 0xFFFFFF) | (a << 24), (color & 0xFFFFFF) | ((a / 3) << 24), color & 0xFFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, paint);
        paint.setShader(null);
    }

    private void drawStars(Canvas c, float t, int w, int h, float strength) {
        paint.setShader(null);
        for (int i = 0; i < STARS; i++) {
            float s = 0.5f + 0.5f * (float) Math.sin(t * starSpeed[i] + starPhase[i]);
            paint.setColor(Color.WHITE);
            paint.setAlpha(Math.round(255 * strength * (0.12f + 0.75f * s * s)));
            c.drawCircle(starX[i] * w, starY[i] * h, starR[i] * (0.7f + 0.4f * s), paint);
        }
    }

    private void drawSparkle(Canvas c, float x, float y, float r, int color) {
        path.reset();
        for (int i = 0; i < 16; i++) {
            double a = Math.PI / 8 * i - Math.PI / 2;
            float rr = i % 2 == 0 ? r : r * 0.38f;
            float px = x + (float) Math.cos(a) * rr, py = y + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        paint.setShader(null);
        paint.setColor(color);
        c.drawPath(path, paint);
    }

    /** One meteor every 23 s, crossing in 1.3 s, from a different spot each time. */
    private void shootingStar(Canvas c, float t, int w, int h) {
        float cycle = 23f, k = t % cycle;
        if (k > 1.3f) return;
        int n = (int) (t / cycle);
        float p = k / 1.3f;
        float sx = w * (0.15f + 0.6f * ((n * 0.37f) % 1f)), sy = h * (0.05f + 0.15f * ((n * 0.53f) % 1f));
        float x = sx + w * 0.22f * p, y = sy + h * 0.12f * p;
        float tail = w * 0.08f;
        float a = (float) Math.sin(Math.PI * p);
        paint.setStrokeWidth(1.6f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setShader(new LinearGradient(x, y, x - tail, y - tail * 0.55f,
                Color.argb(Math.round(230 * a), 255, 255, 255), 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawLine(x, y, x - tail, y - tail * 0.55f, paint);
        paint.setShader(null);
    }

    // ---- aurora ----------------------------------------------------------------------------------

    private static final int[] AURORA = {0xFF19D3A2, 0xFF7B5CFF, 0xFF2FA7FF};

    private void drawAurora(Canvas c, float t, int w, int h) {
        paint.setShader(new LinearGradient(0, 0, 0, h, 0xFF02060F, 0xFF0A1630, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        for (int i = 0; i < AURORA.length; i++) {
            float baseY = h * (0.22f + 0.13f * i), amp = h * (0.06f + 0.02f * i);
            float band = h * (0.16f + 0.04f * (float) Math.sin(t * 0.2f + i));
            path.reset();
            int steps = 48;
            for (int s = 0; s <= steps; s++) {
                float x = w * s / (float) steps;
                float y = baseY + amp * (float) Math.sin(x / w * 6.28f * (1.2f + 0.3f * i) + t * (0.25f + 0.08f * i) + i * 2f)
                        + amp * 0.4f * (float) Math.sin(x / w * 17f - t * 0.4f);
                if (s == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            for (int s = steps; s >= 0; s--) {
                float x = w * s / (float) steps;
                float y = baseY + band + amp * 0.5f * (float) Math.sin(x / w * 6.28f * (1.2f + 0.3f * i) + t * (0.25f + 0.08f * i) + i * 2f + 0.6f);
                path.lineTo(x, y);
            }
            path.close();
            float shimmer = 0.55f + 0.45f * (float) Math.sin(t * 0.5f + i * 1.3f);
            int col = AURORA[i] & 0xFFFFFF;
            paint.setShader(new LinearGradient(0, baseY - amp, 0, baseY + band + amp,
                    new int[]{col, col | (Math.round(215 * shimmer) << 24), col | (Math.round(90 * shimmer) << 24), col},
                    new float[]{0f, 0.25f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            c.drawPath(path, paint);
        }
        paint.setShader(null);
    }

    // ---- sky -------------------------------------------------------------------------------------

    /** Top and bottom sky colours at 0, 5, 7, 12, 17, 19 and 21 o'clock; Noor's Live sky palette at the ends of day. */
    private static final float[] SKY_HOURS = {0, 5, 7, 12, 17, 19, 21, 24};
    private static final int[][] SKY = {
            {0xFF060A1E, 0xFF1D1F52}, {0xFF0B1240, 0xFF3A2F6B}, {0xFF1C2A6B, 0xFFE98C6A},
            {0xFF2A6FD6, 0xFF8CC8FF}, {0xFF3C6CC4, 0xFFF6CF8E}, {0xFF3A2F6B, 0xFFE0845A},
            {0xFF0B1240, 0xFF2C2560}, {0xFF060A1E, 0xFF1D1F52}};

    private void drawSky(Canvas c, float t, int w, int h) {
        LocalTime now = LocalTime.now();
        float hour = now.getHour() + now.getMinute() / 60f + now.getSecond() / 3600f;
        int i = 0;
        while (i < SKY_HOURS.length - 2 && hour >= SKY_HOURS[i + 1]) i++;
        float f = (hour - SKY_HOURS[i]) / (SKY_HOURS[i + 1] - SKY_HOURS[i]);
        int top = mix(SKY[i][0], SKY[i + 1][0], f), bottom = mix(SKY[i][1], SKY[i + 1][1], f);
        paint.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        boolean day = isDay(hour);
        float[] body = skyBody(hour, w, h);
        blob(c, body[0], body[1], h * 0.22f, day ? 0xFFFFE6A8 : 0xFFE8EEFF, day ? 0.55f : 0.25f);
        // Three soft clouds drifting right, wrapping around.
        for (int k = 0; k < 3; k++) {
            float cx = ((t * (6f + 3f * k) * density + k * w * 0.41f) % (w * 1.4f)) - w * 0.2f;
            float cy = h * (0.18f + 0.12f * k);
            blob(c, cx, cy, h * (0.16f + 0.04f * k), day ? 0xFFFFFFFF : 0xFF8A93C8, day ? 0.28f : 0.12f);
            blob(c, cx + h * 0.12f, cy + h * 0.02f, h * 0.12f, day ? 0xFFFFFFFF : 0xFF8A93C8, day ? 0.22f : 0.1f);
        }
    }

    /** Stars that fade in after sunset, and the sun or moon disk, drawn crisp over the soft sky. */
    private void drawSkyFront(Canvas c, float t, int w, int h) {
        LocalTime now = LocalTime.now();
        float hour = now.getHour() + now.getMinute() / 60f;
        boolean day = isDay(hour);
        float night = day ? 0f : (hour >= 19f ? Math.min(1f, (hour - 19f) / 1.5f) : Math.min(1f, (6.5f - hour) / 1.5f));
        if (night > 0) drawStars(c, t, w, h, night);
        float[] body = skyBody(hour, w, h);
        paint.setShader(null);
        paint.setColor(day ? 0xFFFFF4D6 : 0xFFF3F1E8);
        c.drawCircle(body[0], body[1], h * (day ? 0.045f : 0.035f), paint);
    }

    private static boolean isDay(float hour) {
        return hour >= 6.5f && hour < 19f;
    }

    /** The sun by day or the moon by night, on an arc across the sky: {x, y}. */
    private static float[] skyBody(float hour, int w, int h) {
        float p = isDay(hour) ? (hour - 6.5f) / 12.5f : ((hour + 5f) % 24f) / 11.5f;
        p = Math.min(1f, Math.max(0f, p));
        return new float[]{w * (0.1f + 0.8f * p), h * (0.62f - 0.48f * (float) Math.sin(Math.PI * p))};
    }

    private static int mix(int a, int b, float f) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }
}
