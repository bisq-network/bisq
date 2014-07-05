package io.bitsquare.trade.protocol.offerer;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.protocol.FaultHandler;
import io.bitsquare.trade.protocol.ResultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestTakerDepositPayment
{
    private static final Logger log = LoggerFactory.getLogger(RequestTakerDepositPayment.class);

    public static void run(ResultHandler resultHandler,
                           FaultHandler faultHandler,
                           PeerAddress peerAddress,
                           MessageFacade messageFacade,
                           String tradeId,
                           BankAccount bankAccount,
                           String accountId,
                           String offererPubKey,
                           String preparedOffererDepositTxAsHex,
                           long offererTxOutIndex)
    {
        RequestTakerDepositPaymentMessage tradeMessage = new RequestTakerDepositPaymentMessage(tradeId, bankAccount, accountId, offererPubKey, preparedOffererDepositTxAsHex, offererTxOutIndex);
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, new OutgoingTradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("RequestTakerDepositPaymentMessage successfully arrived at peer");
                resultHandler.onResult();
            }

            @Override
            public void onFailed()
            {
                log.error("RequestTakerDepositPaymentMessage faultHandler.onFault to arrive at peer");
                faultHandler.onFault(new Exception("RequestTakerDepositPaymentMessage faultHandler.onFault to arrive at peer"));
            }
        });
    }

}
