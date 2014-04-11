package io.bitsquare.msg;

/**
 * Gateway to messaging
 */
public interface IMessageFacade
{
    void broadcast(Message message);

    void send(Message message, String receiverMsgID);

    void registerListener(String listenerPubKey);
}
