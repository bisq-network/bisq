/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.btc;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.exceptions.WalletException;
import io.bitsquare.btc.provider.fee.FeeService;
import io.bitsquare.common.handlers.ExceptionHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.user.Preferences;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.DeterministicSeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;

/**
 * WalletService handles all non trade specific wallet and bitcoin related services.
 * It startup the wallet app kit and initialized the wallet.
 */
public class SquWalletService extends WalletService {
    private static final Logger log = LoggerFactory.getLogger(SquWalletService.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SquWalletService(WalletSetup walletSetup,
                            Preferences preferences,
                            FeeService feeService) {
        super(walletSetup,
                preferences,
                feeService);

        walletSetup.addSetupCompletedHandler(() -> {
            wallet = walletSetup.getTokenWallet();
            wallet.addEventListener(walletEventListener);
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    public String exportWalletData(boolean includePrivKeys) {
        StringBuilder addressEntryListData = new StringBuilder();
        return "BitcoinJ SQU wallet:\n" +
                wallet.toString(includePrivKeys, true, true, walletSetup.chain()) + "\n\n" +
                "SQU address entry list:\n" +
                addressEntryListData.toString() +
                "All pubkeys as hex:\n" +
                wallet.printAllPubKeysAsHex();
    }

    //TODO
    public void restoreSeedWords(DeterministicSeed seed, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
       /* Context ctx = Context.get();
        new Thread(() -> {
            try {
                Context.propagate(ctx);
                walletAppKit.stopAsync();
                walletAppKit.awaitTerminated();
                initialize(seed, resultHandler, exceptionHandler);
            } catch (Throwable t) {
                t.printStackTrace();
                log.error("Executing task failed. " + t.getMessage());
            }
        }, "RestoreWallet-%d").start();*/
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Withdrawal Send
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction prepareSendTx(String receiverAddress,
                                     Coin receiverAmount,
                                     Optional<String> changeAddressStringOptional) throws AddressFormatException,
            AddressEntryException, InsufficientMoneyException, WalletException, TransactionVerificationException {

        Transaction tx = new Transaction(params);
        Preconditions.checkArgument(Restrictions.isAboveDust(receiverAmount),
                "The amount is too low (dust limit).");
        tx.addOutput(receiverAmount, new Address(params, receiverAddress));

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.fee = Coin.ZERO;
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.aesKey = walletSetup.getAesKey();
        sendRequest.shuffleOutputs = false;
        sendRequest.signInputs = false;
        sendRequest.ensureMinRequiredFee = false;
        sendRequest.changeAddress = changeAddressStringOptional.isPresent() ?
                new Address(params, changeAddressStringOptional.get()) :
                wallet.freshReceiveAddress();
        wallet.completeTx(sendRequest);
        checkWalletConsistency();
        verifyTransaction(tx);
        printTx("prepareSendTx", tx);
        return tx;
    }

    public Transaction signFinalSendTx(Transaction tx) throws WalletException, TransactionVerificationException {
        // TODO
        int index = 0;
        TransactionInput txIn = tx.getInput(index);

        signTransactionInput(tx, txIn, index);
        checkWalletConsistency();
        verifyTransaction(tx);
        // now all sigs need to be included
        checkScriptSigs(tx);
        printTx("signFinalSendTx", tx);
        return tx;
    }

    public void commitAndBroadcastTx(Transaction tx, FutureCallback<Transaction> callback) {
        wallet.commitTx(tx);
        ListenableFuture<Transaction> broadcastComplete = walletSetup.peerGroup().broadcastTransaction(tx).future();
        Futures.addCallback(broadcastComplete, callback);
        printTx("commitAndBroadcastTx", tx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Util
    ///////////////////////////////////////////////////////////////////////////////////////////


}
