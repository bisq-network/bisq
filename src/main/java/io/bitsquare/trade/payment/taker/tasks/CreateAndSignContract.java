package io.bitsquare.trade.payment.taker.tasks;

import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Trade;
import io.bitsquare.util.Utilities;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAndSignContract extends AbstractTakerAsSellerTask
{
    private static final Logger log = LoggerFactory.getLogger(CreateAndSignContract.class);

    public CreateAndSignContract(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        Trade trade = sharedModel.getTrade();
        Contract contract = new Contract(trade.getOffer(),
                trade.getTradeAmount(),
                trade.getTakeOfferFeeTxId(),
                sharedModel.getPeersAccountId(),
                sharedModel.getUser().getAccountID(),
                sharedModel.getPeersBankAccount(),
                sharedModel.getUser().getCurrentBankAccount(),
                trade.getOffer().getMessagePubKeyAsHex(),
                sharedModel.getUser().getMessagePubKeyAsHex()
        );

        String contractAsJson = Utilities.objectToJson(contract);
        String signature = sharedModel.getCryptoFacade().signContract(sharedModel.getWalletFacade().getRegistrationAddressInfo().getKey(), contractAsJson);
        //log.trace("contract: " + contract);
        //log.debug("contractAsJson: " + contractAsJson);
        //log.trace("contract signature: " + signature);

        trade.setContract(contract);
        trade.setContractAsJson(contractAsJson);
        trade.setContractTakerSignature(signature);

        complete();
    }

}
