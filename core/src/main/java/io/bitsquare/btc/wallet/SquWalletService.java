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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import io.bitsquare.btc.Restrictions;
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.exceptions.WalletException;
import io.bitsquare.btc.provider.fee.FeeService;
import io.bitsquare.btc.provider.squ.SquUtxoFeedService;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.user.Preferences;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.CoinSelection;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

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

    public void requestSquUtxo(@Nullable ResultHandler resultHandler, @Nullable ErrorMessageHandler errorMessageHandler) {
        Set<UTXO> utxoSet = squUtxoFeedService.getUtxoSet();
        if (utxoSet != null)
            squUTXOProvider.setUtxoSet(utxoSet);

        squUtxoFeedService.requestSquUtxo(utxos -> {
                    squUTXOProvider.setUtxoSet(utxos);
                    if (resultHandler != null)
                        resultHandler.handleResult();
                },
                (errorMessage, throwable) -> {
                    if (errorMessageHandler != null)
                        errorMessageHandler.handleErrorMessage(errorMessage);
                });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send SQU with BTC fee
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedSendTx(String receiverAddress,
                                         Coin receiverAmount,
                                         Optional<String> changeAddressStringOptional) throws AddressFormatException,
            InsufficientMoneyException, WalletException, TransactionVerificationException {

        Transaction tx = new Transaction(params);
        checkArgument(Restrictions.isAboveDust(receiverAmount),
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Burn SQU for CompensationRequest fee
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedCompensationRequestFeeTx(Coin createCompensationRequestFee) throws WalletException, TransactionVerificationException, InsufficientMoneyException, ChangeBelowDustException {
        Transaction tx = new Transaction(params);

        SquFeeCoinSelector squFeeCoinSelector = new SquFeeCoinSelector(wallet, true);
        CoinSelection coinSelection = squFeeCoinSelector.getCoinSelection(createCompensationRequestFee);
        coinSelection.gathered.stream().forEach(tx::addInput);
        Coin change = coinSelection.valueGathered.subtract(createCompensationRequestFee);
        if (change.isPositive())
            tx.addOutput(change, wallet.freshReceiveAddress());

        printTx("preparedCompensationRequestFeeTx", tx);
        return tx;
    }

    public void signAndBroadcastCompensationRequestFeeTx(Transaction tx, FutureCallback<Transaction> callback) throws WalletException, TransactionVerificationException {
        // Sign all SQU inputs
        int endIndex = tx.getInputs().size();
        for (int i = 0; i < endIndex; i++) {
            TransactionInput txIn = tx.getInputs().get(i);
            TransactionOutput connectedOutput = txIn.getConnectedOutput();
            if (connectedOutput != null && connectedOutput.isMine(wallet)) {
                signTransactionInput(tx, txIn, i);
                checkScriptSig(tx, txIn, i);
            }
        }

        checkWalletConsistency();
        verifyTransaction(tx);

        wallet.commitTx(tx);
        checkWalletConsistency();
        Futures.addCallback(walletsSetup.getPeerGroup().broadcastTransaction(tx).future(), callback);
        printTx("signAndBroadcastCompensationRequestFeeTx", tx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Burn SQU for CompensationRequest fee
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedVotingTx(Coin createCompensationRequestFee) throws WalletException, TransactionVerificationException, InsufficientMoneyException, ChangeBelowDustException {
        Transaction tx = new Transaction(params);
        //TODO
        SquFeeCoinSelector squFeeCoinSelector = new SquFeeCoinSelector(wallet, true);
        CoinSelection coinSelection = squFeeCoinSelector.getCoinSelection(createCompensationRequestFee);
        coinSelection.gathered.stream().forEach(tx::addInput);
        Coin change = coinSelection.valueGathered.subtract(createCompensationRequestFee);
        if (change.isPositive())
            tx.addOutput(change, wallet.freshReceiveAddress());

        printTx("PreparedVotingTx", tx);
        return tx;
    }

    public void signAndBroadcastVotingTx(Transaction tx, FutureCallback<Transaction> callback) throws WalletException, TransactionVerificationException {
        // Sign all SQU inputs
        //TODO
        int endIndex = tx.getInputs().size();
        for (int i = 0; i < endIndex; i++) {
            TransactionInput txIn = tx.getInputs().get(i);
            TransactionOutput connectedOutput = txIn.getConnectedOutput();
            if (connectedOutput != null && connectedOutput.isMine(wallet)) {
                signTransactionInput(tx, txIn, i);
                checkScriptSig(tx, txIn, i);
            }
        }

        checkWalletConsistency();
        verifyTransaction(tx);

        wallet.commitTx(tx);
        checkWalletConsistency();
        Futures.addCallback(walletsSetup.getPeerGroup().broadcastTransaction(tx).future(), callback);
        printTx("signAndBroadcastVotingTx", tx);
    }
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Create funds to a CompensationRequest
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Address getSquAddressForCompensationRequestFunding() {
        return wallet.freshReceiveAddress();
    }
}
