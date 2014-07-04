package io.bitsquare.trade.protocol.tasks.taker;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.messages.taker.RequestOffererPublishDepositTxMessage;
import io.bitsquare.trade.protocol.tasks.FaultHandler;
import io.bitsquare.trade.protocol.tasks.ResultHandler;
import io.bitsquare.user.User;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendSignedTakerDepositTxAsHex
{
    private static final Logger log = LoggerFactory.getLogger(SendSignedTakerDepositTxAsHex.class);

    public static void run(ResultHandler resultHandler,
                           FaultHandler faultHandler,
                           PeerAddress peerAddress,
                           MessageFacade messageFacade,
                           WalletFacade walletFacade,
                           Trade trade,
                           User user,
                           Transaction signedTakerDepositTx,
                           long takerTxOutIndex,
                           long offererTxOutIndex)
    {
        RequestOffererPublishDepositTxMessage tradeMessage = new RequestOffererPublishDepositTxMessage(trade.getId(),
                user.getCurrentBankAccount(),
                user.getAccountId(),
                user.getMessagePubKeyAsHex(),
                Utils.bytesToHexString(signedTakerDepositTx.bitcoinSerialize()),
                Utils.bytesToHexString(signedTakerDepositTx.getInput(1).getScriptBytes()),
                Utils.bytesToHexString(signedTakerDepositTx.getInput(1).getConnectedOutput().getParentTransaction().bitcoinSerialize()),
                trade.getContractAsJson(),
                trade.getTakerSignature(),
                walletFacade.getAddressInfoByTradeID(trade.getId()).getAddressString(),
                takerTxOutIndex,
                offererTxOutIndex);
        messageFacade.sendTradeMessage(peerAddress, tradeMessage, new OutgoingTradeMessageListener()
        {
            @Override
            public void onResult()
            {
                log.trace("RequestOffererDepositPublicationMessage successfully arrived at peer");
                resultHandler.onResult();
            }

            @Override
            public void onFailed()
            {
                log.error("RequestOffererDepositPublicationMessage faultHandler.onFault to arrive at peer");
                faultHandler.onFault(new Exception("RequestOffererDepositPublicationMessage faultHandler.onFault to arrive at peer"));
            }
        });
    }
}
