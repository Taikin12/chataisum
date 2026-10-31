package com.taikin.chataisum.client;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class ChatAISumConfigScreen {
	private ChatAISumConfigScreen() {
	}

	public static Screen create(Screen parent) {
		ChatAISumConfig config = ChatAISumConfig.get();
		ModelCatalog.refreshAll();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.literal("chatAIsum"))
				.setSavingRunnable(config::save);
		ConfigEntryBuilder eb = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Component.literal("一般"));
		general.addEntry(eb.startBooleanToggle(Component.literal("有効"), config.enabled)
				.setDefaultValue(true)
				.setSaveConsumer(v -> config.enabled = v)
				.setTooltip(Component.literal("MOD全体の有効/無効。"))
				.build());
		general.addEntry(eb.startBooleanToggle(Component.literal("システムメッセージを含める"), config.includeSystem)
				.setDefaultValue(true)
				.setSaveConsumer(v -> config.includeSystem = v)
				.setTooltip(Component.literal("プレイヤーチャットに加えて、サーバー告知や参加退出なども要約対象にします。"))
				.build());
		general.addEntry(eb.startStrField(Component.literal("要約の言語"), config.language)
				.setDefaultValue("Japanese (日本語)")
				.setSaveConsumer(v -> config.language = v == null ? "" : v.trim())
				.setTooltip(Component.literal("LLMに指定する出力言語。例: Japanese (日本語), English"))
				.build());
		general.addEntry(eb.startStrField(Component.literal("追加の指示"), config.instruction)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.instruction = v == null ? "" : v.trim())
				.setTooltip(Component.literal("毎回の要約に追加する指示。空でOK。"))
				.build());
		general.addEntry(eb.startIntSlider(Component.literal("チャット表示の最大行数"), config.maxLines, 1, 64)
				.setDefaultValue(12)
				.setSaveConsumer(v -> config.maxLines = v)
				.build());
		general.addEntry(eb.startIntField(Component.literal("最大メッセージ数"), config.maxMessages)
				.setDefaultValue(800)
				.setMin(1)
				.setMax(4000)
				.setSaveConsumer(v -> config.maxMessages = v)
				.setTooltip(Component.literal("1回の要約でLLMに渡す最大メッセージ数。"))
				.build());
		general.addEntry(eb.startIntField(Component.literal("最大文字数"), config.maxChars)
				.setDefaultValue(12000)
				.setMin(1000)
				.setMax(200000)
				.setSaveConsumer(v -> config.maxChars = v)
				.setTooltip(Component.literal("1回の要約でLLMに渡す最大文字数。"))
				.build());

		ConfigCategory prompt = builder.getOrCreateCategory(Component.literal("プロンプト"));
		prompt.addEntry(eb.startTextDescription(Component.literal(
				"空欄にすると組み込みの既定プロンプトを使います。"
						+ "使用可能: {language} {minutes} {count} {truncated} {focus} {instruction} {log}"))
				.build());
		prompt.addEntry(eb.startTextField(Component.literal("システムプロンプト"), config.systemPrompt)
				.setDefaultValue(ChatPrompt.DEFAULT_SYSTEM)
				.setSaveConsumer(v -> config.systemPrompt = v == null ? "" : v)
				.setTooltip(Component.literal("モデルへの役割・出力形式の指示。{language} を出力言語に置換します。"))
				.build());
		prompt.addEntry(eb.startTextField(Component.literal("ユーザープロンプト"), config.userPrompt)
				.setDefaultValue(ChatPrompt.DEFAULT_USER)
				.setSaveConsumer(v -> config.userPrompt = v == null ? "" : v)
				.setTooltip(Component.literal("チャットログを渡す側のテンプレート。"
						+ "{minutes} {count} {truncated} {focus} {instruction} {log} が使えます。"))
				.build());

		ConfigCategory backend = builder.getOrCreateCategory(Component.literal("バックエンド"));
		backend.addEntry(eb.startTextDescription(Component.literal(
				"モデル欄は候補から選択、または直接入力できます。候補は自動取得され、"
						+ "/chataisum models <検索語> でも確認できます。"))
				.build());
		backend.addEntry(new ConnectionTestEntry(Component.literal("接続確認")));
		backend.addEntry(eb.startEnumSelector(Component.literal("使用するバックエンド"), Backend.class, config.backend)
				.setDefaultValue(Backend.OPENAI)
				.setEnumNameProvider(e -> Component.literal(((Backend) e).id()))
				.setSaveConsumer(v -> config.backend = v)
				.setTooltip(Component.literal("openai / openrouter / opencode (Zen API) から選択。"))
				.build());

		backend.addEntry(eb.startStrField(Component.literal("OpenAI URL"), config.openaiUrl)
				.setDefaultValue("https://api.openai.com/v1")
				.setSaveConsumer(v -> config.openaiUrl = v == null ? "" : v.trim())
				.build());
		backend.addEntry(eb.startStrField(Component.literal("OpenAI API キー"), config.openaiApiKey)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.openaiApiKey = v == null ? "" : v.trim())
				.setTooltip(Component.literal("空なら環境変数 OPENAI_API_KEY を使用。"))
				.build());
		backend.addEntry(eb.startStringDropdownMenu(Component.literal("OpenAI モデル"), config.openaiModel)
				.setSelections(candidates(Backend.OPENAI, config.openaiModel))
				.setSuggestionMode(true)
				.setDefaultValue("gpt-6-luna")
				.setSaveConsumer(v -> config.openaiModel = v == null ? "" : v.trim())
				.setTooltip(Component.literal("例: gpt-6-luna"))
				.build());

		backend.addEntry(eb.startStrField(Component.literal("OpenRouter URL"), config.openrouterUrl)
				.setDefaultValue("https://openrouter.ai/api/v1")
				.setSaveConsumer(v -> config.openrouterUrl = v == null ? "" : v.trim())
				.build());
		backend.addEntry(eb.startStrField(Component.literal("OpenRouter API キー"), config.openrouterApiKey)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.openrouterApiKey = v == null ? "" : v.trim())
				.setTooltip(Component.literal("空なら環境変数 OPENROUTER_API_KEY を使用。"))
				.build());
		backend.addEntry(eb.startStringDropdownMenu(Component.literal("OpenRouter モデル"), config.openrouterModel)
				.setSelections(candidates(Backend.OPENROUTER, config.openrouterModel))
				.setSuggestionMode(true)
				.setDefaultValue("openai/gpt-6-luna")
				.setSaveConsumer(v -> config.openrouterModel = v == null ? "" : v.trim())
				.setTooltip(Component.literal("例: openai/gpt-6-luna"))
				.build());

		backend.addEntry(eb.startStrField(Component.literal("opencode (Zen) URL"), config.opencodeUrl)
				.setDefaultValue("https://opencode.ai/zen/v1")
				.setSaveConsumer(v -> config.opencodeUrl = v == null ? "" : v.trim())
				.setTooltip(Component.literal("OpenCode Zen の OpenAI 互換 API。"))
				.build());
		backend.addEntry(eb.startStrField(Component.literal("opencode API キー"), config.opencodeApiKey)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.opencodeApiKey = v == null ? "" : v.trim())
				.setTooltip(Component.literal("空なら環境変数 OPENCODE_API_KEY を使用。"))
				.build());
		backend.addEntry(eb.startStringDropdownMenu(Component.literal("opencode モデル"), config.opencodeModel)
				.setSelections(candidates(Backend.OPENCODE, config.opencodeModel))
				.setSuggestionMode(true)
				.setDefaultValue("gpt-6-luna")
				.setSaveConsumer(v -> config.opencodeModel = v == null ? "" : v.trim())
				.setTooltip(Component.literal("例: gpt-6-luna"))
				.build());

		backend.addEntry(eb.startStrField(Component.literal("Gemini URL"), config.geminiUrl)
				.setDefaultValue("https://generativelanguage.googleapis.com/v1beta")
				.setSaveConsumer(v -> config.geminiUrl = v == null ? "" : v.trim())
				.build());
		backend.addEntry(eb.startStrField(Component.literal("Gemini API キー"), config.geminiApiKey)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.geminiApiKey = v == null ? "" : v.trim())
				.setTooltip(Component.literal("空なら環境変数 GEMINI_API_KEY または GOOGLE_API_KEY を使用。"))
				.build());
		backend.addEntry(eb.startStringDropdownMenu(Component.literal("Gemini モデル"), config.geminiModel)
				.setSelections(candidates(Backend.GEMINI, config.geminiModel))
				.setSuggestionMode(true)
				.setDefaultValue("gemini-3.8-flash")
				.setSaveConsumer(v -> config.geminiModel = v == null ? "" : v.trim())
				.setTooltip(Component.literal("例: gemini-3.8-flash"))
				.build());

		backend.addEntry(eb.startStrField(Component.literal("互換API URL"), config.compatibleUrl)
				.setDefaultValue("http://127.0.0.1:1234/v1")
				.setSaveConsumer(v -> config.compatibleUrl = v == null ? "" : v.trim())
				.setTooltip(Component.literal("OpenAI 互換の任意サーバー（LM Studio, Ollama, vLLM, Groq など）。"))
				.build());
		backend.addEntry(eb.startStrField(Component.literal("互換API キー"), config.compatibleApiKey)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.compatibleApiKey = v == null ? "" : v.trim())
				.setTooltip(Component.literal("不要なら空。空なら環境変数 OPENAI_COMPATIBLE_API_KEY を使用。"))
				.build());
		backend.addEntry(eb.startStringDropdownMenu(Component.literal("互換API モデル"), config.compatibleModel)
				.setSelections(candidates(Backend.COMPATIBLE, config.compatibleModel))
				.setSuggestionMode(true)
				.setDefaultValue("")
				.setSaveConsumer(v -> config.compatibleModel = v == null ? "" : v.trim())
				.setTooltip(Component.literal("空なら /models の先頭を自動使用。"))
				.build());

		ConfigCategory request = builder.getOrCreateCategory(Component.literal("リクエスト"));
		request.addEntry(eb.startIntField(Component.literal("タイムアウト（ms）"), config.timeoutMs)
				.setDefaultValue(120000)
				.setMin(5000)
				.setMax(600000)
				.setSaveConsumer(v -> config.timeoutMs = v)
				.build());
		request.addEntry(eb.startIntField(Component.literal("最大出力トークン"), config.maxOutputTokens)
				.setDefaultValue(6000)
				.setMin(100)
				.setMax(16000)
				.setSaveConsumer(v -> config.maxOutputTokens = v)
				.build());
		request.addEntry(eb.startFloatField(Component.literal("温度 (temperature)"), (float) config.temperature)
				.setDefaultValue(0.3f)
				.setMin(0.0f)
				.setMax(2.0f)
				.setSaveConsumer(v -> config.temperature = v)
				.setTooltip(Component.literal("temperature 非対応のモデルでは送信されません。"))
				.build());

		return builder.build();
	}

	private static List<String> candidates(Backend backend, String current) {
		LinkedHashSet<String> models = new LinkedHashSet<>(ModelCatalog.get(backend));
		if (current != null && !current.isBlank()) {
			models.add(current);
		}
		return new ArrayList<>(models);
	}
}
