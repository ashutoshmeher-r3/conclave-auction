package com.r3.conclave.sample.auction.common;

public class InvalidUserException extends RuntimeException{

    private String message;

    public InvalidUserException(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
