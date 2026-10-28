package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Noor panel (noor_panel): a glass card in the free space right of the clock with the next prayer and a
 * countdown, the day's five prayer times with the next one in gold, and a verse or hadith of the day from the
 * theme's verses.json (the Noor app's daily cards), in Arabic and English.
 * Refreshed by Home's minute tick; nothing is fetched from the network.
 */
final class NoorPanel extends LinearLayout {

    private static final int GOLD = 0xFFFFC44D, INK = 0xFFEEF0F5, INK2 = 0x9EEEF0F5;

    private final Config cfg;
    private final float d;
    private final TextView nextName, nextTime, countdown, arabic, english, ref;
    private final TextView[] names = new TextView[5], times = new TextView[5];
    private List<JSONObject> cards = new ArrayList<>();
    private LocalDate cardDay;

    NoorPanel(Context ctx, Config cfg, File themeDir) {
        super(ctx);
        this.cfg = cfg;
        d = ctx.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setPadding(dp(22), dp(16), dp(22), dp(16));
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x2EFFFFFF, 0x12FFFFFF});
        glass.setCornerRadius(dp(22));
        glass.setStroke(Math.max(1, dp(1)), 0x40FFFFFF);
        setBackground(glass);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        TextView kicker = text(ctx, 10, INK2);
        kicker.setText("NEXT PRAYER");
        kicker.setLetterSpacing(0.18f);
        addView(kicker);
        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.BOTTOM);
        nextName = text(ctx, 26, INK);
        nextName.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        nextTime = text(ctx, 26, GOLD);
        nextTime.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        nextTime.setPadding(dp(12), 0, 0, 0);
        countdown = text(ctx, 12, INK2);
        countdown.setPadding(dp(12), 0, 0, dp(5));
        row.addView(nextName);
        row.addView(nextTime);
        row.addView(countdown);
        addView(row);

        LinearLayout grid = new LinearLayout(ctx);
        grid.setPadding(0, dp(8), 0, dp(10));
        for (int i = 0; i < 5; i++) {
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            names[i] = text(ctx, 10, INK2);
            names[i].setText(Prayer.NAMES[i].toUpperCase(Locale.ROOT));
            names[i].setLetterSpacing(0.1f);
            times[i] = text(ctx, 14, INK);
            col.addView(names[i]);
            col.addView(times[i]);
            grid.addView(col, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        addView(grid);

        View rule = new View(ctx);
        rule.setBackgroundColor(0x26FFFFFF);
        addView(rule, new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));

        arabic = text(ctx, 17, INK);
        arabic.setGravity(Gravity.RIGHT);   // not END: with RTL text direction, END is the left edge
        arabic.setTextDirection(TEXT_DIRECTION_RTL);
        arabic.setMaxLines(3);
        arabic.setEllipsize(TextUtils.TruncateAt.END);
        arabic.setLineSpacing(0, 1.15f);
        arabic.setPadding(0, dp(10), 0, dp(4));
        // The system's serif falls back to Noto Naskh Arabic. (Noor's Amiri Quran drew nothing in a TextView: its
        // tall Quranic metrics put the glyphs outside the line box.)
        arabic.setTypeface(Typeface.SERIF);
        arabic.setIncludeFontPadding(true);
        english = text(ctx, 12, INK);
        english.setMaxLines(4);
        english.setEllipsize(TextUtils.TruncateAt.END);
        english.setLineSpacing(0, 1.12f);
        ref = text(ctx, 10, GOLD);
        ref.setPadding(0, dp(4), 0, 0);
        addView(arabic);
        addView(english);
        addView(ref);

        // verses.json is ~0.3 MB: parse it off the main thread, then show the day's card.
        File cardsFile = themeDir == null ? null : new File(themeDir, "verses.json");
        if (cardsFile != null && cardsFile.isFile()) {
            new Thread(() -> {
                List<JSONObject> loaded = new ArrayList<>();
                try {
                    JSONArray all = new JSONArray(new String(Files.readAllBytes(cardsFile.toPath()), StandardCharsets.UTF_8));
                    for (int i = 0; i < all.length(); i++) {
                        JSONObject c = all.optJSONObject(i);
                        // Short ones only: a TV panel has room for about three lines of each language.
                        // Both languages, or the panel shows a gap where the Arabic goes (some hadith have none).
                        String ar = c == null ? "" : c.optString("ar").trim();
                        if (!ar.isEmpty() && ar.length() < 190 && c.optString("en").length() < 220) loaded.add(c);
                    }
                } catch (Exception ignored) {
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    cards = loaded;
                    cardDay = null;
                    update();
                });
            }, "noor-cards").start();
        }
        update();
    }

    /** Called every minute: prayer times and countdown, and a new card when the day changes. */
    void update() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalTime now = LocalTime.now(zone);
        int minute = now.getHour() * 60 + now.getMinute();
        boolean place = !Double.isNaN(cfg.prayerLat);
        int[] t = place ? Prayer.times(today, cfg.prayerLat, cfg.prayerLon, zone, cfg.prayerMethod, cfg.asrHanafi) : null;
        int next = t == null ? -1 : Prayer.next(t, minute);
        int at = t == null ? -1 : next >= 0 ? t[next]
                : Prayer.times(today.plusDays(1), cfg.prayerLat, cfg.prayerLon, zone, cfg.prayerMethod, cfg.asrHanafi)[0];
        if (t == null) {
            nextName.setText("Set your city");
            nextTime.setText("");
            countdown.setText("latitude / longitude in the theme");
        } else {
            nextName.setText(Prayer.NAMES[Math.max(0, next)]);
            nextTime.setText(clock(at));
            int left = (at - minute + 1440) % 1440;
            countdown.setText(left >= 60 ? "in " + left / 60 + "h " + left % 60 + "m" : "in " + left + "m");
        }
        for (int i = 0; i < 5; i++) {
            boolean isNext = t != null && i == Math.max(0, next);
            times[i].setText(t == null || t[i] < 0 ? "–" : clock(t[i]));
            times[i].setTextColor(isNext ? GOLD : INK);
            names[i].setTextColor(isNext ? GOLD : INK2);
        }
        if (!cards.isEmpty() && !today.equals(cardDay)) {
            cardDay = today;
            JSONObject c = cards.get((int) (today.toEpochDay() % cards.size()));
            arabic.setText(c.optString("ar"));
            english.setText(c.optString("en"));
            String src = c.optString("src");   // "Qur'an 2:4 · Surah Al-Baqarah", "Riyad as-Salihin 257 · Abu Hurairah"
            ref.setText(c.optString("ref") + (src.isEmpty() ? "" : "  ·  " + src));
        }
        arabic.setVisibility(cards.isEmpty() ? GONE : VISIBLE);
        english.setVisibility(cards.isEmpty() ? GONE : VISIBLE);
        ref.setVisibility(cards.isEmpty() ? GONE : VISIBLE);
    }

    private String clock(int minutes) {
        String pattern = android.text.format.DateFormat.is24HourFormat(getContext()) ? "H:mm" : "h:mm a";
        return LocalTime.of(minutes / 60 % 24, minutes % 60).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()));
    }

    private TextView text(Context ctx, float sp, int color) {
        TextView v = new TextView(ctx);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        return v;
    }

    private int dp(float v) {
        return Math.round(v * d);
    }
}
