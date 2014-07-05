package io.bitsquare.trade.protocol.taker;

import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.protocol.FaultHandler;
import io.bitsquare.trade.protocol.ResultHandler;
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
                           BankAccount bankAccount,
                           String accountId,
                           String messagePubKeyAsHex,
                           String tradeId,
                           String contractAsJson,
                           String takerSignature,
                           Transaction signedTakerDepositTx,
                           long offererTxOutIndex)
    {
        long takerTxOutIndex = signedTakerDepositTx.getInput(1).getOutpoint().getIndex();

        RequestOffererPublishDepositTxMessage tradeMessage = new RequestOffererPublishDepositTxMessage(tradeId,
                                                                                                       bankAccount,
                                                                                                       accountId,
                                                                                                       messagePubKeyAsHex,
                                                                                                       Utils.bytesToHexString(signedTakerDepositTx.bitcoinSerialize()),
                                                                                                       Utils.bytesToHexString(signedTakerDepositTx.getInput(1).getScriptBytes()),
                                                                                                       Utils.bytesToHexString(signedTakerDepositTx.getInput(1)
                                                                                                                                                  .getConnectedOutput()
                                                                                                                                                  .getParentTransaction()
                                                                                                                                                  .bitcoinSerialize()),
                                                                                                       contractAsJson,
                                                                                                       takerSignature,
                                                                                                       walletFacade.getAddressInfoByTradeID(tradeId).getAddressString(),
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
