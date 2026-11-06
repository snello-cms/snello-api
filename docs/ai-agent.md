# AI Agent

Snello includes an AI assistant exposed by the backend chat endpoint:

- POST /api/chat

The assistant is implemented with Quarkus LangChain4j and can use CMS tools
to read metadata, list records and suggest create/update payloads.

## Provider Configuration

Both providers can be configured at the same time in
src/main/resources/application.properties:

- OpenAI
- Gemini

Relevant keys:

- snello.aitype
- quarkus.langchain4j.chat-model.provider=${snello.aitype:openai}
- quarkus.langchain4j.openai.*
- quarkus.langchain4j.ai.gemini.*

Environment variables:

- OPENAI_API_KEY
- GEMINI_API_KEY

## Switch Between Models

Switch is controlled by one property:

- snello.aitype=openai -> uses OpenAI
- snello.aitype=ai-gemini -> uses Gemini

After changing provider, restart the application.

Example:

```bash
# OpenAI
./mvnw quarkus:dev -Dsnello.aitype=openai

# Gemini
./mvnw quarkus:dev -Dsnello.aitype=ai-gemini
```

If you run in Docker or Kubernetes, pass the same property as an environment
variable or JVM system property and keep both API keys available.
