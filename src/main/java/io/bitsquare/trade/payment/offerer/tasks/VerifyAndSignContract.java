package io.bitsquare.trade.payment.offerer.tasks;

import io.bitsquare.trade.Contract;
import io.bitsquare.util.Utilities;
import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyAndSignContract extends AbstractOffererAsBuyerTask
{
    private static final Logger log = LoggerFactory.getLogger(VerifyAndSignContract.class);

    public VerifyAndSignContract(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        Contract contract = new Contract(sharedModel.getTrade().getOffer(),
                sharedModel.getTrade().getTradeAmount(),
                sharedModel.getTrade().getTakeOfferFeeTxId(),
                sharedModel.getUser().getAccountID(),
                sharedModel.getPeersAccountId(),
                sharedModel.getUser().getCurrentBankAccount(),
                sharedModel.getPeersBankAccount(),
                sharedModel.getTrade().getOffer().getMessagePubKeyAsHex(),
                sharedModel.getTakerMessagePubKey());

        String contractAsJson = Utilities.objectToJson(contract);
        // log.trace("Offerer contract created: " + contract);
        // log.trace("Offerers contractAsJson: " + contractAsJson);
        // log.trace("Takers contractAsJson: " + sharedModel.peersContractAsJson);
        if (contractAsJson.equals(sharedModel.getPeersContractAsJson()))
        {
            log.trace("The 2 contracts as json does match");
            String signature = sharedModel.getCryptoFacade().signContract(sharedModel.getWalletFacade().getRegistrationAddressInfo().getKey(), contractAsJson);
            sharedModel.getTrade().setContract(contract);
            sharedModel.getTrade().setContractAsJson(contractAsJson);
            sharedModel.getTrade().setContractTakerSignature(signature);
            //log.trace("signature: " + signature);

            complete();
        }
        else
        {
            // TODO use diff output as feedback ?
            log.error("Contracts are not matching.");
            log.error("Offerers contractAsJson: " + contractAsJson);
            log.error("Takers contractAsJson: " + sharedModel.getPeersContractAsJson());

            failed(new Exception("Contracts are not matching"));
        }
    }

}
