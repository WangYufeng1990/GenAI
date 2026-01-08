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
        // 直接构造请求给 Ollama 官方接口
        // 使用 /api/chat 端点，该端点会在 message.thinking 字段中返回 thinking 过程
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-r1:8b");
        requestBody.put("stream", true);
        
        // 构造消息列表（/api/chat 端点需要 messages 数组）
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", message);
        requestBody.put("messages", java.util.Arrays.asList(userMessage));
        
        // 用于跟踪是否已经输出了 thinking 开始标签
        AtomicBoolean thinkingStarted = new AtomicBoolean(false);
        AtomicBoolean thinkingEnded = new AtomicBoolean(false);
        
        return webClient.post()
                .uri("/api/chat")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(Map.class) // 将每一行 JSON 映射为 Map
                .map(map -> {
                    StringBuilder output = new StringBuilder();
                    
                    // /api/chat 端点返回的是 message 对象，包含 role、content 和 thinking
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageObj = (Map<String, Object>) map.get("message");
                    if (messageObj != null) {
                        // 检查是否有 thinking 字段
                        String thinking = (String) messageObj.get("thinking");
                        if (thinking != null && !thinking.isEmpty()) {
                            // 如果是第一次遇到 thinking，输出开始标签
                            if (!thinkingStarted.get()) {
                                output.append("<think>");
                                thinkingStarted.set(true);
                            }
                            output.append(thinking);
                        }
                        
                        // 检查是否有 content 字段
                        String content = (String) messageObj.get("content");
                        if (content != null && !content.isEmpty()) {
                            // 如果 thinking 已经开始但还没结束，先结束 thinking 标签
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
        // 简单版本：只返回 content，不包含 thinking 过程（使用 WebClient）
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-r1:8b");
        requestBody.put("stream", true);
        
        // 构造消息列表
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
                    // 只获取 content 字段，忽略 thinking
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
        // 使用 Spring AI 的 ChatModel 简化实现
        // 只返回 content，不包含 thinking 过程
        return chatModel.stream(new org.springframework.ai.chat.prompt.Prompt(List.of(new UserMessage(message))))
                .map(response -> {
                    // 从 ChatResponse 中提取 content
                    return response.getResult().getOutput().getContent();
                });
    }
}