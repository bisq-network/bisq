package io.bitsquare.trade.protocol.shared;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.trade.handlers.ExceptionHandler;
import io.bitsquare.trade.handlers.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyPeerAccount
{
    private static final Logger log = LoggerFactory.getLogger(VerifyPeerAccount.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler, BlockChainFacade blockChainFacade, String peersAccountId, BankAccount peersBankAccount)
    {
        //TODO mocked yet
        if (blockChainFacade.verifyAccountRegistration())
        {
            if (blockChainFacade.isAccountBlackListed(peersAccountId, peersBankAccount))
            {
                log.error("Taker is blacklisted");
                exceptionHandler.onError(new Exception("Taker is blacklisted"));
            }
            else
            {
                resultHandler.onResult();
            }
        }
        else
        {
            log.error("Account registration validation for peer faultHandler.onFault.");
            exceptionHandler.onError(new Exception("Account registration validation for peer faultHandler.onFault."));
        }
    }

}
