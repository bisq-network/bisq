package io.bitsquare.msg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * That facade delivers messaging functionality from an external library -> to be defined...
 * The external library codebase must not be used outside that facade.
 * That way a change of the library will only affect that class.
 */
public class MessageFacade
{
    private static final Logger log = LoggerFactory.getLogger(MessageFacade.class);

    public void broadcast(Message message)
    {
        log.info(message.toString());
    }

    public void send(Message message, String receiverPubKey)
    {
        log.info(message.toString() + "/" + receiverPubKey);
    }

    public void registerListener(String listenerPubKey)
    {
        log.info(listenerPubKey);
    }

}
