## JavaScript / TypeScript LLM Stack — Deep Rules

### LangChain.js Specifics
- `new OpenAI()` without `timeout` / `maxRetries` options
- `ConversationChain` without `maxTokenLimit` in memory
- Streaming: missing error handler on `stream.on('error', ...)`

### Async Correctness
- `chain.call()` not awaited in async function
- Unhandled promise rejection on `openai.chat.completions.create()`
- Missing `AbortController` for cancellable LLM requests in server-side code

### Security
- API keys in frontend bundle (must be server-side only)
- Template literal prompt construction with user input without sanitization
