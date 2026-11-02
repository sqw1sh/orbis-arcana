package com.orbisarcana.client.ui;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Button of the UI kit: dark slate (or teal for the main action of a screen), teal edge on hover and focus. */
public class OrbisButton extends AbstractButton {
    private final Ui.ButtonStyle style;
    private final Consumer<OrbisButton> onPress;

    public OrbisButton(int x, int y, int width, int height, Component message, Ui.ButtonStyle style, Consumer<OrbisButton> onPress) {
        super(x, y, width, height, message);
        this.style = style;
        this.onPress = onPress;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.accept(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Ui.button(g, getX(), getY(), getWidth(), getHeight(), style, isHoveredOrFocused(), active);
        var font = Minecraft.getInstance().font;
        int color = !active ? Ui.TEXT_DIM : style == Ui.ButtonStyle.PRIMARY ? 0xFFEFFFFB : Ui.TEXT;
        g.centeredText(font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
