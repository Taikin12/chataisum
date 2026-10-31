package com.taikin.chataisum.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.taikin.chataisum.ChatAISumMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ChatAISumConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("chataisum.json");
	private static final Path LEGACY_PATH = FabricLoader.getInstance().getConfigDir().resolve("chatsum.json");
	private static final String LEGACY_OPENCODE_URL = "http://127.0.0.1:4096";
	private static final String ZEN_URL = "https://opencode.ai/zen/v1";

	private static volatile ChatAISumConfig instance;

	public boolean enabled = true;

	public Backend backend = Backend.OPENAI;

	public String openaiUrl = "https://api.openai.com/v1";
	public String openaiApiKey = "";
	public String openaiModel = "gpt-6-luna";

	public String openrouterUrl = "https://openrouter.ai/api/v1";
	public String openrouterApiKey = "";
	public String openrouterModel = "openai/gpt-6-luna";

	public String opencodeUrl = ZEN_URL;
	public String opencodeApiKey = "";
	public String opencodeModel = "gpt-6-luna";

	public String compatibleUrl = "http://127.0.0.1:1234/v1";
	public String compatibleApiKey = "";
	public String compatibleModel = "";

	public String geminiUrl = "https://generativelanguage.googleapis.com/v1beta";
	public String geminiApiKey = "";
	public String geminiModel = "gemini-3.8-flash";

	public int timeoutMs = 120000;
	public int maxOutputTokens = 6000;
	public double temperature = 0.3;

	public String language = "Japanese (日本語)";
	public String instruction = "";
	public String systemPrompt = ChatPrompt.DEFAULT_SYSTEM;
	public String userPrompt = ChatPrompt.DEFAULT_USER;
	public int maxMessages = 800;
	public int maxChars = 12000;
	public int maxLines = 12;
	public boolean includeSystem = true;

	public static ChatAISumConfig get() {
		ChatAISumConfig config = instance;
		if (config == null) {
			synchronized (ChatAISumConfig.class) {
				config = instance;
				if (config == null) {
					config = load();
					instance = config;
				}
			}
		}
		return config;
	}

	public static void reload() {
		synchronized (ChatAISumConfig.class) {
			instance = load();
		}
	}

	private static ChatAISumConfig load() {
		if (!Files.exists(PATH) && Files.exists(LEGACY_PATH)) {
			try {
				Files.createDirectories(PATH.getParent());
				Files.copy(LEGACY_PATH, PATH);
			} catch (IOException e) {
				ChatAISumMod.LOGGER.warn("Could not migrate {}", LEGACY_PATH, e);
			}
		}
		ChatAISumConfig config = null;
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				config = GSON.fromJson(reader, ChatAISumConfig.class);
			} catch (IOException | JsonSyntaxException e) {
				ChatAISumMod.LOGGER.warn("Could not read {}, using defaults", PATH, e);
			}
		}
		if (config == null) {
			config = new ChatAISumConfig();
		}
		config.normalize();
		config.save();
		return config;
	}

	private void normalize() {
		if (backend == null) {
			backend = Backend.OPENAI;
		}
		openaiUrl = normalizeUrl(openaiUrl, "https://api.openai.com/v1");
		openaiApiKey = trimKey(openaiApiKey);
		openaiModel = trimModel(openaiModel, "gpt-6-luna");
		if ("gpt-4o-mini".equals(openaiModel)) {
			openaiModel = "gpt-6-luna";
		}
		openrouterUrl = normalizeUrl(openrouterUrl, "https://openrouter.ai/api/v1");
		openrouterApiKey = trimKey(openrouterApiKey);
		openrouterModel = trimModel(openrouterModel, "openai/gpt-6-luna");
		if ("openai/gpt-4o-mini".equals(openrouterModel)) {
			openrouterModel = "openai/gpt-6-luna";
		}

		boolean legacyLocal = LEGACY_OPENCODE_URL.equals(opencodeUrl);
		opencodeUrl = normalizeUrl(opencodeUrl, ZEN_URL);
		opencodeApiKey = trimKey(opencodeApiKey);
		opencodeModel = trimModel(opencodeModel, "gpt-6-luna");
		if (legacyLocal) {
			opencodeUrl = ZEN_URL;
			opencodeModel = "gpt-6-luna";
		}
		compatibleUrl = normalizeUrl(compatibleUrl, "http://127.0.0.1:1234/v1");
		compatibleApiKey = trimKey(compatibleApiKey);
		compatibleModel = trimModel(compatibleModel, "");
		geminiUrl = normalizeUrl(geminiUrl, "https://generativelanguage.googleapis.com/v1beta");
		geminiApiKey = trimKey(geminiApiKey);
		geminiModel = trimModel(geminiModel, "gemini-3.8-flash");

		if (language == null || language.isBlank()) {
			language = "Japanese (日本語)";
		}
		if (instruction == null) {
			instruction = "";
		}
		if (systemPrompt == null) {
			systemPrompt = ChatPrompt.DEFAULT_SYSTEM;
		}
		if (userPrompt == null) {
			userPrompt = ChatPrompt.DEFAULT_USER;
		}
		timeoutMs = Math.clamp(timeoutMs, 5000, 600000);
		if (maxOutputTokens == 800 || maxOutputTokens == 2000) {
			maxOutputTokens = 6000;
		}
		maxOutputTokens = Math.clamp(maxOutputTokens, 100, 16000);
		temperature = Math.max(0.0, Math.min(2.0, temperature));
		maxMessages = Math.clamp(maxMessages, 1, 4000);
		maxChars = Math.clamp(maxChars, 1000, 200000);
		maxLines = Math.clamp(maxLines, 1, 64);
	}

	private static String trimKey(String value) {
		return value == null ? "" : value.trim();
	}

	private static String trimModel(String value, String fallback) {
		if (value == null) {
			return fallback;
		}
		return value.trim();
	}

	private static String normalizeUrl(String value, String fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		String url = value.trim();
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			String scheme = fallback.startsWith("http://") ? "http://" : "https://";
			url = scheme + url;
		}
		return url;
	}

	public String resolveOpenAiKey() {
		return resolveKey(openaiApiKey, "OPENAI_API_KEY");
	}

	public String resolveOpenRouterKey() {
		return resolveKey(openrouterApiKey, "OPENROUTER_API_KEY");
	}

	public String resolveOpenCodeKey() {
		return resolveKey(opencodeApiKey, "OPENCODE_API_KEY");
	}

	public String resolveCompatibleKey() {
		return resolveKey(compatibleApiKey, "OPENAI_COMPATIBLE_API_KEY");
	}

	public String resolveGeminiKey() {
		return resolveKey(geminiApiKey, "GEMINI_API_KEY", "GOOGLE_API_KEY");
	}

	private static String resolveKey(String value, String... envNames) {
		if (value != null && !value.isBlank()) {
			return value.trim();
		}
		for (String envName : envNames) {
			String env = System.getenv(envName);
			if (env != null && !env.isBlank()) {
				return env.trim();
			}
		}
		return "";
	}

	public String activeUrl() {
		return switch (backend) {
			case OPENAI -> openaiUrl;
			case OPENROUTER -> openrouterUrl;
			case OPENCODE -> opencodeUrl;
			case COMPATIBLE -> compatibleUrl;
			case GEMINI -> geminiUrl;
		};
	}

	public void setActiveUrl(String value) {
		switch (backend) {
			case OPENAI -> openaiUrl = value;
			case OPENROUTER -> openrouterUrl = value;
			case OPENCODE -> opencodeUrl = value;
			case COMPATIBLE -> compatibleUrl = value;
			case GEMINI -> geminiUrl = value;
		}
	}

	public String activeModel() {
		return switch (backend) {
			case OPENAI -> openaiModel;
			case OPENROUTER -> openrouterModel;
			case OPENCODE -> opencodeModel;
			case COMPATIBLE -> compatibleModel;
			case GEMINI -> geminiModel;
		};
	}

	public void setActiveModel(String value) {
		switch (backend) {
			case OPENAI -> openaiModel = value;
			case OPENROUTER -> openrouterModel = value;
			case OPENCODE -> opencodeModel = value;
			case COMPATIBLE -> compatibleModel = value;
			case GEMINI -> geminiModel = value;
		}
	}

	public String activeKey() {
		return switch (backend) {
			case OPENAI -> resolveOpenAiKey();
			case OPENROUTER -> resolveOpenRouterKey();
			case OPENCODE -> resolveOpenCodeKey();
			case COMPATIBLE -> resolveCompatibleKey();
			case GEMINI -> resolveGeminiKey();
		};
	}

	public void setActiveKey(String value) {
		switch (backend) {
			case OPENAI -> openaiApiKey = value;
			case OPENROUTER -> openrouterApiKey = value;
			case OPENCODE -> opencodeApiKey = value;
			case COMPATIBLE -> compatibleApiKey = value;
			case GEMINI -> geminiApiKey = value;
		}
	}

	public boolean activeKeyPresent() {
		if (backend == Backend.COMPATIBLE) {
			return true;
		}
		return !activeKey().isBlank();
	}

	public void save() {
		normalize();
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			ChatAISumMod.LOGGER.warn("Could not save {}", PATH, e);
		}
	}
}
