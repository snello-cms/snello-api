# AI Agent (AG-UI)

Snello AI is the **flower-style** agent stack: authenticated WebSocket AG-UI, CDI-async agent runs (no NATS), history + skills + MCP tools, and CMS/SQL tools.

## WebSocket

- Path: `ws(s)://host/agui`
- Auth: OIDC bearer (header) or browser subprotocol `bearer-token-carrier` (see flower README pattern)
- Requires `quarkus.http.auth.proactive=false` (needed by `@BearerTokenAuthentication` on the WS endpoint)
- Protocol: AG-UI events (`SESSION_STARTED`, `RUN_*`, `TEXT_MESSAGE_*`, `TOOL_CALL_*`, `RUN_ERROR`)
- Each frame is a RunAgentInput; `forwardedProps.projectUuid` is required; `model` = `quick`|`pro`; `language` = `it`|`en`

Pipeline: `AguiServiceWss` → `Event.fireAsync(AguiRequest)` → `AguiAgentWorker` → `Event.fireAsync(AguiResponse)` → WS events.

## REST (control plane)

| Path | Role |
|------|------|
| `GET /api/ai/history`, `GET /api/ai/history/{uuid}` | User history events (scoped to token `sub`) |
| `GET /api/ai/history/sessions` | User sessions |
| `POST /api/ai/feedback` | Feedback history event |
| `GET /api/ai/me/projects` | Projects accessible to caller |
| `GET /api/ai/card-catalog?projectUuid=` | UI cards for project |
| Admin CRUD `/api/ai/projects`, `/skills`, `/mcp-tools`, `/cards`, `/service-accounts` | `Admin` |

SQL dynamic tools remain at `/api/ai_tools`.

## Persistence (system tables)

`history_session`, `history_event`, `ai_project`, `ai_skill`, `ai_mcp_tool`, `ai_ui_card`, `ai_service_account`, `ai_tools`.

## Provider configuration

- `snello.aitype=openai` \| `ai-gemini`
- Named models: `quick` / `pro` under OpenAI or Gemini (`GEMINI_MODEL_QUICK`, `GEMINI_MODEL_PRO`)
- Keys: `OPENAI_API_KEY`, `GEMINI_API_KEY`
- MCP HTTP base: `snello.ai.mcp.http.base-url` (env `SNELLO_AI_MCP_BASE_URL`)

Default project seeded as `snello.cms` when empty (see `AiProjectSeeder`).

## Flower catalog import (kayak.love)

Export from [ai.kayak.love](https://ai.kayak.love) (Admin APIs under `/api/v1/…`, camelCase) into Snello snake_case fixture, then upsert on a control/all-in-one node:

```bash
FLOWER_TOKEN='Bearer …' ./dev/scripts/export-flower-ai-catalog.sh
AIO_BASE_URL=https://api.sne.lol ./dev/scripts/import-ai-catalog.sh
AIO_BASE_URL=https://api.sne.lol ./dev/scripts/test-ai-catalog-remote.sh
```

Fixture: `dev/exports/flower-ai-kayaklove.json` (also under control-app test resources). Service accounts are skipped (flower endpoint 500; kayak.love MCP tools are unauthenticated).
