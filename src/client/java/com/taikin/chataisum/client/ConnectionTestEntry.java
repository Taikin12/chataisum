package com.taikin.chataisum.client;

import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

public final class ConnectionTestEntry extends TooltipListEntry<Boolean> {
	private static final int SUCCESS_COLOR = 0x55FF55;
	private static final int FAILURE_COLOR = 0xFF5555;
	private static final int PENDING_COLOR = 0xFFFF55;
	private static final int IDLE_COLOR = 0xA0A0A0;

	private final Button button;
	private volatile String status = "";
	private volatile int statusColor = IDLE_COLOR;
	private volatile boolean running;

	public ConnectionTestEntry(Component fieldName) {
		super(fieldName, () -> Optional.empty());
		this.button = Button.builder(Component.literal("接続を確認"), b -> startTest())
				.bounds(0, 0, 150, 20)
				.build();
	}

	private void startTest() {
		if (running) {
			return;
		}
		running = true;
		status = "確認中...";
		statusColor = PENDING_COLOR;
		button.active = false;
		button.setMessage(Component.literal("確認中..."));

		ChatAISumService.testConnection(result -> {
			running = false;
			button.active = true;
			button.setMessage(Component.literal(result.success() ? "OK" : "失敗"));
			status = result.formatted();
			statusColor = result.success() ? SUCCESS_COLOR : FAILURE_COLOR;
		});
	}

	@Override
	public Boolean getValue() {
		return Boolean.FALSE;
	}

	@Override
	public Optional<Boolean> getDefaultValue() {
		return Optional.empty();
	}

	@Override
	public int getItemHeight() {
		return 42;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int index, int y, int x, int entryWidth,
			int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
		super.extractRenderState(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
		Font font = Minecraft.getInstance().font;
		graphics.text(font, getFieldName(), x, y + 6, getPreferredTextColor());
		button.setX(x + entryWidth - 150);
		button.setY(y);
		button.setWidth(150);
		button.extractRenderState(graphics, mouseX, mouseY, delta);

		if (!status.isEmpty()) {
			int maxWidth = Math.max(10, entryWidth - 10);
			String shown = font.plainSubstrByWidth(status, maxWidth);
			if (shown.length() < status.length()) {
				shown = font.plainSubstrByWidth(status, Math.max(10, maxWidth - font.width("..."))) + "...";
			}
			graphics.text(font, Component.literal(shown), x, y + 25, statusColor);
		}
	}

	@Override
	public List<? extends GuiEventListener> children() {
		return List.of(button);
	}

	@Override
	public List<? extends NarratableEntry> narratables() {
		return List.of(button);
	}
}
