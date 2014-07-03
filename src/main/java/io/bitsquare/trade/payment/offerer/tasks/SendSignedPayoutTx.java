package io.bitsquare.trade.payment.offerer.tasks;

import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.Transaction;
import io.bitsquare.msg.listeners.TradeMessageListener;
import io.bitsquare.trade.payment.offerer.messages.BankTransferInitedMessage;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import java.math.BigInteger;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendSignedPayoutTx extends AbstractOffererAsBuyerTask
{
    private static final Logger log = LoggerFactory.getLogger(SendSignedPayoutTx.class);

    public SendSignedPayoutTx(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");

        try
        {
            Transaction depositTransaction = sharedModel.getTrade().getDepositTransaction();
            BigInteger collateral = sharedModel.getTrade().getCollateralAmount();
            BigInteger offererPaybackAmount = sharedModel.getTrade().getTradeAmount().add(collateral);
            BigInteger takerPaybackAmount = collateral;

            log.trace("offererPaybackAmount " + offererPaybackAmount);
            log.trace("takerPaybackAmount " + takerPaybackAmount);
            log.trace("depositTransaction.getHashAsString() " + depositTransaction.getHashAsString());
            log.trace("takerPayoutAddress " + sharedModel.getTakerPayoutAddress());

            Pair<ECKey.ECDSASignature, String> result = sharedModel.getWalletFacade().offererCreatesAndSignsPayoutTx(depositTransaction.getHashAsString(),
                    offererPaybackAmount,
                    takerPaybackAmount,
                    sharedModel.getTakerPayoutAddress(),
                    sharedModel.getTrade().getId());

            ECKey.ECDSASignature offererSignature = result.getKey();
            String offererSignatureR = offererSignature.r.toString();
            String offererSignatureS = offererSignature.s.toString();
            String depositTxAsHex = result.getValue();
            String offererPayoutAddress = sharedModel.getWalletFacade().getAddressInfoByTradeID(sharedModel.getTrade().getId()).getAddressString();

            BankTransferInitedMessage tradeMessage = new BankTransferInitedMessage(sharedModel.getTrade().getId(),
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

            sharedModel.getMessageFacade().sendTradeMessage(sharedModel.peerAddress, tradeMessage, new TradeMessageListener()
            {
                @Override
                public void onResult()
                {
                    log.trace("BankTransferInitedMessage successfully arrived at peer");
                    complete();
                }

                @Override
                public void onFailed()
                {
                    log.error("BankTransferInitedMessage failed to arrive at peer");
                    failed(new Exception("BankTransferInitedMessage failed to arrive at peer"));

                }
            });
        } catch (Exception e)
        {
            log.error("Exception at OffererCreatesAndSignsPayoutTx " + e);
            failed(e);
        }
    }

}
