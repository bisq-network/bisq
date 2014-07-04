package io.bitsquare.trade.protocol.tasks.offerer;

import com.google.bitcoin.core.Transaction;
import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.trade.protocol.tasks.FaultHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignAndPublishDepositTx
{
    private static final Logger log = LoggerFactory.getLogger(SignAndPublishDepositTx.class);

    public static void run(ResultHandler resultHandler,
                           FaultHandler faultHandler,
                           WalletFacade walletFacade,
                           String preparedOffererDepositTxAsHex,
                           String signedTakerDepositTxAsHex,
                           String txConnOutAsHex,
                           String txScriptSigAsHex,
                           long offererTxOutIndex,
                           long takerTxOutIndex)
    {
        try
        {
            walletFacade.offererSignAndPublishTx(preparedOffererDepositTxAsHex,
                    signedTakerDepositTxAsHex,
                    txConnOutAsHex,
                    txScriptSigAsHex,
                    offererTxOutIndex,
                    takerTxOutIndex,
                    new FutureCallback<Transaction>()
                    {
                        @Override
                        public void onSuccess(Transaction transaction)
                        {
                            log.trace("offererSignAndPublishTx succeeded " + transaction);
                            resultHandler.onResult(transaction);
                        }

                        @Override
                        public void onFailure(Throwable t)
                        {
                            log.error("offererSignAndPublishTx faultHandler.onFault:" + t);
                            faultHandler.onFault(t);
                        }
                    });
        } catch (Exception e)
        {
            log.error("offererSignAndPublishTx faultHandler.onFault:" + e);
            faultHandler.onFault(e);
        }
    }

    public interface ResultHandler
    {
        void onResult(Transaction transaction);
    }

}
