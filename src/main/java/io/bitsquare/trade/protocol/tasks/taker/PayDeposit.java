package io.bitsquare.trade.protocol.tasks.taker;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Transaction;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.tasks.FaultHandler;
import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayDeposit
{
    private static final Logger log = LoggerFactory.getLogger(PayDeposit.class);

    public static void run(ResultHandler resultHandler, FaultHandler faultHandler, WalletFacade walletFacade, Trade trade, String offererPubKey, String preparedOffererDepositTxAsHex)
    {
        try
        {
            BigInteger collateralAmount = trade.getCollateralAmount();
            Transaction signedTakerDepositTx = walletFacade.takerAddPaymentAndSignTx(trade.getTradeAmount().add(collateralAmount),
                    trade.getTradeAmount().add(collateralAmount).add(collateralAmount),
                    offererPubKey,
                    walletFacade.getAddressInfoByTradeID(trade.getId()).getPubKeyAsHexString(),
                    trade.getOffer().getArbitrator().getPubKeyAsHex(),
                    preparedOffererDepositTxAsHex,
                    trade.getId());

            log.trace("sharedModel.signedTakerDepositTx: " + signedTakerDepositTx);
            long takerTxOutIndex = signedTakerDepositTx.getInput(1).getOutpoint().getIndex();

            resultHandler.onResult(signedTakerDepositTx, takerTxOutIndex);
        } catch (InsufficientMoneyException e)
        {
            log.error("Pay deposit faultHandler.onFault due InsufficientMoneyException " + e);
            faultHandler.onFault(new Exception("Pay deposit faultHandler.onFault due InsufficientMoneyException " + e));
        }
    }

    public interface ResultHandler
    {
        void onResult(Transaction signedTakerDepositTx, long takerTxOutIndex);
    }


}
