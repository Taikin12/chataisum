package com.taikin.chataisum.client;

import com.taikin.chataisum.ChatAISumMod;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatAISumService {
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "chataisum-worker");
		thread.setDaemon(true);
		return thread;
	});
	private static final AtomicBoolean BUSY = new AtomicBoolean();

	private ChatAISumService() {
	}

	public static void summarize(int minutes, String focus, FabricClientCommandSource source) {
		ChatAISumConfig config = ChatAISumConfig.get();
		if (!config.enabled) {
			source.sendFeedback(Component.literal("[ChatAISum] 無効です。/chataisum on で有効にできます。"));
			return;
		}
		if (!BUSY.compareAndSet(false, true)) {
			source.sendFeedback(Component.literal("[ChatAISum] 前の要約を実行中です。"));
			return;
		}
		ChatLog.Snapshot snapshot = ChatLog.read(minutes);
		if (snapshot.entries().isEmpty()) {
			BUSY.set(false);
			source.sendFeedback(Component.literal("[ChatAISum] 過去" + minutes + "分のチャットログに要約できるメッセージがありません。"));
			return;
		}
		source.sendFeedback(Component.literal("[ChatAISum] 過去" + minutes + "分の "
				+ snapshot.totalInWindow() + " 件を要約しています..."));

		EXECUTOR.execute(() -> {
			try {
				String summary = ApiClient.summarize(snapshot, minutes, focus, config);
				onClientThread(() -> show(summary, minutes, snapshot));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				onClientThread(() -> chat("[ChatAISum] 要約が中断されました。"));
			} catch (Exception e) {
				ChatAISumMod.LOGGER.warn("Chat summary failed", e);
				String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
				onClientThread(() -> chat("[ChatAISum] 要約に失敗しました: " + message));
			} finally {
				BUSY.set(false);
			}
		});
	}

	public record TestResult(boolean success, String message, long elapsedMs) {
		public String formatted() {
			return success
					? "接続OK (" + elapsedMs + " ms): " + message
					: "接続失敗: " + message;
		}
	}

	public static void testConnection(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("[ChatAISum] 接続を確認しています..."));
		testConnection(result -> chat("[ChatAISum] " + result.formatted()));
	}

	public static void testConnection(java.util.function.Consumer<TestResult> callback) {
		ChatAISumConfig config = ChatAISumConfig.get();
		EXECUTOR.execute(() -> {
			long start = System.currentTimeMillis();
			try {
				String result = ApiClient.test(config);
				long elapsed = System.currentTimeMillis() - start;
				onClientThread(() -> callback.accept(new TestResult(true, result, elapsed)));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				long elapsed = System.currentTimeMillis() - start;
				onClientThread(() -> callback.accept(new TestResult(false, "中断されました", elapsed)));
			} catch (Exception e) {
				ChatAISumMod.LOGGER.warn("Connection test failed", e);
				String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
				long elapsed = System.currentTimeMillis() - start;
				onClientThread(() -> callback.accept(new TestResult(false, message, elapsed)));
			}
		});
	}

	private static void show(String summary, int minutes, ChatLog.Snapshot snapshot) {
		List<String> lines = new ArrayList<>();
		for (String raw : summary.split("\\R")) {
			String line = raw.strip();
			if (line.isEmpty()) {
				continue;
			}
			for (String wrapped : wrap(line, 220)) {
				lines.add(wrapped);
			}
		}
		if (lines.isEmpty()) {
			chat("[ChatAISum] 要約結果が空でした。");
			return;
		}
		int maxLines = ChatAISumConfig.get().maxLines;
		boolean clipped = lines.size() > maxLines;
		if (clipped) {
			lines = lines.subList(0, maxLines);
		}
		String header = "[ChatAISum] 過去" + minutes + "分の要約 ("
				+ snapshot.totalInWindow() + "件" + (snapshot.truncated() ? "、一部のみ" : "") + "):";
		chat(header);
		for (String line : lines) {
			chat(line);
		}
		if (clipped) {
			chat("[ChatAISum] （以下省略）");
		}
	}

	private static List<String> wrap(String text, int width) {
		List<String> out = new ArrayList<>();
		int start = 0;
		while (start < text.length()) {
			int end = Math.min(text.length(), start + width);
			out.add(text.substring(start, end));
			start = end;
		}
		if (out.isEmpty()) {
			out.add(text);
		}
		return out;
	}

	private static void chat(String text) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.gui != null && client.gui.hud != null) {
			client.gui.hud.getChat().addClientSystemMessage(Component.literal(text));
		}
	}

	private static void onClientThread(Runnable runnable) {
		Minecraft client = Minecraft.getInstance();
		if (client != null) {
			client.execute(runnable);
		}
	}
}
