package io.bitsquare.trade.protocol.shared;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.trade.protocol.FaultHandler;
import io.bitsquare.trade.protocol.ResultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyPeerAccount
{
    private static final Logger log = LoggerFactory.getLogger(VerifyPeerAccount.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, BlockChainFacade blockChainFacade, String peersAccountId, BankAccount peersBankAccount)
    {
        //TODO mocked yet
        if (blockChainFacade.verifyAccountRegistration())
        {
            if (blockChainFacade.isAccountBlackListed(peersAccountId, peersBankAccount))
            {
                log.error("Taker is blacklisted");
                faultHandler.onFault(new Exception("Taker is blacklisted"));
            }
            else
            {
                resultHandler.onResult();
            }
        }
        else
        {
            log.error("Account registration validation for peer faultHandler.onFault.");
            faultHandler.onFault(new Exception("Account registration validation for peer faultHandler.onFault."));
        }
    }

}
