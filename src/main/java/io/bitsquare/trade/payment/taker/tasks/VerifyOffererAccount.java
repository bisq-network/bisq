package io.bitsquare.trade.payment.taker.tasks;

import io.nucleo.scheduler.worker.WorkerFaultHandler;
import io.nucleo.scheduler.worker.WorkerResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyOffererAccount extends AbstractTakerAsSellerTask
{
    private static final Logger log = LoggerFactory.getLogger(VerifyOffererAccount.class);

    public VerifyOffererAccount(WorkerResultHandler resultHandler, WorkerFaultHandler faultHandler)
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
                log.error("Offerer is blacklisted");
                failed(new Exception("Offerer is blacklisted"));
            }
            else
            {
                complete();
            }
        }
        else
        {
            log.error("Account Registration for peer failed.");
            failed(new Exception("Account Registration for peer failed."));
        }
    }

}
