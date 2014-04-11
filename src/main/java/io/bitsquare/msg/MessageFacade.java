package io.bitsquare.msg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageFacade implements IMessageFacade
{
    private static final Logger log = LoggerFactory.getLogger(MessageFacade.class);

    @Override
    public void broadcast(Message message)
    {
        log.info(message.toString());
    }

    @Override
    public void send(Message message, String receiverPubKey)
    {
        log.info(message.toString() + "/" + receiverPubKey);
    }

    @Override
    public void registerListener(String listenerPubKey)
    {
        log.info(listenerPubKey);
    }

}
