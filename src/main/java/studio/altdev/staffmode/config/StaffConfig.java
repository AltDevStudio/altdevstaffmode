package studio.altdev.staffmode.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Настройки мода. Хранятся в config/altstaffmod.json.
 * Почти всё вынесено в конфиг, потому что форматы сообщений на разных серверах разные —
 * при необходимости их можно подкрутить под конкретный сервер.
 */
public class StaffConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("altstaffmod.json");

    // ---- Авто-подпись бана ----

    /** Текст "Ответа" (например, ссылка на ВК). Редактируется в меню. */
    public String vkAnswer = "vk.com/yourgroup";

    /**
     * Шаблон приписки к команде бана. {answer} заменяется на vkAnswer.
     * Итог для /ban nick 1d причина -> "ban nick 1d причина - Вопросы? ВК » \"vk.com/...\""
     */
    public String banSuffixTemplate = "- Вопросы? ВК » \"{answer}\"";

    /** Команды, к которым приписывается подпись (без слэша, в нижнем регистре). */
    public List<String> banCommandAliases = new ArrayList<>(List.of("ban", "tempban", "banip"));

    /** Включить авто-подпись бана. */
    public boolean banSuffixEnabled = true;

    // ---- Быстрый мут кликом по чату ----

    /** Включить подсказку /mute при клике по сообщению в чате. */
    public boolean clickToMuteEnabled = true;

    /** Шаблон команды мута. {nick} -> ник игрока. В конце пробел, чтобы дописать время/причину. */
    public String muteCommandTemplate = "/mute {nick} ";

    /** Применять клик-мут и к системным (game) сообщениям сервера, а не только к player-чату. */
    public boolean clickToMuteOnGameMessages = true;

    /**
     * Регулярка для вытаскивания ника из системного сообщения чата (group 1 — ник).
     * По умолчанию ловит форматы "&lt;Ник&gt;" и "Ник:". Подстрой под свой сервер.
     */
    public String gameMessageNickRegex = "(?:<|\\b)([A-Za-z0-9_]{3,16})(?:>|:)\\s";

    // ---- Статистика мутов/банов из чата ----

    /**
     * Учитывать наказание, только если в сообщении есть ник самого игрока
     * (например: Администратор "МойНик" замутил ...). Защищает от учёта чужих наказаний.
     */
    public boolean requireOwnNameInStatMessage = true;

    /** Ключевые слова (в нижнем регистре), по которым сообщение считается мутом. */
    public List<String> muteKeywords = new ArrayList<>(List.of("замут"));

    /** Ключевые слова, по которым сообщение считается баном. */
    public List<String> banKeywords = new ArrayList<>(List.of("забан"));

    // ---- Загрузка / сохранение ----

    public static StaffConfig load() {
        try {
            if (Files.exists(PATH)) {
                String json = Files.readString(PATH);
                StaffConfig cfg = GSON.fromJson(json, StaffConfig.class);
                if (cfg != null) {
                    cfg.sanitize();
                    return cfg;
                }
            }
        } catch (Exception e) {
            System.err.println("[AltStaffMod] Не удалось загрузить конфиг: " + e.getMessage());
        }
        StaffConfig cfg = new StaffConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[AltStaffMod] Не удалось сохранить конфиг: " + e.getMessage());
        }
    }

    /** Подстраховка на случай старого/битого конфига. */
    private void sanitize() {
        if (banCommandAliases == null) banCommandAliases = new ArrayList<>(List.of("ban"));
        if (muteKeywords == null) muteKeywords = new ArrayList<>(List.of("замут"));
        if (banKeywords == null) banKeywords = new ArrayList<>(List.of("забан"));
        if (banSuffixTemplate == null) banSuffixTemplate = "- Вопросы? ВК » \"{answer}\"";
        if (muteCommandTemplate == null) muteCommandTemplate = "/mute {nick} ";
        if (vkAnswer == null) vkAnswer = "";
        if (gameMessageNickRegex == null) gameMessageNickRegex = "";
    }
}
