package com.taikin.chataisum.client;

import com.taikin.chataisum.ChatAISumMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatLog {
	private static final Pattern LOG_TIME = Pattern.compile("^\\[(\\d{2}):(\\d{2}):(\\d{2})\\]");
	private static final Pattern CHAT_MARKER = Pattern.compile("\\[CHAT\\] (.*)$");
	private static final Pattern PLAYER_CHAT = Pattern.compile(
			"(^|\\s)<[^\\s>]{1,32}>|(^|\\s)[^\\s:：<>]{1,24}\\s*[:：]\\s");
	private static final Pattern OWN_OUTPUT = Pattern.compile("^\\[ChatAISum\\]");
	private static final Pattern SERVER_LOG_HEADER = Pattern.compile(
			"^-{3,}.*(?i:(ログ|log|発言)).*-{3,}$");
	private static final Pattern SERVER_LOG_END = Pattern.compile("^-{5,}$");
	private static final Pattern SERVER_LOG_LINE = Pattern.compile(
			"^\\|\\s*(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}),\\s*(.+)$");
	private static final DateTimeFormatter SERVER_LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private ChatLog() {
	}

	public record Entry(long timeMillis, String text, boolean playerChat) {
	}

	public record Snapshot(List<Entry> entries, int totalInWindow, boolean truncated, long nowMillis) {
	}

	public static Snapshot read(int minutes) {
		ChatAISumConfig config = ChatAISumConfig.get();
		ZoneId zone = ZoneId.systemDefault();
		long now = System.currentTimeMillis();
		long cutoff = now - minutes * 60_000L;
		Path path = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log");

		List<Entry> found = new ArrayList<>();
		if (!Files.exists(path)) {
			ChatAISumMod.LOGGER.warn("Chat log not found: {}", path);
			return new Snapshot(List.of(), 0, false, now);
		}

		List<String> lines;
		try {
			lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		} catch (IOException e) {
			ChatAISumMod.LOGGER.warn("Could not read {}", path, e);
			return new Snapshot(List.of(), 0, false, now);
		}

		LocalDate date = LocalDate.now(zone);
		LocalTime nowTime = LocalTime.now(zone);
		LocalDate firstDate = date;
		for (String line : lines) {
			Matcher matcher = LOG_TIME.matcher(line);
			if (matcher.find()) {
				LocalTime first = time(matcher);
				if (first.isAfter(nowTime)) {
					firstDate = firstDate.minusDays(1);
				}
				break;
			}
		}
		date = firstDate;

		LocalTime previous = null;
		boolean inServerLog = false;
		Set<String> seen = new HashSet<>();
		for (String line : lines) {
			Matcher timeMatcher = LOG_TIME.matcher(line);
			LocalTime lineTime = null;
			if (timeMatcher.find()) {
				lineTime = time(timeMatcher);
				if (previous != null && lineTime.isBefore(previous)) {
					date = date.plusDays(1);
				}
				previous = lineTime;
			}

			Matcher chatMatcher = CHAT_MARKER.matcher(line);
			if (!chatMatcher.find()) {
				continue;
			}
			String message = chatMatcher.group(1).strip();
			if (message.isEmpty() || OWN_OUTPUT.matcher(message).find()) {
				continue;
			}

			if (inServerLog) {
				if (SERVER_LOG_END.matcher(message).matches()) {
					inServerLog = false;
					continue;
				}
				Matcher inner = SERVER_LOG_LINE.matcher(message);
				if (inner.matches()) {
					try {
						long time = LocalDateTime.parse(inner.group(1), SERVER_LOG_TIME)
								.atZone(zone).toInstant().toEpochMilli();
						add(found, seen, time, inner.group(2).strip());
					} catch (RuntimeException ignored) {
					}
				}
				continue;
			}
			if (SERVER_LOG_HEADER.matcher(message).matches()) {
				inServerLog = true;
				continue;
			}
			if (lineTime == null) {
				continue;
			}
			long time = LocalDateTime.of(date, lineTime).atZone(zone).toInstant().toEpochMilli();
			add(found, seen, time, message);
		}

		List<Entry> window = new ArrayList<>();
		for (Entry entry : found) {
			if (entry.timeMillis() < cutoff || entry.timeMillis() > now + 60_000L) {
				continue;
			}
			if (!config.includeSystem && !entry.playerChat()) {
				continue;
			}
			window.add(entry);
		}
		window.sort(Comparator.comparingLong(Entry::timeMillis));

		List<Entry> selected = new ArrayList<>();
		int chars = 0;
		for (int i = window.size() - 1; i >= 0; i--) {
			Entry entry = window.get(i);
			int length = entry.text().length() + 20;
			if (selected.size() >= config.maxMessages || chars + length > config.maxChars) {
				continue;
			}
			selected.add(entry);
			chars += length;
		}
		selected.sort(Comparator.comparingLong(Entry::timeMillis));
		return new Snapshot(List.copyOf(selected), window.size(), selected.size() < window.size(), now);
	}

	private static void add(List<Entry> entries, Set<String> seen, long time, String text) {
		if (text.isEmpty()) {
			return;
		}
		String key = time + "\u0000" + text;
		if (!seen.add(key)) {
			return;
		}
		entries.add(new Entry(time, text, PLAYER_CHAT.matcher(text).find()));
	}

	private static LocalTime time(Matcher matcher) {
		return LocalTime.of(
				Integer.parseInt(matcher.group(1)),
				Integer.parseInt(matcher.group(2)),
				Integer.parseInt(matcher.group(3)));
	}
}
