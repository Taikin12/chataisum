package com.taikin.chataisum.client;

public enum Backend {
	OPENAI,
	OPENROUTER,
	OPENCODE,
	COMPATIBLE,
	GEMINI;

	public static Backend parse(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
		return switch (normalized) {
			case "openai", "gpt" -> OPENAI;
			case "openrouter", "or" -> OPENROUTER;
			case "opencode", "oc" -> OPENCODE;
			case "compatible", "compat", "custom", "other", "chat", "openai-compatible", "oai" -> COMPATIBLE;
			case "gemini", "google" -> GEMINI;
			default -> null;
		};
	}

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
