# chatAIsum

過去 m 分の Minecraft チャットを LLM で要約するクライアント専用 Fabric MOD です。

- 対応: Minecraft 26.3 / Fabric Loader 0.20.1 / Java 25
- 必須: Fabric API / Cloth Config
- 任意: Mod Menu（ゲーム内設定画面）
- バックエンド: **OpenAI**（既定）/ **OpenRouter** / **OpenCode Zen** / **OpenAI互換API** / **Google Gemini**
- 既定モデル: **GPT-6 Luna**（`gpt-6-luna` / OpenRouter は `openai/gpt-6-luna`）

## 使い方

```
/chataisum 10              # 過去10分を要約
/chataisum 30 建築の話     # 注目したい内容を指定
/chataisum test            # 現在のバックエンドへの接続を確認
/chataisum                  # 状態表示
/chataisum status
/chataisum on|off|toggle
/chataisum reload
```

要約結果は自分のチャット欄に表示されます（サーバーには送信されません）。
Mod Menu の「設定」からも変更できます（Cloth Config）。
バックエンド画面の「接続確認」ボタン、または `/chataisum test` で API キー・URL・モデルを実際に呼び出して確認できます。

## モデル候補の検索

モデルは各 API の `/models` から取得します。

```
/chataisum models          # 現在のバックエンドの候補を取得して表示
/chataisum models luna     # "luna" を含む候補だけ表示
```

Mod Menu のモデル欄は検索可能なドロップダウンになっています（候補は自動取得・30分キャッシュ）。

## サーバーの `/lc log` など

チャットはメモリに保持せず、実行時に `logs/latest.log` から直近 m 分を読み取ります。
`----- 名前の発言ログ -----` 〜 `----------` 形式のログ貼り付けを検出すると、
各行のタイムスタンプを復元して履歴に取り込みます（重複は自動排除）。
サーバー側のログ表示コマンドで過去ログを取得してから要約すると、その範囲も要約対象になります。

## プロンプト

Mod Menu の **プロンプト** タブ、またはコマンドで編集できます。

```
/chataisum prompt                 # 現在の設定を表示
/chataisum prompt system <text>   # システムプロンプトを変更
/chataisum prompt user <text>     # ユーザープロンプト（テンプレート）を変更
/chataisum prompt reset           # 既定に戻す
```

使用可能なプレースホルダ:

| プレースホルダ | 内容 |
| --- | --- |
| `{language}` | 設定した出力言語 |
| `{minutes}` | 要約対象の分数 |
| `{count}` | 対象メッセージ数 |
| `{truncated}` | 切り詰め時の注記（通常は空） |
| `{focus}` | `/chataisum m 注目内容` の行（空なら空） |
| `{instruction}` | 追加の指示の行（空なら空） |
| `{log}` | タイムスタンプ付きチャットログ |

空欄にすると組み込みの既定プロンプトが使われます。

## バックエンド

```
/chataisum backend openai
/chataisum backend openrouter
/chataisum backend opencode
/chataisum backend compatible
/chataisum backend gemini

/chataisum url <url>        # 現在のバックエンドの URL
/chataisum model <model>    # 現在のバックエンドのモデル
/chataisum key <key>        # 現在のバックエンドの API キー
```

| backend | 既定 URL | 既定モデル | キー |
| --- | --- | --- | --- |
| `openai` | `https://api.openai.com/v1` | `gpt-6-luna` | `openaiApiKey` / `OPENAI_API_KEY` |
| `openrouter` | `https://openrouter.ai/api/v1` | `openai/gpt-6-luna` | `openrouterApiKey` / `OPENROUTER_API_KEY` |
| `opencode` | `https://opencode.ai/zen/v1` | `gpt-6-luna` | `opencodeApiKey` / `OPENCODE_API_KEY` |
| `compatible` | `http://127.0.0.1:1234/v1` | 空なら `/models` の先頭 | `compatibleApiKey`（任意） / `OPENAI_COMPATIBLE_API_KEY` |
| `gemini` | `https://generativelanguage.googleapis.com/v1beta` | `gemini-3.8-flash` | `geminiApiKey` / `GEMINI_API_KEY`（`GOOGLE_API_KEY` も可） |

OpenAI / OpenCode は Responses API を優先し、未対応なら Chat Completions に自動フォールバックします。
OpenRouter と互換API は Chat Completions を使用します。
互換API は LM Studio / Ollama / vLLM / Groq など、OpenAI 互換の任意エンドポイントに使えます。
Gemini は Google AI Studio の `generateContent` API を使用します。

## 設定

`config/chataisum.json`（またはコマンド / Mod Menu）:

| キー | 既定 | 説明 |
| --- | --- | --- |
| `enabled` | `true` | MOD の有効/無効 |
| `backend` | `OPENAI` | `OPENAI` / `OPENROUTER` / `OPENCODE` |
| `openaiUrl` | `https://api.openai.com/v1` | OpenAI API のベース URL |
| `openaiApiKey` | `""` | OpenAI API キー（空なら `OPENAI_API_KEY`） |
| `openaiModel` | `gpt-6-luna` | OpenAI モデル |
| `openrouterUrl` | `https://openrouter.ai/api/v1` | OpenRouter のベース URL |
| `openrouterApiKey` | `""` | OpenRouter API キー（空なら `OPENROUTER_API_KEY`） |
| `openrouterModel` | `openai/gpt-6-luna` | OpenRouter モデル |
| `opencodeUrl` | `https://opencode.ai/zen/v1` | OpenCode Zen API のベース URL |
| `opencodeApiKey` | `""` | OpenCode API キー（空なら `OPENCODE_API_KEY`） |
| `opencodeModel` | `gpt-6-luna` | OpenCode モデル |
| `compatibleUrl` | `http://127.0.0.1:1234/v1` | 互換API のベース URL |
| `compatibleApiKey` | `""` | 互換API キー（任意。空なら `OPENAI_COMPATIBLE_API_KEY`） |
| `compatibleModel` | `""` | 互換API モデル（空なら `/models` の先頭を自動使用） |
| `geminiUrl` | `https://generativelanguage.googleapis.com/v1beta` | Gemini API のベース URL |
| `geminiApiKey` | `""` | Gemini API キー（空なら `GEMINI_API_KEY` / `GOOGLE_API_KEY`） |
| `geminiModel` | `gemini-3.8-flash` | Gemini モデル |
| `timeoutMs` | `120000` | HTTP タイムアウト |
| `maxOutputTokens` | `6000` | 要約の最大トークン数（足りない場合は自動で増加） |
| `temperature` | `0.3` | 生成温度（非対応モデルには送信しません） |
| `language` | `Japanese (日本語)` | 要約の言語 |
| `instruction` | `""` | 追加の要約指示（`{instruction}` に入る） |
| `systemPrompt` | 組み込み | システムプロンプト |
| `userPrompt` | 組み込み | ユーザープロンプトテンプレート |
| `maxMessages` | `800` | 1回に渡す最大メッセージ数 |
| `maxChars` | `12000` | 1回に渡す最大文字数 |
| `maxLines` | `12` | チャットに表示する最大行数 |
| `includeSystem` | `true` | システムメッセージも要約対象に含める |

旧バージョンのローカル opencode server 設定（`http://127.0.0.1:4096`）は、読み込み時に Zen API へ自動移行されます。

## ビルド

```sh
./gradlew build     # -> build/libs/chataisum-<version>.jar
```
