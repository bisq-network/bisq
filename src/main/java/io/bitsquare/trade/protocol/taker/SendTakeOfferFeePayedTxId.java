package io.bitsquare.trade.protocol.taker;

import com.google.bitcoin.core.Coin;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.handlers.ExceptionHandler;
import io.bitsquare.trade.handlers.ResultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendTakeOfferFeePayedTxId
{
    private static final Logger log = LoggerFactory.getLogger(SendTakeOfferFeePayedTxId.class);

    public static void run(ResultHandler resultHandler,
                           ExceptionHandler exceptionHandler,
                           PeerAddress peerAddress,
                           MessageFacade messageFacade,
                           String tradeId,
                           String takeOfferFeeTxId,
                           Coin tradeAmount,
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
                log.error("TakeOfferFeePayedMessage  did not arrive at peer");
                exceptionHandler.onError(new Exception("TakeOfferFeePayedMessage did not arrive at peer"));
            }
        });
    }
}
