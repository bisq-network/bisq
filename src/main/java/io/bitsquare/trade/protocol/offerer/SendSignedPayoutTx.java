package io.bitsquare.trade.protocol.offerer;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.ECKey;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.protocol.FaultHandler;
import io.bitsquare.trade.protocol.ResultHandler;
import javafx.util.Pair;
import net.tomp2p.peers.PeerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendSignedPayoutTx
{
    private static final Logger log = LoggerFactory.getLogger(SendSignedPayoutTx.class);

    public static void run(ResultHandler resultHandler,
                           FaultHandler faultHandler,
                           PeerAddress peerAddress,
                           MessageFacade messageFacade,
                           WalletFacade walletFacade,
                           String tradeId,
                           String takerPayoutAddress,
                           String offererPayoutAddress,
                           String depositTransactionId,
                           Coin collateral,
                           Coin tradeAmount)
    {
        log.trace("Run task");
        try
        {
            Coin offererPaybackAmount = tradeAmount.add(collateral);
            Coin takerPaybackAmount = collateral;

            Pair<ECKey.ECDSASignature, String> result = walletFacade.offererCreatesAndSignsPayoutTx(depositTransactionId, offererPaybackAmount, takerPaybackAmount, takerPayoutAddress, tradeId);

            ECKey.ECDSASignature offererSignature = result.getKey();
            String offererSignatureR = offererSignature.r.toString();
            String offererSignatureS = offererSignature.s.toString();
            String depositTxAsHex = result.getValue();

            BankTransferInitedMessage tradeMessage = new BankTransferInitedMessage(tradeId,
                                                                                   depositTxAsHex,
                                                                                   offererSignatureR,
                                                                                   offererSignatureS,
                                                                                   offererPaybackAmount,
                                                                                   takerPaybackAmount,
                                                                                   offererPayoutAddress);

            messageFacade.sendTradeMessage(peerAddress, tradeMessage, new OutgoingTradeMessageListener()
            {
                @Override
                public void onResult()
                {
                    log.trace("BankTransferInitedMessage successfully arrived at peer");
                    resultHandler.onResult();
                }

                @Override
                public void onFailed()
                {
                    log.error("BankTransferInitedMessage did not arrive at peer");
                    faultHandler.onFault(new Exception("BankTransferInitedMessage did not arrive at peer"));

                }
            });
        } catch (Exception e)
        {
            log.error("Exception at OffererCreatesAndSignsPayoutTx " + e);
            faultHandler.onFault(e);
        }
    }
}
