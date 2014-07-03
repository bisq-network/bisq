package io.bitsquare.trade.payment.offerer.tasks;

import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyTakerAccount extends AbstractOffererAsBuyerTask
{
    private static final Logger log = LoggerFactory.getLogger(VerifyTakerAccount.class);

    public VerifyTakerAccount(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
    {
        super(resultHandler, faultHandler);
    }

    @Override
    public void execute()
    {
        log.trace("execute");
        //TODO mocked yet
        if (sharedModel.getBlockChainFacade().verifyAccountRegistration())
        {
            if (sharedModel.getBlockChainFacade().isAccountBlackListed(sharedModel.getPeersAccountId(), sharedModel.getPeersBankAccount()))
            {
                log.error("Taker is blacklisted");
                failed(new Exception("Taker is blacklisted"));
            }
            else
            {
                complete();
            }
        }
        else
        {
            log.error("Account registration validation for peer failed.");
            failed(new Exception("Account registration validation for peer failed."));
        }
    }

}
