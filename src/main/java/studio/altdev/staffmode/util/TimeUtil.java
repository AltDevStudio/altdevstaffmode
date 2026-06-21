package studio.altdev.staffmode.util;

public final class TimeUtil {

    private TimeUtil() {}

    /** Форматирует длительность (мс) в "Xч Yм" / "Yм Zс" / "Zс". */
    public static String format(long millis) {
        if (millis < 0) millis = 0;
        long totalSec = millis / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return h + "ч " + m + "м";
        if (m > 0) return m + "м " + s + "с";
        return s + "с";
    }
}
