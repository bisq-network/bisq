package io.bitsquare.trade.payment.taker.tasks;

import com.google.bitcoin.core.InsufficientMoneyException;
import io.bitsquare.trade.Trade;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayDeposit extends AbstractTakerAsSellerTask
{
    private static final Logger log = LoggerFactory.getLogger(PayDeposit.class);

    public PayDeposit(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        try
        {
            Trade trade = sharedModel.getTrade();
            BigInteger collateralAmount = trade.getCollateralAmount();
            sharedModel.setSignedTakerDepositTx(sharedModel.getWalletFacade().takerAddPaymentAndSignTx(trade.getTradeAmount().add(collateralAmount),
                    trade.getTradeAmount().add(collateralAmount).add(collateralAmount),
                    sharedModel.getOffererPubKey(),
                    sharedModel.getWalletFacade().getAddressInfoByTradeID(trade.getId()).getPubKeyAsHexString(),
                    trade.getOffer().getArbitrator().getPubKeyAsHex(),
                    sharedModel.getPreparedOffererDepositTxAsHex(),
                    trade.getId()));

            log.trace("sharedModel.signedTakerDepositTx: " + sharedModel.getSignedTakerDepositTx());
            sharedModel.setTakerTxOutIndex(sharedModel.getSignedTakerDepositTx().getInput(1).getOutpoint().getIndex());

            complete();
        } catch (InsufficientMoneyException e)
        {
            log.error("Pay deposit failed due InsufficientMoneyException " + e);
            failed(new Exception("Pay deposit failed due InsufficientMoneyException " + e));
        }
    }

}
