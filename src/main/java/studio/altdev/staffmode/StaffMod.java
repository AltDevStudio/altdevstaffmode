package studio.altdev.staffmode;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import studio.altdev.staffmode.chat.ChatTracker;
import studio.altdev.staffmode.config.StaffConfig;
import studio.altdev.staffmode.gui.StaffMenuScreen;
import studio.altdev.staffmode.stats.StatsManager;

public class StaffMod implements ClientModInitializer {

    public static final String MOD_ID = "altstaffmod";

    private static final KeyBinding.Category MENU_CATEGORY =
            KeyBinding.Category.create(Identifier.of(MOD_ID, "general"));

    public static StaffConfig config;
    public static StatsManager stats;

    private static KeyBinding menuKey;

    // Защита от рекурсии при пере-отправке модифицированной команды.
    private static boolean resendingGuard = false;

    @Override
    public void onInitializeClient() {
        config = StaffConfig.load();
        stats = new StatsManager();
        stats.load();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.altstaffmod.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                MENU_CATEGORY
        ));

        // Открытие меню + накопление времени
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            stats.tick(client);
            while (menuKey.wasPressed()) {
                client.setScreen(new StaffMenuScreen());
            }
        });

        // Сессия = заход/выход с сервера
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> stats.onSessionStart());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stats.onSessionEnd());

        // Сохранение при закрытии игры
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            stats.save();
            config.save();
        });

        // Перехват исходящих команд для авто-подписи бана
        ClientSendMessageEvents.ALLOW_COMMAND.register(StaffMod::onSendCommand);

        // Приём чата: клик-мут + статистика наказаний
        ChatTracker.register();

        System.out.println("[AltStaffMod] Инициализирован.");
    }

    /**
     * Возвращает false, чтобы отменить исходную команду, и пере-отправляет модифицированную.
     * command — текст команды БЕЗ ведущего слэша.
     */
    private static boolean onSendCommand(String command) {
        if (resendingGuard) {
            // Это наша же пере-отправка — пропускаем как есть.
            return true;
        }
        if (config == null || !config.banSuffixEnabled) {
            return true;
        }
        String modified = modifyBanCommand(command);
        if (modified.equals(command)) {
            return true;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.networkHandler == null) {
            return true;
        }
        resendingGuard = true;
        try {
            client.player.networkHandler.sendChatCommand(modified);
        } finally {
            resendingGuard = false;
        }
        return false; // отменяем оригинал — ушла уже модифицированная версия
    }

    /** Дописывает подпись "- Вопросы? ВК » ..." к командам бана. */
    public static String modifyBanCommand(String command) {
        if (command == null || command.isBlank()) return command;
        String trimmed = command.stripLeading();
        int sp = trimmed.indexOf(' ');
        String first = (sp == -1 ? trimmed : trimmed.substring(0, sp)).toLowerCase();

        if (!config.banCommandAliases.contains(first)) {
            return command;
        }
        String suffix = config.banSuffixTemplate
                .replace("{answer}", config.vkAnswer == null ? "" : config.vkAnswer);

        // Не дублируем, если подпись уже есть.
        if (suffix.isBlank() || command.contains(suffix)) {
            return command;
        }
        return command + " " + suffix;
    }
}
