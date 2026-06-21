package studio.altdev.staffmode.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Учёт отыгранного времени (сессия / сутки / неделя) и количества выданных мутов/банов.
 * Данные хранятся в config/altstaffmod_stats.json.
 */
public class StatsManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("altstaffmod_stats.json");

    /** Сериализуемые данные. Ключ карт — дата в формате ISO (yyyy-MM-dd). */
    public static class StatsData {
        public Map<String, Long> dailyPlayMillis = new HashMap<>();
        public Map<String, Integer> dailyMutes = new HashMap<>();
        public Map<String, Integer> dailyBans = new HashMap<>();
        public long totalMutes = 0;
        public long totalBans = 0;
    }

    private StatsData data = new StatsData();

    // Тайминг сессии
    private long sessionStartMillis = 0;
    private long lastTickMillis = 0;
    private int saveCounter = 0;

    // ---- Жизненный цикл ----

    public void load() {
        try {
            if (Files.exists(PATH)) {
                String json = Files.readString(PATH);
                StatsData loaded = GSON.fromJson(json, StatsData.class);
                if (loaded != null) {
                    if (loaded.dailyPlayMillis == null) loaded.dailyPlayMillis = new HashMap<>();
                    if (loaded.dailyMutes == null) loaded.dailyMutes = new HashMap<>();
                    if (loaded.dailyBans == null) loaded.dailyBans = new HashMap<>();
                    data = loaded;
                }
            }
        } catch (Exception e) {
            System.err.println("[AltStaffMod] Не удалось загрузить статистику: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(data));
        } catch (IOException e) {
            System.err.println("[AltStaffMod] Не удалось сохранить статистику: " + e.getMessage());
        }
    }

    public void onSessionStart() {
        sessionStartMillis = System.currentTimeMillis();
        lastTickMillis = sessionStartMillis;
    }

    public void onSessionEnd() {
        flushTickTime();
        sessionStartMillis = 0;
        lastTickMillis = 0;
        save();
    }

    // ---- Тики: накопление времени ----

    public void tick(MinecraftClient client) {
        if (client.player == null) {
            // не в мире — пауза тайминга
            lastTickMillis = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (sessionStartMillis == 0) {
            sessionStartMillis = now;
        }
        flushTickTimeAt(now);

        if (++saveCounter >= 600) { // ~ каждые 30 секунд
            saveCounter = 0;
            save();
        }
    }

    private void flushTickTime() {
        flushTickTimeAt(System.currentTimeMillis());
    }

    private void flushTickTimeAt(long now) {
        if (lastTickMillis != 0) {
            long delta = now - lastTickMillis;
            // Защита от больших скачков (свернутое окно / зависание / перевод часов).
            if (delta > 0 && delta < 60_000) {
                String today = LocalDate.now().toString();
                data.dailyPlayMillis.merge(today, delta, Long::sum);
            }
        }
        lastTickMillis = now;
    }

    // ---- Учёт наказаний ----

    public void addMute() {
        data.totalMutes++;
        String today = LocalDate.now().toString();
        data.dailyMutes.merge(today, 1, Integer::sum);
        save();
    }

    public void addBan() {
        data.totalBans++;
        String today = LocalDate.now().toString();
        data.dailyBans.merge(today, 1, Integer::sum);
        save();
    }

    // ---- Геттеры для меню ----

    public long getSessionMillis() {
        return sessionStartMillis == 0 ? 0 : System.currentTimeMillis() - sessionStartMillis;
    }

    public long getTodayPlayMillis() {
        return data.dailyPlayMillis.getOrDefault(LocalDate.now().toString(), 0L);
    }

    public long getWeekPlayMillis() {
        return sumLastWeek(data.dailyPlayMillis, 0L, Long::sum);
    }

    public int getTodayMutes() {
        return data.dailyMutes.getOrDefault(LocalDate.now().toString(), 0);
    }

    public int getTodayBans() {
        return data.dailyBans.getOrDefault(LocalDate.now().toString(), 0);
    }

    public int getWeekMutes() {
        return (int) sumLastWeek(data.dailyMutes, 0L, (a, b) -> a + b);
    }

    public int getWeekBans() {
        return (int) sumLastWeek(data.dailyBans, 0L, (a, b) -> a + b);
    }

    public long getTotalMutes() {
        return data.totalMutes;
    }

    public long getTotalBans() {
        return data.totalBans;
    }

    public void resetSession() {
        sessionStartMillis = System.currentTimeMillis();
        lastTickMillis = sessionStartMillis;
    }

    // ---- Вспомогательное ----

    private <T extends Number> long sumLastWeek(Map<String, T> map, Long zero,
                                                java.util.function.BiFunction<Long, Long, Long> sum) {
        LocalDate from = LocalDate.now().minusDays(6); // последние 7 дней включая сегодня
        long acc = zero;
        for (Map.Entry<String, T> e : map.entrySet()) {
            try {
                LocalDate d = LocalDate.parse(e.getKey());
                if (!d.isBefore(from)) {
                    acc = sum.apply(acc, e.getValue().longValue());
                }
            } catch (Exception ignored) {
                // битый ключ — пропускаем
            }
        }
        return acc;
    }
}
