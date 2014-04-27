package io.bitsquare.msg;

import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trade;
import io.bitsquare.util.Utils;

public class Message
{
    public final static String BROADCAST_NEW_OFFER = "BROADCAST_NEW_OFFER";
    public final static String REQUEST_TAKE_OFFER = "REQUEST_TAKE_OFFER";
    public final static String OFFER_ACCEPTED = "OFFER_ACCEPTED";
    public final static String REQUEST_OFFER_FEE_PAYMENT_CONFIRM = "REQUEST_OFFER_FEE_PAYMENT_CONFIRM";
    public final static String SEND_SIGNED_CONTRACT = "SEND_SIGNED_CONTRACT";

    private String type;
    private Object payload;


    public Message(String type, String msg)
    {
        this.type = type;
        this.payload = msg;
    }

    public Message(String type, Trade trade)
    {
        this.type = type;
        this.payload = trade;
    }

    public Message(String type, Offer offer)
    {
        this.type = type;
        this.payload = offer;
    }

    public Message(String type, Contract contract)
    {
        this.type = type;
        this.payload = contract;
    }

    public String getType()
    {
        return type;
    }


    public String toString()
    {
        return type + ": " + Utils.convertToJson(payload);
    }


}
