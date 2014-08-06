package io.bitsquare.btc.tasks;

import com.google.bitcoin.core.*;
import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.btc.AddressBasedCoinSelector;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PayFeeTask
{
    private static final Logger log = LoggerFactory.getLogger(PayFeeTask.class);

    private enum State
    {
        INIT,
        TX_COMPLETED,
        TX_COMMITTED,
        TX_BROAD_CASTED,
    }

    private State state;

    public String start(Wallet wallet, FeePolicy feePolicy, AddressEntry addressEntry, FutureCallback<Transaction> callback)
    {
        state = State.INIT;

        Transaction tx = new Transaction(wallet.getParams());
        Coin fee = FeePolicy.CREATE_OFFER_FEE.subtract(FeePolicy.TX_FEE);
        log.trace("fee: " + fee.toFriendlyString());
        tx.addOutput(fee, feePolicy.getAddressForCreateOfferFee());

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.shuffleOutputs = false;
        // we allow spending of unconfirmed tx (double spend risk is low and usability would suffer if we need to wait for 1 confirmation)
        sendRequest.coinSelector = new AddressBasedCoinSelector(wallet.getParams(), addressEntry, true);
        sendRequest.changeAddress = addressEntry.getAddress();

        try
        {
            Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
            state = State.TX_COMPLETED;


        } catch (IllegalStateException e)
        {
            e.printStackTrace();
        } catch (InsufficientMoneyException e)
        {
            e.printStackTrace();
        } catch (IllegalArgumentException e)
        {
            e.printStackTrace();
        } catch (Wallet.DustySendRequested e)
        {
            e.printStackTrace();
        } catch (Wallet.CouldNotAdjustDownwards e)
        {
            e.printStackTrace();
        } catch (Wallet.ExceededMaxTransactionSize e)
        {
            e.printStackTrace();
        } catch (VerificationException e)
        {
            e.printStackTrace();
        }
        /*
         * @throws IllegalStateException if no transaction broadcaster has been configured.
     * @throws InsufficientMoneyException if the request could not be completed due to not enough balance.
     * @throws IllegalArgumentException if you try and complete the same SendRequest twice
     * @throws DustySendRequested if the resultant transaction would violate the dust rules (an output that's too small to be worthwhile)
     * @throws CouldNotAdjustDownwards if emptying the wallet was requested and the output can't be shrunk for fees without violating a protocol rule.
     * @throws ExceededMaxTransactionSize if the resultant transaction is too big for Bitcoin to process (try breaking up the amounts of value)
         */


        WalletFacade.printInputs("payCreateOfferFee", tx);
        log.debug("tx=" + tx);

        return tx.getHashAsString();
    }


}
