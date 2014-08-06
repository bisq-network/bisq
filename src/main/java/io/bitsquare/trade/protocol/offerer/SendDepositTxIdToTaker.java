package io.bitsquare.trade.protocol.offerer;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.handlers.ExceptionHandler;
import io.bitsquare.trade.handlers.ResultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendDepositTxIdToTaker
{
    private static final Logger log = LoggerFactory.getLogger(SendDepositTxIdToTaker.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler, PeerAddress peerAddress, MessageFacade messageFacade, String tradeId, Transaction depositTransaction)
    {
        log.trace("Run task");
        DepositTxPublishedMessage tradeMessage = new DepositTxPublishedMessage(tradeId, Utils.HEX.encode(depositTransaction.bitcoinSerialize()));
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, new OutgoingTradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("DepositTxPublishedMessage successfully arrived at peer");
                resultHandler.onResult();
            }

            @Override
            public void onFailed()
            {
                log.error("DepositTxPublishedMessage  did not arrive at peer");
                exceptionHandler.onError(new Exception("DepositTxPublishedMessage did not arrive at peer"));
            }
        });
    }

}
