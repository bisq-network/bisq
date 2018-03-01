package io.bisq.api;

public class NoPaymentAccountException extends Exception {

    public NoPaymentAccountException(String message) {
        super(message);
    }
}

