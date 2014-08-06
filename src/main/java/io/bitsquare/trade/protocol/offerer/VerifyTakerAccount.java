package io.bitsquare.trade.protocol.offerer;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainFacade;
import io.bitsquare.trade.handlers.ExceptionHandler;
import io.bitsquare.trade.handlers.ResultHandler;
import io.bitsquare.trade.protocol.shared.VerifyPeerAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyTakerAccount
{
    private static final Logger log = LoggerFactory.getLogger(VerifyTakerAccount.class);

    public static void run(ResultHandler resultHandler, ExceptionHandler exceptionHandler, BlockChainFacade blockChainFacade, String peersAccountId, BankAccount peersBankAccount)
    {
        log.trace("Run task");
        VerifyPeerAccount.run(resultHandler, exceptionHandler, blockChainFacade, peersAccountId, peersBankAccount);
    }

}
