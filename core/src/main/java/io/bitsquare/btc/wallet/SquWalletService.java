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

package io.bitsquare.btc.wallet;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import io.bitsquare.btc.Restrictions;
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.exceptions.WalletException;
import io.bitsquare.btc.provider.fee.FeeService;
import io.bitsquare.btc.provider.squ.SquUtxoFeedService;
import io.bitsquare.user.Preferences;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

/**
 * WalletService handles all non trade specific wallet and bitcoin related services.
 * It startup the wallet app kit and initialized the wallet.
 */
public class SquWalletService extends WalletService {
    private static final Logger log = LoggerFactory.getLogger(SquWalletService.class);
    private SquUTXOProvider squUTXOProvider;
    private SquUtxoFeedService squUtxoFeedService;
    private SquCoinSelector squCoinSelector;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SquWalletService(WalletsSetup walletsSetup,
                            SquUTXOProvider squUTXOProvider,
                            SquUtxoFeedService squUtxoFeedService,
                            Preferences preferences,
                            FeeService feeService) {
        super(walletsSetup,
                preferences,
                feeService);
        this.squUTXOProvider = squUTXOProvider;
        this.squUtxoFeedService = squUtxoFeedService;
        this.squCoinSelector = new SquCoinSelector(walletsSetup.getParams());

        walletsSetup.addSetupCompletedHandler(() -> {
            wallet = walletsSetup.getSquWallet();
            wallet.setUTXOProvider(this.squUTXOProvider);
            wallet.setCoinSelector(squCoinSelector);
            wallet.addEventListener(new BitsquareWalletEventListener());
            wallet.addEventListener(new AbstractWalletEventListener() {
                @Override
                public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                    onChange();
                }

                @Override
                public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                    onChange();
                }

                @Override
                public void onReorganize(Wallet wallet) {
                    onChange();
                }

                @Override
                public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                    onChange();
                }

                @Override
                public void onKeysAdded(List<ECKey> keys) {
                    onChange();
                }

                @Override
                public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {
                    onChange();
                }

                @Override
                public void onWalletChanged(Wallet wallet) {
                    onChange();
                }

                public void onChange() {

                }
            });

            // wallet.addEventListener();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Overridden Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    String getWalletAsString(boolean includePrivKeys) {
        return "BitcoinJ wallet:\n" +
                wallet.toString(includePrivKeys, true, true, walletsSetup.getChain()) + "\n\n" +
                "All pubkeys as hex:\n" +
                wallet.printAllPubKeysAsHex();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Coin getAvailableBalance() {
        return wallet != null ? wallet.getBalance(Wallet.BalanceType.AVAILABLE) : Coin.ZERO;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send SQU with BTC fee
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedSendTx(String receiverAddress,
                                         Coin receiverAmount,
                                         Optional<String> changeAddressStringOptional) throws AddressFormatException,
            InsufficientMoneyException, WalletException, TransactionVerificationException {

        Transaction tx = new Transaction(params);
        Preconditions.checkArgument(Restrictions.isAboveDust(receiverAmount),
                "The amount is too low (dust limit).");
        tx.addOutput(receiverAmount, new Address(params, receiverAddress));

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.fee = Coin.ZERO;
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.aesKey = aesKey;
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

    public void signAndBroadcastSendTx(Transaction tx, FutureCallback<Transaction> callback) throws WalletException, TransactionVerificationException {
        Transaction signedTx = signFinalSendTx(tx);
        wallet.commitTx(signedTx);
        Futures.addCallback(walletsSetup.getPeerGroup().broadcastTransaction(signedTx).future(), callback);
        printTx("commitAndBroadcastTx", signedTx);
    }

    private Transaction signFinalSendTx(Transaction tx) throws WalletException, TransactionVerificationException {
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
}
