package com.aspect.exception;

public class NoMessagesToHandleException extends MessageHandlerException {
    public NoMessagesToHandleException(String message){
        super(message);
    }
}
