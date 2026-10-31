package com.taikin.chataisum.client;

import com.taikin.chataisum.ChatAISumMod;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class ModelCatalog {
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
		Thread thread = new Thread(runnable, "chataisum-models");
		thread.setDaemon(true);
		return thread;
	});
	private static final long TTL_MS = 30 * 60 * 1000L;

	private static final Map<Backend, Entry> CACHE = new ConcurrentHashMap<>();
	private static final Set<Backend> IN_FLIGHT = ConcurrentHashMap.newKeySet();

	private ModelCatalog() {
	}

	private record Entry(long fetchedAt, List<String> models) {
	}

	public static List<String> get(Backend backend) {
		Entry entry = CACHE.get(backend);
		return entry == null ? List.of() : entry.models();
	}

	public static boolean isStale(Backend backend) {
		Entry entry = CACHE.get(backend);
		return entry == null || System.currentTimeMillis() - entry.fetchedAt() > TTL_MS;
	}

	public static void refreshAll() {
		for (Backend backend : Backend.values()) {
			if (!isStale(backend)) {
				continue;
			}
			ChatAISumConfig config = ChatAISumConfig.get();
			if (backend == Backend.OPENAI && config.resolveOpenAiKey().isBlank()) {
				continue;
			}
			if (backend == Backend.GEMINI && config.resolveGeminiKey().isBlank()) {
				continue;
			}
			refreshAsync(backend, null);
		}
	}

	public static void refreshAsync(Backend backend, Consumer<List<String>> callback) {
		if (!IN_FLIGHT.add(backend)) {
			return;
		}
		EXECUTOR.execute(() -> {
			try {
				List<String> models = ApiClient.listModels(backend, ChatAISumConfig.get());
				CACHE.put(backend, new Entry(System.currentTimeMillis(), models));
				ChatAISumMod.LOGGER.info("Loaded {} {} models", models.size(), backend.id());
				if (callback != null) {
					callback.accept(models);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				if (backend == Backend.COMPATIBLE) {
					ChatAISumMod.LOGGER.debug("Could not load {} models: {}", backend.id(), e.toString());
				} else {
					ChatAISumMod.LOGGER.warn("Could not load {} models: {}", backend.id(), e.toString());
				}
				if (callback != null) {
					callback.accept(List.of());
				}
			} finally {
				IN_FLIGHT.remove(backend);
			}
		});
	}

	public static List<String> search(Backend backend, String query) {
		List<String> models = get(backend);
		if (query == null || query.isBlank()) {
			return models;
		}
		String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
		return models.stream()
				.filter(model -> model.toLowerCase(java.util.Locale.ROOT).contains(needle))
				.toList();
	}
}
