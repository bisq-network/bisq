package io.bitsquare.trade.protocol.taker;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.trade.handlers.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayDeposit
{
    private static final Logger log = LoggerFactory.getLogger(PayDeposit.class);

    public static void run(ResultHandler resultHandler,
                           ExceptionHandler exceptionHandler,
                           WalletFacade walletFacade,
                           Coin collateral,
                           Coin tradeAmount,
                           String tradeId,
                           String pubKeyForThatTrade,
                           String arbitratorPubKey,
                           String offererPubKey,
                           String preparedOffererDepositTxAsHex)
    {
        log.trace("Run task");
        try
        {
            Coin amountToPay = tradeAmount.add(collateral);
            Coin msOutputAmount = amountToPay.add(collateral);

            Transaction signedTakerDepositTx = walletFacade.takerAddPaymentAndSignTx(amountToPay,
                                                                                     msOutputAmount,
                                                                                     offererPubKey,
                                                                                     pubKeyForThatTrade,
                                                                                     arbitratorPubKey,
                                                                                     preparedOffererDepositTxAsHex,
                                                                                     tradeId);

            log.trace("sharedModel.signedTakerDepositTx: " + signedTakerDepositTx);
            resultHandler.onResult(signedTakerDepositTx);
        } catch (InsufficientMoneyException e)
        {
            log.error("Pay deposit faultHandler.onFault due InsufficientMoneyException " + e);
            exceptionHandler.onError(new Exception("Pay deposit faultHandler.onFault due InsufficientMoneyException " + e));
        }
    }

    public interface ResultHandler
    {
        void onResult(Transaction signedTakerDepositTx);
    }


}
