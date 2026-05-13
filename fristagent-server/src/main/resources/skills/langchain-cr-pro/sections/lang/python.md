## Python LLM Stack — Deep Rules

### LangChain / LlamaIndex Specifics
- `LLMChain` / `ConversationChain` deprecated — use `LCEL` (LangChain Expression Language) pipe syntax
- `ChatOpenAI(streaming=True)` without `StreamingStdOutCallbackHandler` or async iterator
- `ConversationBufferMemory` without `max_token_limit` → unbounded growth
- `PromptTemplate` using `.format()` instead of `.from_template()` loses input validation

### Async Correctness
- `llm.predict()` / `chain.run()` inside `async def` without `await chain.arun()`
- `asyncio.run()` nested inside already-running event loop (use `nest_asyncio` or restructure)

### Security
- `f"...{user_input}..."` directly in `PromptTemplate` string → prompt injection
- Storing `OPENAI_API_KEY` in `.env` files committed to repository
- Tool functions executing `subprocess` or `eval` without authorization check
