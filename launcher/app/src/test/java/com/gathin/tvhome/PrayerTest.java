package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Prayer-time maths against published Umm al-Qura times for Makkah, and the next-prayer pick. */
public class PrayerTest {

    private static void near(String name, int expected, int actual) {
        assertTrue(name + " " + actual / 60 + ":" + actual % 60, Math.abs(expected - actual) <= 4);
    }

    @Test
    public void makkahMidsummerMatchesUmmAlQura() {
        int[] t = Prayer.times(LocalDate.of(2026, 6, 21), 21.4225, 39.8262, ZoneId.of("Asia/Riyadh"), "makkah", false);
        near("fajr", 4 * 60 + 11, t[0]);
        near("dhuhr", 12 * 60 + 22, t[1]);
        near("asr", 15 * 60 + 41, t[2]);
        near("maghrib", 19 * 60 + 6, t[3]);
        assertEquals(t[3] + 90, t[4]);
    }

    @Test
    public void hanafiAsrIsLater() {
        LocalDate d = LocalDate.of(2026, 3, 1);
        ZoneId z = ZoneId.of("America/New_York");
        assertTrue(Prayer.times(d, 40.71, -74.0, z, "isna", true)[2] > Prayer.times(d, 40.71, -74.0, z, "isna", false)[2] + 30);
    }

    @Test
    public void nextPrayerWrapsToTomorrowsFajr() {
        int[] t = {300, 720, 930, 1100, 1200};
        assertEquals(0, Prayer.next(t, 100));
        assertEquals(2, Prayer.next(t, 720));
        assertEquals(-1, Prayer.next(t, 1300));
    }
}
