package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * The five daily prayer times for a place, computed offline from the sun's position with the same NOAA
 * equations as {@link Sun} (accurate to a minute or two). Pure maths, no Android types, so it runs in JVM
 * tests; no network and no location permission, the place comes from config.xml or the theme.
 * Methods: isna (15°/15°, the Noor app's default), mwl (18°/17°), egypt (19.5°/17.5°), karachi (18°/18°),
 * makkah (18.5°, Isha 90 minutes after Maghrib). Asr: standard (shadow length 1) or hanafi (2).
 */
final class Prayer {

    static final String[] NAMES = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};

    private Prayer() {
    }

    /**
     * Local minutes of the day of Fajr, Dhuhr, Asr, Maghrib and Isha; an entry is -1 when the sun never
     * reaches that angle that day (high latitudes in summer).
     */
    static int[] times(LocalDate date, double latitude, double longitude, ZoneId zone, String method, boolean hanafi) {
        double g = 2 * Math.PI / 365 * (date.getDayOfYear() - 1);
        double eqTime = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g)
                - 0.006758 * Math.cos(2 * g) + 0.000907 * Math.sin(2 * g)
                - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double lat = Math.toRadians(latitude);
        int offset = zone.getRules().getOffset(date.atTime(12, 0)).getTotalSeconds() / 60;
        double noon = 720 - 4 * longitude - eqTime + offset;

        double fajrAngle, ishaAngle;
        int ishaAfterMaghrib = 0;
        switch (method == null ? "" : method.trim().toLowerCase(Locale.ROOT)) {
            case "mwl": fajrAngle = 18; ishaAngle = 17; break;
            case "egypt": fajrAngle = 19.5; ishaAngle = 17.5; break;
            case "karachi": fajrAngle = 18; ishaAngle = 18; break;
            case "makkah": fajrAngle = 18.5; ishaAngle = 0; ishaAfterMaghrib = 90; break;
            default: fajrAngle = 15; ishaAngle = 15; break;   // isna
        }
        // Asr: the sun's altitude at which a stick's shadow is its own length (x2 for Hanafi) plus its noon shadow.
        double asrAltitude = Math.atan(1 / ((hanafi ? 2 : 1) + Math.tan(Math.abs(lat - decl))));
        double maghrib = noon + hourAngleMinutes(-Math.toRadians(0.833), lat, decl);
        return new int[]{
                round(noon - hourAngleMinutes(-Math.toRadians(fajrAngle), lat, decl)),
                round(noon + 1),
                round(noon + hourAngleMinutes(asrAltitude, lat, decl)),
                round(maghrib),
                ishaAfterMaghrib > 0 ? round(maghrib + ishaAfterMaghrib)
                        : round(noon + hourAngleMinutes(-Math.toRadians(ishaAngle), lat, decl))};
    }

    /** Index into {@link #NAMES} of the next prayer after {@code minuteOfDay}, or -1 when it is tomorrow's Fajr. */
    static int next(int[] times, int minuteOfDay) {
        for (int i = 0; i < times.length; i++) if (times[i] > minuteOfDay) return i;
        return -1;
    }

    /** Minutes from noon until the sun is at {@code altitude} radians; NaN if it never gets there. */
    private static double hourAngleMinutes(double altitude, double lat, double decl) {
        double cosHa = (Math.sin(altitude) - Math.sin(lat) * Math.sin(decl)) / (Math.cos(lat) * Math.cos(decl));
        return 4 * Math.toDegrees(Math.acos(cosHa));
    }

    private static int round(double minutes) {
        return Double.isNaN(minutes) ? -1 : (int) Math.round(minutes);
    }
}
