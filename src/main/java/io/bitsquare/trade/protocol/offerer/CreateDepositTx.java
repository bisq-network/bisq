package io.bitsquare.trade.protocol.offerer;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.trade.protocol.FaultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateDepositTx
{
    private static final Logger log = LoggerFactory.getLogger(CreateDepositTx.class);

    public static void run(ResultHandler resultHandler,
                           FaultHandler faultHandler,
                           WalletFacade walletFacade,
                           String tradeId,
                           Coin offererInputAmount,
                           String takerMultiSigPubKey,
                           String arbitratorPubKeyAsHex)
    {
        log.trace("Run task");
        try
        {
            String offererPubKey = walletFacade.getAddressInfoByTradeID(tradeId).getPubKeyAsHexString();
            Transaction transaction = walletFacade.offererCreatesMSTxAndAddPayment(offererInputAmount, offererPubKey, takerMultiSigPubKey, arbitratorPubKeyAsHex, tradeId);

            String preparedOffererDepositTxAsHex = Utils.HEX.encode(transaction.bitcoinSerialize());
            long offererTxOutIndex = transaction.getInput(0).getOutpoint().getIndex();

            resultHandler.onResult(offererPubKey, preparedOffererDepositTxAsHex, offererTxOutIndex);
        } catch (InsufficientMoneyException e)
        {
            log.error("Create deposit tx faultHandler.onFault due InsufficientMoneyException " + e);
            faultHandler.onFault(new Exception("Create deposit tx faultHandler.onFault due InsufficientMoneyException " + e));
        }
    }

    public interface ResultHandler
    {
        void onResult(String offererPubKey, String preparedOffererDepositTxAsHex, long offererTxOutIndex);
    }

}
