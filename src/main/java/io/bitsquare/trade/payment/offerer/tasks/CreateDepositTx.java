package io.bitsquare.trade.payment.offerer.tasks;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateDepositTx extends AbstractOffererAsBuyerTask
{
    private static final Logger log = LoggerFactory.getLogger(CreateDepositTx.class);

    public CreateDepositTx(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");

        try
        {
            sharedModel.setOffererPubKey(sharedModel.getWalletFacade().getAddressInfoByTradeID(sharedModel.getTrade().getId()).getPubKeyAsHexString());
            Transaction tx = sharedModel.getWalletFacade().offererCreatesMSTxAndAddPayment(sharedModel.getTrade().getCollateralAmount(),
                    sharedModel.getOffererPubKey(),
                    sharedModel.getTakerMultiSigPubKey(),
                    sharedModel.getTrade().getOffer().getArbitrator().getPubKeyAsHex(),
                    sharedModel.getTrade().getId());

            sharedModel.setPreparedOffererDepositTxAsHex(Utils.bytesToHexString(tx.bitcoinSerialize()));
            sharedModel.setOffererTxOutIndex(tx.getInput(0).getOutpoint().getIndex());
            complete();
        } catch (InsufficientMoneyException e)
        {
            log.error("Create deposit tx failed due InsufficientMoneyException " + e);
            failed(new Exception("Create deposit tx failed due InsufficientMoneyException " + e));
        }
    }

}
