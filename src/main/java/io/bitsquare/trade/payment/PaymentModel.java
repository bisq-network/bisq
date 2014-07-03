package io.bitsquare.trade.payment;

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.Trade;
import io.nucleo.scheduler.model.PropertyProviderModel;
import net.tomp2p.peers.PeerAddress;

public class PaymentModel extends PropertyProviderModel
{
    public final MessageFacade messageFacade;
    public final Offer offer;
    public PeerAddress peerAddress;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PaymentModel(MessageFacade messageFacade, Trade trade)
    {
        this.messageFacade = messageFacade;
        this.offer = trade.getOffer();
    }


}
