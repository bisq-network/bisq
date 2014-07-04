package io.bitsquare.trade.protocol.tasks.offerer;

import com.google.bitcoin.core.Utils;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.messages.offerer.DepositTxPublishedMessage;
import io.bitsquare.trade.protocol.tasks.FaultHandler;
import io.bitsquare.trade.protocol.tasks.ResultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendDepositTxIdToTaker
{
    private static final Logger log = LoggerFactory.getLogger(SendDepositTxIdToTaker.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, PeerAddress peerAddress, MessageFacade messageFacade, Trade trade)
    {
        DepositTxPublishedMessage tradeMessage = new DepositTxPublishedMessage(trade.getId(), Utils.bytesToHexString(trade.getDepositTransaction().bitcoinSerialize()));
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
                log.error("DepositTxPublishedMessage faultHandler.onFault to arrive at peer");
                faultHandler.onFault(new Exception("DepositTxPublishedMessage faultHandler.onFault to arrive at peer"));
            }
        });
    }

}
