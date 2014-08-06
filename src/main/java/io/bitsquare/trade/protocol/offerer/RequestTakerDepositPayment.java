package io.bitsquare.trade.protocol.offerer;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.handlers.ExceptionHandler;
import io.bitsquare.trade.handlers.ResultHandler;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestTakerDepositPayment
{
    private static final Logger log = LoggerFactory.getLogger(RequestTakerDepositPayment.class);

    public static void run(ResultHandler resultHandler,
                           ExceptionHandler exceptionHandler,
                           PeerAddress peerAddress,
                           MessageFacade messageFacade,
                           String tradeId,
                           BankAccount bankAccount,
                           String accountId,
                           String offererPubKey,
                           String preparedOffererDepositTxAsHex,
                           long offererTxOutIndex)
    {
        log.trace("Run task");
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
                log.error("RequestTakerDepositPaymentMessage  did not arrive at peer");
                exceptionHandler.onError(new Exception("RequestTakerDepositPaymentMessage did not arrive at peer"));
            }
        });
    }

}
