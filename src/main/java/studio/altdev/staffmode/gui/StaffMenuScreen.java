package studio.altdev.staffmode.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import studio.altdev.staffmode.StaffMod;
import studio.altdev.staffmode.stats.StatsManager;
import studio.altdev.staffmode.util.TimeUtil;

/**
 * Меню модерации (открывается по Right Shift):
 *  - редактирование текста "Ответа" для подписи бана;
 *  - просмотр статистики времени и наказаний.
 */
public class StaffMenuScreen extends Screen {

    private TextFieldWidget answerField;

    public StaffMenuScreen() {
        super(Text.literal("Alt Staff Mode"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = 40;

        // Поле "Ответ"
        answerField = new TextFieldWidget(this.textRenderer, cx - 150, top + 12, 300, 20,
                Text.literal("Ответ"));
        answerField.setMaxLength(256);
        answerField.setText(StaffMod.config.vkAnswer == null ? "" : StaffMod.config.vkAnswer);
        addDrawableChild(answerField);
        setInitialFocus(answerField);

        // Кнопки внизу
        int btnY = this.height - 60;

        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"), b -> {
            saveAnswer();
        }).dimensions(cx - 152, btnY, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Сбросить сессию"), b -> {
            StaffMod.stats.resetSession();
        }).dimensions(cx - 50, btnY, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> {
            this.close();
        }).dimensions(cx + 52, btnY, 100, 20).build());
    }

    private void saveAnswer() {
        StaffMod.config.vkAnswer = answerField.getText();
        StaffMod.config.save();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int white = 0xFFFFFF;
        int gray = 0xAAAAAA;
        int accent = 0x55FF55;

        // Заголовок
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 16, white);

        // Подпись к полю
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Ответ (подставляется в подпись бана):"), cx - 150, 40, gray);

        // Превью итоговой подписи
        String preview = StaffMod.config.banSuffixTemplate
                .replace("{answer}", answerField != null ? answerField.getText() : "");
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Пример: ... причина " + preview), cx - 150, 78, gray);

        // ---- Статистика ----
        StatsManager s = StaffMod.stats;
        int y = 110;
        int line = 14;

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("§lСтатистика"), cx - 150, y, accent);
        y += line + 4;

        context.drawTextWithShadow(this.textRenderer, Text.literal(
                "Время за сессию: §f" + TimeUtil.format(s.getSessionMillis())), cx - 150, y, gray);
        y += line;
        context.drawTextWithShadow(this.textRenderer, Text.literal(
                "Время за сутки: §f" + TimeUtil.format(s.getTodayPlayMillis())), cx - 150, y, gray);
        y += line;
        context.drawTextWithShadow(this.textRenderer, Text.literal(
                "Время за неделю: §f" + TimeUtil.format(s.getWeekPlayMillis())), cx - 150, y, gray);
        y += line + 6;

        context.drawTextWithShadow(this.textRenderer, Text.literal(
                "Муты — сегодня: §f" + s.getTodayMutes()
                        + " §7| неделя: §f" + s.getWeekMutes()
                        + " §7| всего: §f" + s.getTotalMutes()), cx - 150, y, gray);
        y += line;
        context.drawTextWithShadow(this.textRenderer, Text.literal(
                "Баны — сегодня: §f" + s.getTodayBans()
                        + " §7| неделя: §f" + s.getWeekBans()
                        + " §7| всего: §f" + s.getTotalBans()), cx - 150, y, gray);
    }

    @Override
    public void close() {
        // Сохраняем введённый "Ответ" при закрытии.
        saveAnswer();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
