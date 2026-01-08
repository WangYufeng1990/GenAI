package com.martin.genAiAgent.controller;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

@RestController
public class ChatController {

    private final WebClient webClient = WebClient.create("http://localhost:11434");
    private final ChatModel chatModel;

    public ChatController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public Flux<String> stream(@RequestParam String message) {
        // Directly construct request to Ollama official API
        // Use /api/chat endpoint, which returns thinking process in message.thinking field
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-r1:8b");
        requestBody.put("stream", true);
        
        // Construct message list (/api/chat endpoint requires messages array)
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", message);
        requestBody.put("messages", java.util.Arrays.asList(userMessage));
        
        // Used to track whether thinking start tag has been output
        AtomicBoolean thinkingStarted = new AtomicBoolean(false);
        AtomicBoolean thinkingEnded = new AtomicBoolean(false);
        
        return webClient.post()
                .uri("/api/chat")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(Map.class) // Map each line of JSON to Map
                .map(map -> {
                    StringBuilder output = new StringBuilder();
                    
                    // /api/chat endpoint returns message object containing role, content and thinking
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageObj = (Map<String, Object>) map.get("message");
                    if (messageObj != null) {
                        // Check if thinking field exists
                        String thinking = (String) messageObj.get("thinking");
                        if (thinking != null && !thinking.isEmpty()) {
                            // If first time encountering thinking, output start tag
                            if (!thinkingStarted.get()) {
                                output.append("<think>");
                                thinkingStarted.set(true);
                            }
                            output.append(thinking);
                        }
                        
                        // Check if content field exists
                        String content = (String) messageObj.get("content");
                        if (content != null && !content.isEmpty()) {
                            // If thinking has started but not ended, end thinking tag first
                            if (thinkingStarted.get() && !thinkingEnded.get()) {
                                output.append("</think>");
                                thinkingEnded.set(true);
                            }
                            output.append(content);
                        }
                    }
                    
                    return output.toString();
                });
    }

    @GetMapping(value = "/stream-simple", produces = "text/event-stream")
    public Flux<String> streamSimple(@RequestParam String message) {
        // Simple version: only return content, without thinking process (using WebClient)
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-r1:8b");
        requestBody.put("stream", true);
        
        // Construct message list
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", message);
        requestBody.put("messages", java.util.Arrays.asList(userMessage));
        
        return webClient.post()
                .uri("/api/chat")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(Map.class)
                .map(map -> {
                    // Only get content field, ignore thinking
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageObj = (Map<String, Object>) map.get("message");
                    if (messageObj != null) {
                        String content = (String) messageObj.get("content");
                        return content != null ? content : "";
                    }
                    return "";
                });
    }

    @GetMapping(value = "/stream-ai", produces = "text/event-stream")
    public Flux<String> streamAi(@RequestParam String message) {
        // Use Spring AI's ChatModel for simplified implementation
        // Only return content, without thinking process
        return chatModel.stream(new org.springframework.ai.chat.prompt.Prompt(List.of(new UserMessage(message))))
                .map(response -> {
                    // Extract content from ChatResponse
                    return response.getResult().getOutput().getContent();
                });
    }
}