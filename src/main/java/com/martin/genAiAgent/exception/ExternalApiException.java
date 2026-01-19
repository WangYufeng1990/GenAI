package com.martin.genAiAgent.exception;

public class ExternalApiException extends RuntimeException {
    
    private final String serviceName;
    
    public ExternalApiException(String message) {
        super(message);
        this.serviceName = "UNKNOWN";
    }
    
    public ExternalApiException(String message, String serviceName) {
        super(message);
        this.serviceName = serviceName;
    }
    
    public ExternalApiException(String message, String serviceName, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
    }
    
    public String getServiceName() {
        return serviceName;
    }
}
