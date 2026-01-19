package com.martin.genAiAgent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Function;

@Service
@Slf4j
public class RetryService {
    
    /**
     * 带重试机制的WebClient请求
     */
    public static <T> Mono<T> executeWithRetry(
            WebClient webClient, 
            Function<WebClient, Mono<T>> requestFunction,
            String operationName) {
        
        return requestFunction.apply(webClient)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxAttempts(3)
                        .doBeforeRetry(retrySignal -> {
                            log.warn("操作 {} 失败，准备第 {} 次重试: {}", 
                                    operationName, 
                                    retrySignal.totalRetries() + 1,
                                    retrySignal.failure().getMessage());
                        })
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                            log.error("操作 {} 重试次数已用尽，最终失败: {}", 
                                    operationName, 
                                    retrySignal.failure().getMessage());
                            return new RuntimeException("操作重试失败: " + operationName, retrySignal.failure());
                        }))
                .doOnError(error -> {
                    log.error("操作 {} 最终失败: {}", operationName, error.getMessage());
                })
                .doOnSuccess(result -> {
                    log.info("操作 {} 成功完成", operationName);
                });
    }
    
    /**
     * 带重试的数据库操作
     */
    @Retryable(
            value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public <T> T executeDatabaseOperation(
            String operationName, 
            java.util.function.Supplier<T> operation) {
        try {
            log.debug("执行数据库操作: {}", operationName);
            T result = operation.get();
            log.info("数据库操作 {} 成功", operationName);
            return result;
        } catch (Exception e) {
            log.error("数据库操作 {} 失败: {}", operationName, e.getMessage());
            throw new RuntimeException("数据库操作失败: " + operationName, e);
        }
    }
    
    /**
     * 带重试的缓存操作
     */
    @Retryable(
            value = {Exception.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 500)
    )
    public <T> T executeCacheOperation(
            String operationName, 
            java.util.function.Supplier<T> operation) {
        try {
            log.debug("执行缓存操作: {}", operationName);
            T result = operation.get();
            log.debug("缓存操作 {} 成功", operationName);
            return result;
        } catch (Exception e) {
            log.error("缓存操作 {} 失败: {}", operationName, e.getMessage());
            throw new RuntimeException("缓存操作失败: " + operationName, e);
        }
    }
    
    /**
     * 带重试的外部API调用
     */
    @Retryable(
            value = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 1.5)
    )
    public <T> T executeExternalApiCall(
            String serviceName, 
            String operationName, 
            java.util.function.Supplier<T> operation) {
        try {
            log.debug("调用外部API: {} - {}", serviceName, operationName);
            T result = operation.get();
            log.info("外部API调用 {} - {} 成功", serviceName, operationName);
            return result;
        } catch (Exception e) {
            log.error("外部API调用 {} - {} 失败: {}", serviceName, operationName, e.getMessage());
            throw new RuntimeException("外部API调用失败: " + serviceName + " - " + operationName, e);
        }
    }
}
