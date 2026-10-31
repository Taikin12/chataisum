package com.taikin.chataisum.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.taikin.chataisum.ChatAISumMod;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ApiClient {
	private static final Gson GSON = new Gson();
	private static final String USER_AGENT = "chatAIsum/0.4.0";
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private ApiClient() {
	}

	public static String summarize(ChatLog.Snapshot snapshot, int minutes, String focus, ChatAISumConfig config)
			throws IOException, InterruptedException {
		ChatAISumConfig cfg = config == null ? ChatAISumConfig.get() : config;
		Provider provider = Provider.of(cfg);
		String system = ChatPrompt.system(cfg);
		String user = ChatPrompt.user(snapshot, minutes, focus, cfg);
		Duration timeout = Duration.ofMillis(cfg.timeoutMs);
		return requestWithFallback(provider, cfg, timeout, system, user, cfg.maxOutputTokens);
	}

	private static String requestWithFallback(Provider provider, ChatAISumConfig cfg, Duration timeout,
			String system, String user, int maxTokens) throws IOException, InterruptedException {
		List<String> errors = new ArrayList<>();

		if (provider.backend() == Backend.GEMINI) {
			return requestGemini(provider, cfg, timeout, system, user, maxTokens);
		}

		if (provider.backend() == Backend.OPENAI || provider.backend() == Backend.OPENCODE) {
			try {
				return requestResponses(provider, cfg, timeout, system, user, maxTokens);
			} catch (IOException e) {
				errors.add(e.getMessage());
			}
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedException();
			}
		}

		try {
			return requestChatCompletions(provider, cfg, timeout, system, user, maxTokens);
		} catch (IOException e) {
			errors.add(e.getMessage());
		}
		if (Thread.currentThread().isInterrupted()) {
			throw new InterruptedException();
		}

		if (provider.backend() == Backend.COMPATIBLE) {
			try {
				return requestAnthropicMessages(provider, timeout, system, user, maxTokens);
			} catch (IOException e) {
				errors.add(e.getMessage());
			}
		}

		throw new IOException(String.join(" / ", errors));
	}

	public static List<String> listModels(Backend backend, ChatAISumConfig config)
			throws IOException, InterruptedException {
		ChatAISumConfig cfg = config == null ? ChatAISumConfig.get() : config;
		String base;
		String key;
		switch (backend) {
			case OPENAI -> {
				base = cfg.openaiUrl;
				key = cfg.resolveOpenAiKey();
				if (key.isBlank()) {
					throw new IOException("OPENAI_API_KEY が未設定です");
				}
			}
			case OPENROUTER -> {
				base = cfg.openrouterUrl;
				key = cfg.resolveOpenRouterKey();
			}
			case OPENCODE -> {
				base = cfg.opencodeUrl;
				key = cfg.resolveOpenCodeKey();
			}
			case COMPATIBLE -> {
				base = cfg.compatibleUrl;
				key = cfg.resolveCompatibleKey();
			}
			case GEMINI -> {
				base = cfg.geminiUrl;
				key = cfg.resolveGeminiKey();
				if (key.isBlank()) {
					throw new IOException("GEMINI_API_KEY が未設定です");
				}
			}
			default -> throw new IOException("unknown backend");
		}

		String url = base + "/models";
		Duration timeout = Duration.ofMillis(Math.min(cfg.timeoutMs, 30000));
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
				.timeout(timeout)
				.header("Accept", "application/json")
				.header("User-Agent", USER_AGENT)
				.GET();
		if (backend == Backend.GEMINI) {
			request.header("x-goog-api-key", key);
		} else if (!key.isBlank()) {
			request.header("Authorization", "Bearer " + key);
		}
		if (backend == Backend.OPENROUTER) {
			request.header("HTTP-Referer", "https://github.com/Taikin12/chataisum");
			request.header("X-Title", "chatAIsum");
		}

		HttpResponse<String> response = CLIENT.send(request.build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (response.statusCode() != 200) {
			throw new IOException(backend.id() + " /models returned HTTP " + response.statusCode() + ": "
					+ trim(response.body()));
		}
		JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
		JsonArray data = root == null ? null : root.getAsJsonArray("data");
		if (data == null) {
			throw new IOException(backend.id() + " /models returned no data");
		}
		Set<String> ids = new LinkedHashSet<>();
		for (JsonElement element : data) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject model = element.getAsJsonObject();
			JsonElement id = model.get("id");
			if (id == null || !id.isJsonPrimitive()) {
				id = model.get("name");
			}
			if (id == null || !id.isJsonPrimitive()) {
				continue;
			}
			String value = id.getAsString();
			if (backend == Backend.GEMINI) {
				if (value.startsWith("models/")) {
					value = value.substring("models/".length());
				}
				JsonArray methods = model.getAsJsonArray("supportedGenerationMethods");
				if (methods != null) {
					boolean generate = false;
					for (JsonElement method : methods) {
						if (method.isJsonPrimitive() && "generateContent".equals(method.getAsString())) {
							generate = true;
							break;
						}
					}
					if (!generate) {
						continue;
					}
				}
				if (value.contains("embedding")) {
					continue;
				}
			}
			if (value.endsWith(":batch")) {
				continue;
			}
			ids.add(value);
		}
		List<String> models = new ArrayList<>(ids);
		models.sort(String.CASE_INSENSITIVE_ORDER);
		return List.copyOf(models);
	}

	public static String test(ChatAISumConfig config) throws IOException, InterruptedException {
		ChatAISumConfig cfg = config == null ? ChatAISumConfig.get() : config;
		Provider provider = Provider.of(cfg);
		String system = "You are a connection test. Reply with exactly: OK";
		String user = "ping";
		Duration timeout = Duration.ofMillis(Math.min(cfg.timeoutMs, 60000));
		String text = requestWithFallback(provider, cfg, timeout, system, user, 32);
		return provider.backend().id() + " " + provider.model() + " -> " + text;
	}

	private static String requestGemini(Provider provider, ChatAISumConfig cfg, Duration timeout,
			String system, String user, int maxTokens) throws IOException, InterruptedException {
		String model = provider.model();
		if (model.startsWith("models/")) {
			model = model.substring("models/".length());
		}
		String endpoint = provider.base() + "/models/" + model + ":generateContent";

		JsonObject systemPart = new JsonObject();
		systemPart.addProperty("text", system);
		JsonArray systemParts = new JsonArray();
		systemParts.add(systemPart);
		JsonObject systemInstruction = new JsonObject();
		systemInstruction.add("parts", systemParts);

		JsonObject userPart = new JsonObject();
		userPart.addProperty("text", user);
		JsonArray userParts = new JsonArray();
		userParts.add(userPart);
		JsonObject content = new JsonObject();
		content.addProperty("role", "user");
		content.add("parts", userParts);
		JsonArray contents = new JsonArray();
		contents.add(content);

		JsonObject body = new JsonObject();
		body.add("systemInstruction", systemInstruction);
		body.add("contents", contents);

		int tokens = maxTokens;
		while (true) {
			JsonObject generation = new JsonObject();
			generation.addProperty("maxOutputTokens", tokens);
			if (supportsTemperature(provider.model())) {
				generation.addProperty("temperature", cfg.temperature);
			}
			JsonObject requestBody = body.deepCopy();
			requestBody.add("generationConfig", generation);

			HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
					.timeout(timeout)
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.header("User-Agent", USER_AGENT)
					.header("x-goog-api-key", provider.key())
					.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody), StandardCharsets.UTF_8));
			HttpResponse<String> response = CLIENT.send(request.build(),
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				throw new IOException(provider.backend().id() + " generateContent returned HTTP "
						+ response.statusCode() + ": " + trim(response.body()));
			}
			JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
			String text = extractGeminiText(root);
			if (text != null && !text.isBlank()) {
				return text.strip();
			}
			boolean truncated = "MAX_TOKENS".equals(geminiFinishReason(root));
			if (!truncated || tokens >= 16000) {
				throw new IOException(provider.backend().id()
						+ " generateContent returned an empty summary (maxOutputTokens=" + tokens + ")");
			}
			tokens = Math.min(tokens * 2, 16000);
		}
	}

	private static String extractGeminiText(JsonObject root) {
		JsonArray candidates = root == null ? null : root.getAsJsonArray("candidates");
		if (candidates == null || candidates.isEmpty() || !candidates.get(0).isJsonObject()) {
			return null;
		}
		JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
		if (content == null) {
			return null;
		}
		JsonArray parts = content.getAsJsonArray("parts");
		if (parts == null) {
			return null;
		}
		StringBuilder text = new StringBuilder();
		for (JsonElement part : parts) {
			if (!part.isJsonObject()) {
				continue;
			}
			JsonElement value = part.getAsJsonObject().get("text");
			if (value != null && value.isJsonPrimitive()) {
				if (text.length() > 0) {
					text.append('\n');
				}
				text.append(value.getAsString());
			}
		}
		return text.toString();
	}

	private static String geminiFinishReason(JsonObject root) {
		JsonArray candidates = root == null ? null : root.getAsJsonArray("candidates");
		if (candidates == null || candidates.isEmpty() || !candidates.get(0).isJsonObject()) {
			return null;
		}
		return string(candidates.get(0).getAsJsonObject(), "finishReason");
	}

	private static String requestAnthropicMessages(Provider provider, Duration timeout,
			String system, String user, int maxTokens) throws IOException, InterruptedException {
		String endpoint = append(provider.base(), "/messages");
		JsonObject body = new JsonObject();
		body.addProperty("model", provider.model());
		body.addProperty("system", system);

		JsonArray messages = new JsonArray();
		JsonObject message = new JsonObject();
		message.addProperty("role", "user");
		message.addProperty("content", user);
		messages.add(message);
		body.add("messages", messages);

		int tokens = maxTokens;
		while (true) {
			body.addProperty("max_tokens", tokens);
			HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
					.timeout(timeout)
					.header("Content-Type", "application/json")
					.header("Accept", "application/json")
					.header("User-Agent", USER_AGENT)
					.header("anthropic-version", "2023-06-01")
					.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
			if (provider.key() != null && !provider.key().isBlank()) {
				request.header("x-api-key", provider.key());
			}

			HttpResponse<String> response = CLIENT.send(request.build(),
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				throw new IOException(provider.backend().id() + " /messages returned HTTP "
						+ response.statusCode() + ": " + trim(response.body()));
			}
			JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
			String text = extractAnthropicText(root);
			if (text != null && !text.isBlank()) {
				return text.strip();
			}
			boolean truncated = "max_tokens".equals(string(root, "stop_reason"));
			if (!truncated || tokens >= 16000) {
				throw new IOException(provider.backend().id()
						+ " /messages returned an empty summary (max_tokens=" + tokens + ")");
			}
			tokens = Math.min(tokens * 2, 16000);
		}
	}

	private static String extractAnthropicText(JsonObject root) {
		if (root == null) {
			return null;
		}
		JsonArray content = root.getAsJsonArray("content");
		if (content == null) {
			return null;
		}
		StringBuilder text = new StringBuilder();
		for (JsonElement part : content) {
			if (!part.isJsonObject()) {
				continue;
			}
			JsonObject object = part.getAsJsonObject();
			JsonElement type = object.get("type");
			JsonElement value = object.get("text");
			if (type != null && "text".equals(type.getAsString())
					&& value != null && value.isJsonPrimitive()) {
				if (text.length() > 0) {
					text.append('\n');
				}
				text.append(value.getAsString());
			}
		}
		return text.toString();
	}

	private static String requestResponses(Provider provider, ChatAISumConfig cfg, Duration timeout,
			String system, String user, int maxTokens) throws IOException, InterruptedException {
		String endpoint = append(provider.base(), "/responses");
		JsonObject body = new JsonObject();
		body.addProperty("model", provider.model());
		body.addProperty("instructions", system);
		body.addProperty("input", user);

		int tokens = maxTokens;
		while (true) {
			body.addProperty("max_output_tokens", tokens);
			HttpResponse<String> response = send(provider, endpoint, body, timeout);
			if (response.statusCode() != 200) {
				throw new IOException(provider.backend().id() + " /responses returned HTTP "
						+ response.statusCode() + ": " + trim(response.body()));
			}
			JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
			String text = extractResponsesText(root);
			if (text != null && !text.isBlank()) {
				return text.strip();
			}
			boolean truncated = root != null && "incomplete".equals(string(root, "status"));
			if (!truncated || tokens >= 16000) {
				throw new IOException(provider.backend().id()
						+ " /responses returned an empty summary (max_output_tokens=" + tokens + ")");
			}
			tokens = Math.min(tokens * 2, 16000);
		}
	}

	private static String requestChatCompletions(Provider provider, ChatAISumConfig cfg, Duration timeout,
			String system, String user, int maxTokens) throws IOException, InterruptedException {
		String endpoint = append(provider.base(), "/chat/completions");
		JsonObject body = new JsonObject();
		body.addProperty("model", provider.model());
		if (supportsTemperature(provider.model())) {
			body.addProperty("temperature", cfg.temperature);
		}

		JsonArray messages = new JsonArray();
		messages.add(message("system", system));
		messages.add(message("user", user));
		body.add("messages", messages);

		int tokens = maxTokens;
		while (true) {
			body.addProperty("max_tokens", tokens);
			HttpResponse<String> response = send(provider, endpoint, body, timeout);
			if (response.statusCode() != 200) {
				throw new IOException(provider.backend().id() + " /chat/completions returned HTTP "
						+ response.statusCode() + ": " + trim(response.body()));
			}
			JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
			String text = extractChatText(root);
			if (text != null && !text.isBlank()) {
				return text.strip();
			}
			boolean truncated = "length".equals(chatFinishReason(root));
			if (!truncated || tokens >= 16000) {
				throw new IOException(provider.backend().id()
						+ " /chat/completions returned an empty summary (max_tokens=" + tokens + ")");
			}
			tokens = Math.min(tokens * 2, 16000);
		}
	}

	private static HttpResponse<String> send(Provider provider, String endpoint, JsonObject body, Duration timeout)
			throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(endpoint))
				.timeout(timeout)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("User-Agent", USER_AGENT)
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
		if (provider.key() != null && !provider.key().isBlank()) {
			request.header("Authorization", "Bearer " + provider.key());
		}
		if (provider.backend() == Backend.OPENROUTER) {
			request.header("HTTP-Referer", "https://github.com/Taikin12/chataisum");
			request.header("X-Title", "chatAIsum");
		}
		return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	private static String append(String base, String path) {
		if (base.endsWith(path)) {
			return base;
		}
		return base + path;
	}

	private static boolean supportsTemperature(String model) {
		String id = model.toLowerCase(Locale.ROOT);
		return !(id.startsWith("gpt-5") || id.startsWith("gpt-6") || id.startsWith("o1")
				|| id.startsWith("o3") || id.startsWith("o4"));
	}

	private static JsonObject message(String role, String content) {
		JsonObject message = new JsonObject();
		message.addProperty("role", role);
		message.addProperty("content", content);
		return message;
	}

	private static String chatFinishReason(JsonObject root) {
		JsonArray choices = root == null ? null : root.getAsJsonArray("choices");
		if (choices == null || choices.isEmpty() || !choices.get(0).isJsonObject()) {
			return null;
		}
		return string(choices.get(0).getAsJsonObject(), "finish_reason");
	}

	private static String string(JsonObject object, String field) {
		if (object == null) {
			return null;
		}
		JsonElement value = object.get(field);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static String extractChatText(JsonObject root) {
		JsonArray choices = root == null ? null : root.getAsJsonArray("choices");
		if (choices == null || choices.isEmpty()) {
			return null;
		}
		JsonElement first = choices.get(0);
		if (!first.isJsonObject()) {
			return null;
		}
		JsonObject message = first.getAsJsonObject().getAsJsonObject("message");
		if (message == null) {
			return null;
		}
		JsonElement content = message.get("content");
		if (content == null || !content.isJsonPrimitive()) {
			return null;
		}
		return content.getAsString();
	}

	private static String extractResponsesText(JsonObject root) {
		if (root == null) {
			return null;
		}
		JsonArray output = root.getAsJsonArray("output");
		if (output == null) {
			return null;
		}
		StringBuilder text = new StringBuilder();
		for (JsonElement item : output) {
			if (!item.isJsonObject()) {
				continue;
			}
			JsonObject object = item.getAsJsonObject();
			JsonArray content = object.getAsJsonArray("content");
			if (content == null) {
				continue;
			}
			for (JsonElement part : content) {
				if (!part.isJsonObject()) {
					continue;
				}
				JsonObject partObject = part.getAsJsonObject();
				JsonElement type = partObject.get("type");
				JsonElement value = partObject.get("text");
				if (type != null && "output_text".equals(type.getAsString())
						&& value != null && value.isJsonPrimitive()) {
					if (text.length() > 0) {
						text.append('\n');
					}
					text.append(value.getAsString());
				}
			}
		}
		return text.toString();
	}

	private static String trim(String body) {
		if (body == null) {
			return "";
		}
		String value = body.strip().replaceAll("\\s+", " ");
		return value.length() > 300 ? value.substring(0, 300) + "..." : value;
	}

	private record Provider(Backend backend, String base, String key, String model) {
		static Provider of(ChatAISumConfig cfg) throws IOException, InterruptedException {
			String key;
			String model;
			String base;
			switch (cfg.backend) {
				case OPENAI -> {
					base = cfg.openaiUrl;
					key = cfg.resolveOpenAiKey();
					model = cfg.openaiModel;
				}
				case OPENROUTER -> {
					base = cfg.openrouterUrl;
					key = cfg.resolveOpenRouterKey();
					model = cfg.openrouterModel;
				}
				case OPENCODE -> {
					base = cfg.opencodeUrl;
					key = cfg.resolveOpenCodeKey();
					model = cfg.opencodeModel;
				}
				case COMPATIBLE -> {
					base = cfg.compatibleUrl;
					key = cfg.resolveCompatibleKey();
					model = cfg.compatibleModel;
				}
				case GEMINI -> {
					base = cfg.geminiUrl;
					key = cfg.resolveGeminiKey();
					model = cfg.geminiModel;
				}
				default -> throw new IOException("unknown backend");
			}
			String envName = switch (cfg.backend) {
				case OPENAI -> "OPENAI_API_KEY";
				case OPENROUTER -> "OPENROUTER_API_KEY";
				case OPENCODE -> "OPENCODE_API_KEY";
				case COMPATIBLE -> "OPENAI_COMPATIBLE_API_KEY";
				case GEMINI -> "GEMINI_API_KEY";
			};
			if (cfg.backend != Backend.COMPATIBLE && (key == null || key.isBlank())) {
				throw new IOException(envName + " が未設定です"
						+ "（config/chataisum.json か環境変数）");
			}
			if (model == null || model.isBlank()) {
				if (cfg.backend == Backend.COMPATIBLE) {
					List<String> models = listModels(Backend.COMPATIBLE, cfg);
					if (models.isEmpty()) {
						throw new IOException("互換APIのモデルが見つかりません（/chataisum models で確認）");
					}
					model = models.get(0);
				} else {
					throw new IOException(cfg.backend.id() + " のモデルが未設定です（/chataisum model <model>）");
				}
			}
			ChatAISumMod.LOGGER.debug("chatAIsum request: backend={} base={} model={}",
					cfg.backend.id(), base, model);
			return new Provider(cfg.backend, base, key, model);
		}
	}
}
