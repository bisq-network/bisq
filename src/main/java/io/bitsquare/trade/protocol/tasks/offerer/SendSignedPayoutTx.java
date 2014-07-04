package io.bitsquare.trade.protocol.tasks.offerer;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.messages.offerer.BankTransferInitedMessage;
import io.bitsquare.trade.protocol.tasks.FaultHandler;
import io.bitsquare.trade.protocol.tasks.ResultHandler;
import java.math.BigInteger;
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
                           Trade trade,
                           String takerPayoutAddress)
    {
        try
        {
            Transaction depositTransaction = trade.getDepositTransaction();
            BigInteger collateral = trade.getCollateralAmount();
            BigInteger offererPaybackAmount = trade.getTradeAmount().add(collateral);
            BigInteger takerPaybackAmount = collateral;

            log.trace("offererPaybackAmount " + offererPaybackAmount);
            log.trace("takerPaybackAmount " + takerPaybackAmount);
            log.trace("depositTransaction.getHashAsString() " + depositTransaction.getHashAsString());
            log.trace("takerPayoutAddress " + takerPayoutAddress);

            Pair<ECKey.ECDSASignature, String> result = walletFacade.offererCreatesAndSignsPayoutTx(depositTransaction.getHashAsString(),
                    offererPaybackAmount,
                    takerPaybackAmount,
                    takerPayoutAddress,
                    trade.getId());

            ECKey.ECDSASignature offererSignature = result.getKey();
            String offererSignatureR = offererSignature.r.toString();
            String offererSignatureS = offererSignature.s.toString();
            String depositTxAsHex = result.getValue();
            String offererPayoutAddress = walletFacade.getAddressInfoByTradeID(trade.getId()).getAddressString();

            BankTransferInitedMessage tradeMessage = new BankTransferInitedMessage(trade.getId(),
                    depositTxAsHex,
                    offererSignatureR,
                    offererSignatureS,
                    offererPaybackAmount,
                    takerPaybackAmount,
                    offererPayoutAddress);

            log.trace("depositTxAsHex " + depositTxAsHex);
            log.trace("offererSignatureR " + offererSignatureR);
            log.trace("offererSignatureS " + offererSignatureS);
            log.trace("offererPaybackAmount " + offererPaybackAmount);
            log.trace("takerPaybackAmount " + takerPaybackAmount);
            log.trace("offererPayoutAddress " + offererPayoutAddress);

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
                    log.error("BankTransferInitedMessage faultHandler.onFault to arrive at peer");
                    faultHandler.onFault(new Exception("BankTransferInitedMessage faultHandler.onFault to arrive at peer"));

                }
            });
        } catch (Exception e)
        {
            log.error("Exception at OffererCreatesAndSignsPayoutTx " + e);
            faultHandler.onFault(e);
        }
    }
}
