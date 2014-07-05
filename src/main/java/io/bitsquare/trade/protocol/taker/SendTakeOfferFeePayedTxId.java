package io.bitsquare.trade.protocol.taker;

import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.protocol.FaultHandler;
import io.bitsquare.trade.protocol.ResultHandler;
import java.math.BigInteger;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendTakeOfferFeePayedTxId
{
    private static final Logger log = LoggerFactory.getLogger(SendTakeOfferFeePayedTxId.class);

    public static void run(ResultHandler resultHandler,
                           FaultHandler faultHandler,
                           PeerAddress peerAddress,
                           MessageFacade messageFacade,
                           String tradeId,
                           String takeOfferFeeTxId,
                           BigInteger tradeAmount,
                           String pubKeyForThatTradeAsHex)
    {
        log.trace("Run task");
        TakeOfferFeePayedMessage msg = new TakeOfferFeePayedMessage(tradeId, takeOfferFeeTxId, tradeAmount, pubKeyForThatTradeAsHex);

        messageFacade.sendTradeMessage(peerAddress, msg, new OutgoingTradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("TakeOfferFeePayedMessage successfully arrived at peer");
                resultHandler.onResult();
            }

            @Override
            public void onFailed()
            {
                log.error("TakeOfferFeePayedMessage faultHandler.onFault to arrive at peer");
                faultHandler.onFault(new Exception("TakeOfferFeePayedMessage faultHandler.onFault to arrive at peer"));
            }
        });
    }
}
