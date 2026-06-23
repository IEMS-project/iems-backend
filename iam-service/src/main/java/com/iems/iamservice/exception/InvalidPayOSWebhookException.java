package com.iems.iamservice.exception;

public class InvalidPayOSWebhookException extends RuntimeException {
    public InvalidPayOSWebhookException(String message, Throwable cause) {
        super(message, cause);
    }
}
