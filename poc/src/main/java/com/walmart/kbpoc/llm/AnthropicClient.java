package com.walmart.kbpoc.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmart.kbpoc.util.Env;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal Anthropic Messages API client used for the "enhanced" pipeline's
 * block segmentation + entity extraction step. This is the LLM-based
 * block_source path chosen for this POC (see docs/poc-scope.md) rather than
 * a rule-based DOM classifier.
 */
public class AnthropicClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-5";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String workspaceId;

    public AnthropicClient() {
        this.apiKey = Env.require("ANTHROPIC_API_KEY");
        this.workspaceId = Env.get("ANTHROPIC_WORKSPACE_ID");
    }

    public List<LlmBlock> segment(String docType, String title, String contentHtml) {
        String prompt = buildPrompt(docType, title, contentHtml);
        String responseText = call(prompt);
        return parseBlocks(responseText);
    }

    private String buildPrompt(String docType, String title, String contentHtml) {
        return """
                You are segmenting a Walmart customer-service knowledge base article into
                typed content blocks for a retrieval system. The document type is "%s"
                titled "%s".

                Split the HTML below into an ORDERED list of blocks. Allowed block_type
                values: intro, eligibility_criteria, procedure_steps, step, exception,
                escalation, qa_pair, disclaimer. Use "step" for each individual numbered
                step inside a procedure (one block per step, preserving order). Extract
                any dollar amounts as entity_type "dollar_threshold" (value = number only,
                unit = "USD") and any day/time windows as entity_type "time_window" (value
                = number only, unit = "days"). Only attach entities to the block whose text
                actually contains them.

                Respond with ONLY a JSON array (no markdown fences, no commentary), where
                each element has this exact shape:
                {"block_type": "...", "order": 1, "heading": "...", "text": "...",
                 "entities": [{"entity_type": "...", "value": "...", "unit": "...", "confidence": 0.0}]}

                HTML:
                %s
                """.formatted(docType, title, contentHtml);
    }

    private String call(String prompt) {
        try {
            String body = mapper.writeValueAsString(new Request(MODEL, 2000,
                    List.of(new Message("user", prompt))));

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01");
            if (workspaceId != null && !workspaceId.isBlank()) {
                requestBuilder.header("anthropic-workspace-id", workspaceId);
            }
            HttpRequest request = requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body)).build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Anthropic API error " + response.statusCode() + ": " + response.body());
            }
            if (Env.get("KBPOC_DEBUG_LLM") != null) {
                System.err.println("=== FULL HTTP RESPONSE BODY ===\n" + response.body() + "\n=== END ===");
            }
            JsonNode root = mapper.readTree(response.body());
            StringBuilder text = new StringBuilder();
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
            return text.toString();
        } catch (Exception e) {
            throw new RuntimeException("Anthropic API call failed: " + e.getMessage(), e);
        }
    }

    private List<LlmBlock> parseBlocks(String responseText) {
        String json = responseText.trim();
        // Defensive: strip markdown code fences if the model added them anyway.
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```[a-zA-Z]*\\n", "").replaceFirst("```\\s*$", "");
        }
        try {
            JsonNode arr = mapper.readTree(json);
            List<LlmBlock> blocks = new ArrayList<>();
            for (JsonNode node : arr) {
                LlmBlock b = new LlmBlock();
                b.blockType = node.path("block_type").asText();
                b.order = node.path("order").asInt();
                b.heading = node.path("heading").asText("");
                b.text = node.path("text").asText("");
                for (JsonNode e : node.path("entities")) {
                    LlmBlock.LlmEntity ent = new LlmBlock.LlmEntity();
                    ent.entityType = e.path("entity_type").asText();
                    ent.value = e.path("value").asText();
                    ent.unit = e.path("unit").asText(null);
                    ent.confidence = e.path("confidence").asDouble(0.9);
                    b.entities.add(ent);
                }
                blocks.add(b);
            }
            return blocks;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM segmentation JSON:\n" + json, e);
        }
    }

    private record Request(String model, int max_tokens, List<Message> messages) {
    }

    private record Message(String role, String content) {
    }
}
