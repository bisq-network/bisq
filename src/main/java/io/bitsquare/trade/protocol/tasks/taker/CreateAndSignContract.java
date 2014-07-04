package io.bitsquare.trade.protocol.tasks.taker;

import com.google.bitcoin.core.ECKey;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.tasks.FaultHandler;
import io.bitsquare.trade.protocol.tasks.ResultHandler;
import io.bitsquare.user.User;
import io.bitsquare.util.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAndSignContract
{
    private static final Logger log = LoggerFactory.getLogger(CreateAndSignContract.class);

    public static void run(ResultHandler resultHandler,
                           FaultHandler faultHandler,
                           CryptoFacade cryptoFacade,
                           Trade trade,
                           User user,
                           String peersAccountId,
                           BankAccount peersBankAccount,
                           ECKey registrationKey)
    {
        try
        {
            Contract contract = new Contract(trade.getOffer(),
                    trade.getTradeAmount(),
                    trade.getTakeOfferFeeTxId(),
                    peersAccountId,
                    user.getAccountId(),
                    peersBankAccount,
                    user.getCurrentBankAccount(),
                    trade.getOffer().getMessagePubKeyAsHex(),
                    user.getMessagePubKeyAsHex()
            );

            String contractAsJson = Utilities.objectToJson(contract);
            String signature = cryptoFacade.signContract(registrationKey, contractAsJson);
            //log.trace("contract: " + contract);
            //log.debug("contractAsJson: " + contractAsJson);
            //log.trace("contract signature: " + signature);

            trade.setContract(contract);
            trade.setContractAsJson(contractAsJson);
            trade.setContractTakerSignature(signature);

            resultHandler.onResult();
        } catch (Throwable t)
        {
            log.error("Exception at sign contract " + t);
            faultHandler.onFault(t);
        }
    }


}
