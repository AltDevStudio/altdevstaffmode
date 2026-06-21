package studio.altdev.staffmode.chat;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import studio.altdev.staffmode.StaffMod;

import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Обрабатывает входящие сообщения чата:
 *  - делает сообщение кликабельным (клик подставляет /mute Ник в строку ввода);
 *  - считает выданные мутом/баны по сообщениям-подтверждениям сервера.
 */
public final class ChatTracker {

    private ChatTracker() {}

    private static Pattern cachedNickPattern;
    private static String cachedNickRegex;

    public static void register() {
        // Player-чат: ник известен из GameProfile отправителя.
        ClientReceiveMessageEvents.MODIFY_CHAT.register(ChatTracker::onChat);
        // Системные (game) сообщения: ник вытаскиваем регуляркой, плюс статистика.
        ClientReceiveMessageEvents.MODIFY_GAME.register(ChatTracker::onGame);
    }

    private static Text onChat(Text message, SignedMessage signedMessage, GameProfile sender,
                              MessageType.Parameters params, Instant receptionTimestamp) {
        trackStats(message.getString());

        String nick = sender != null ? sender.getName() : null;
        return makeMuteClickable(message, nick);
    }

    private static Text onGame(Text message, boolean overlay) {
        if (overlay) return message; // не трогаем action bar
        String text = message.getString();
        trackStats(text);

        if (!StaffMod.config.clickToMuteOnGameMessages) {
            return message;
        }
        String nick = extractNick(text);
        return makeMuteClickable(message, nick);
    }

    // ---- Клик-мут ----

    private static Text makeMuteClickable(Text message, String nick) {
        if (!StaffMod.config.clickToMuteEnabled || nick == null || nick.isBlank()) {
            return message;
        }
        final String command = StaffMod.config.muteCommandTemplate.replace("{nick}", nick);
        final MutableText copy = message.copy();
        return copy.styled(style -> style
                .withClickEvent(new ClickEvent.SuggestCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Замутить " + nick))));
    }

    private static String extractNick(String text) {
        String regex = StaffMod.config.gameMessageNickRegex;
        if (regex == null || regex.isBlank()) return null;
        try {
            if (cachedNickPattern == null || !regex.equals(cachedNickRegex)) {
                cachedNickPattern = Pattern.compile(regex);
                cachedNickRegex = regex;
            }
            Matcher m = cachedNickPattern.matcher(text);
            if (m.find() && m.groupCount() >= 1) {
                return m.group(1);
            }
        } catch (Exception e) {
            // битая регулярка — просто не делаем клик
        }
        return null;
    }

    // ---- Статистика наказаний ----

    private static void trackStats(String text) {
        if (text == null || text.isEmpty()) return;
        String lower = text.toLowerCase();

        if (StaffMod.config.requireOwnNameInStatMessage) {
            String myName = ownName();
            if (myName == null || !lower.contains(myName.toLowerCase())) {
                return;
            }
        }

        if (containsAny(lower, StaffMod.config.banKeywords)) {
            StaffMod.stats.addBan();
        } else if (containsAny(lower, StaffMod.config.muteKeywords)) {
            StaffMod.stats.addMute();
        }
    }

    private static boolean containsAny(String haystack, List<String> needles) {
        if (needles == null) return false;
        for (String n : needles) {
            if (n != null && !n.isBlank() && haystack.contains(n.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String ownName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return null;
        return client.player.getGameProfile().getName();
    }
}
