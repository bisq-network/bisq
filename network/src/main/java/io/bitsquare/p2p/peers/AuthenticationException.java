package io.bitsquare.p2p.peers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationException extends Exception {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationException.class);

    public AuthenticationException(String message) {
        super(message);
    }
}
