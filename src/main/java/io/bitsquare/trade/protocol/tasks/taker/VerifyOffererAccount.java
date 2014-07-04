package io.bitsquare.trade.protocol.tasks.taker;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.trade.protocol.tasks.FaultHandler;
import io.bitsquare.trade.protocol.tasks.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyOffererAccount
{
    private static final Logger log = LoggerFactory.getLogger(VerifyOffererAccount.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, BlockChainFacade blockChainFacade, String peersAccountId, BankAccount peersBankAccount)
    {
        //TODO mocked yet
        if (blockChainFacade.verifyAccountRegistration())
        {
            if (blockChainFacade.isAccountBlackListed(peersAccountId, peersBankAccount))
            {
                log.error("Offerer is blacklisted");
                faultHandler.onFault(new Exception("Offerer is blacklisted"));
            }
            else
            {
                resultHandler.onResult();
            }
        }
        else
        {
            log.error("Account Registration for peer faultHandler.onFault.");
            faultHandler.onFault(new Exception("Account Registration for peer faultHandler.onFault."));
        }
    }
}
