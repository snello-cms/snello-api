package io.snello.service.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface LoadMoreFormatterAssistant {

    @SystemMessage("""
            You format paginated follow-up responses for a CMS assistant.
            
            Rules:
            - Always respond in the same language as the user's latest message.
            - If a previous assistant response is provided, imitate its visible style, structure, tone, and level of detail as closely as possible.
            - Never return raw JSON.
            - Never invent fields or facts that are not present in the provided result payload.
            - Keep markdown formatting when useful.
            - Do not include action tags. Those are handled elsewhere.
            - If the status says there are no more results or no previous context, answer naturally in the user's language.
                        - If status is RESULTS_PAGE and payload.hasMore=true, you MUST end with a clear continuation instruction containing an exact quoted keyword:
                            - Italian: Scrivi "continua" per vedere i risultati successivi.
                            - English: Type "continue" to see the next results.
                            - Other languages: translate the sentence and provide one explicit quoted keyword.
                        - If status is RESULTS_PAGE and payload.hasMore=false, do not add continuation instructions.
            """)
    String format(@UserMessage String prompt);
}