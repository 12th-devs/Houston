# On-device tool calling research (2026-10-11)

## Supported APIs

- [ML Kit GenAI Prompt API](https://developers.google.com/ml-kit/genai/prompt/android) accepts text and image plus text, and returns text. Its [structured output API](https://developers.google.com/ml-kit/genai/prompt/android/structured-output) can constrain a response to a Kotlin schema, but the published Prompt API does not expose a native function/tool call and tool-response conversation protocol. Houston retains its existing JSON prompt protocol for AICore.
- [LiteRT-LM Android](https://ai.google.dev/edge/litert-lm/android) 0.18.0 documents OpenAPI tool schemas, `ConversationConfig(automaticToolCalling = false)`, `Message.tool`, and multiple `toolCalls` in a model response. Houston uses this supported manual tool flow so tool execution stays in its existing `AiTools` executor.
- [LiteRT-LM Gemma 4](https://ai.google.dev/edge/litert-lm/models/gemma-4) supports E2B and E4B on Android. The [Gemma 4 model card](https://ai.google.dev/gemma/docs/core/model_card_4) describes native function calling. The E2B model is about 2.58 GB and E4B about 3.65 GB, so Houston requires the user to import a `.litertlm` file instead of bundling one in the APK.

## Integration

`DeviceAi` remains the AICore provider. `LiteRtAi` is an optional provider selected in AI settings. It registers schemas for the existing Houston tools, executes model calls through `AiTools.execute`, sends structured results back to the conversation, and limits sequential rounds. Tool use remains controlled by the existing AI tools switch.

The LiteRT path needs testing with an imported Gemma 4 model on a supported Android phone. An APK build verifies the library API integration, but cannot verify model inference, hardware backend compatibility, or individual notification intents on this workspace.
