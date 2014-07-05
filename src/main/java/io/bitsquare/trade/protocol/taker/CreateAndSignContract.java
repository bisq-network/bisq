package io.bitsquare.trade.protocol.taker;

import com.google.bitcoin.core.ECKey;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.protocol.FaultHandler;
import io.bitsquare.util.Utilities;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAndSignContract
{
    private static final Logger log = LoggerFactory.getLogger(CreateAndSignContract.class);

    public static void run(ResultHandler resultHandler,
                           FaultHandler faultHandler,
                           CryptoFacade cryptoFacade,
                           Offer offer,
                           BigInteger tradeAmount,
                           String takeOfferFeeTxId,
                           String accountId,
                           BankAccount bankAccount,
                           String peersMessagePubKeyAsHex,
                           String messagePubKeyAsHex,
                           String peersAccountId,
                           BankAccount peersBankAccount,
                           ECKey registrationKey)
    {
        try
        {
            Contract contract = new Contract(offer, tradeAmount, takeOfferFeeTxId, peersAccountId, accountId, peersBankAccount, bankAccount, peersMessagePubKeyAsHex, messagePubKeyAsHex);

            String contractAsJson = Utilities.objectToJson(contract);
            String signature = cryptoFacade.signContract(registrationKey, contractAsJson);
            resultHandler.onResult(contract, contractAsJson, signature);
        } catch (Throwable t)
        {
            log.error("Exception at sign contract " + t);
            faultHandler.onFault(t);
        }
    }

    public interface ResultHandler
    {
        void onResult(Contract contract, String contractAsJson, String signature);
    }

}
