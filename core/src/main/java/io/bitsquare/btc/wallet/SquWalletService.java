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
import io.bitsquare.messages.btc.Restrictions;
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.exceptions.WalletException;
import io.bitsquare.messages.btc.provider.fee.FeeService;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.dao.blockchain.SquBlockchainManager;
import io.bitsquare.dao.blockchain.SquUTXO;
import io.bitsquare.messages.user.Preferences;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.CoinSelection;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * WalletService handles all non trade specific wallet and bitcoin related services.
 * It startup the wallet app kit and initialized the wallet.
 */
public class SquWalletService extends WalletService {
    private static final Logger log = LoggerFactory.getLogger(SquWalletService.class);

    private final SquBlockchainManager squBlockchainManager;
    private final SquCoinSelector squCoinSelector;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SquWalletService(WalletsSetup walletsSetup,
                            SquBlockchainManager squBlockchainManager,
                            Preferences preferences,
                            FeeService feeService) {
        super(walletsSetup,
                preferences,
                feeService);
        this.squBlockchainManager = squBlockchainManager;
        this.squCoinSelector = new SquCoinSelector(true);

        walletsSetup.addSetupCompletedHandler(() -> {
            wallet = walletsSetup.getSquWallet();
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
                    // TODO
                }
            });
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
    // Balance
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Coin getAvailableBalance() {
        return wallet != null ? wallet.getBalance(Wallet.BalanceType.AVAILABLE) : Coin.ZERO;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UTXO
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestSquUtxo(@Nullable ResultHandler resultHandler, @Nullable ErrorMessageHandler errorMessageHandler) {
        if (squBlockchainManager.isUtxoAvailable()) {
            applyUtxoSetToUTXOProvider(squBlockchainManager.getUtxoByTxIdMap());
            if (resultHandler != null)
                resultHandler.handleResult();
        } else {
            squBlockchainManager.addUtxoListener(utxoByTxIdMap -> {
                applyUtxoSetToUTXOProvider(utxoByTxIdMap);
                if (resultHandler != null)
                    resultHandler.handleResult();
            });
        }
    }

    private void applyUtxoSetToUTXOProvider(Map<String, Map<Integer, SquUTXO>> utxoByTxIdMap) {
        Set<SquUTXO> utxoSet = new HashSet<>();
        utxoByTxIdMap.entrySet().stream()
                .forEach(e -> e.getValue().entrySet().stream()
                        .forEach(u -> utxoSet.add(u.getValue())));
        squCoinSelector.setUtxoSet(utxoSet);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Sign tx 
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction signTx(Transaction tx) throws WalletException, TransactionVerificationException {
        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput txIn = tx.getInputs().get(i);
            TransactionOutput connectedOutput = txIn.getConnectedOutput();
            if (connectedOutput != null && connectedOutput.isMine(wallet)) {
                signTransactionInput(tx, txIn, i);
                checkScriptSig(tx, txIn, i);
            }
        }

        checkWalletConsistency();
        verifyTransaction(tx);
        // printTx("SQU wallet: Signed Tx", tx);
        return tx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Commit tx 
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void commitTx(Transaction tx) {
        wallet.commitTx(tx);
        //printTx("SQU commit Tx", tx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Broadcast tx 
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void broadcastTx(Transaction tx, FutureCallback<Transaction> callback) throws WalletException, TransactionVerificationException {
        Futures.addCallback(walletsSetup.getPeerGroup().broadcastTransaction(tx).future(), callback);
        printTx("SQU broadcast Tx", tx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send SQU with BTC fee
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedSendTx(String receiverAddress,
                                         Coin receiverAmount) throws AddressFormatException,
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
        sendRequest.changeAddress = getUnusedAddress();
        wallet.completeTx(sendRequest);
        checkWalletConsistency();
        verifyTransaction(tx);
        //  printTx("prepareSendTx", tx);
        return tx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Burn fee tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedBurnFeeTx(Coin fee) throws WalletException, TransactionVerificationException,
            InsufficientMoneyException, ChangeBelowDustException {
        Transaction tx = new Transaction(params);
        CoinSelection coinSelection = squCoinSelector.select(fee, getTransactionOutputsFromUtxoProvider());
        coinSelection.gathered.stream().forEach(tx::addInput);
        Coin change = squCoinSelector.getChange(fee, coinSelection);
        if (change.isPositive())
            tx.addOutput(change, getUnusedAddress());

        // printTx("preparedCompensationRequestFeeTx", tx);
        return tx;
    }

    private List<TransactionOutput> getTransactionOutputsFromUtxoProvider() {
        // As we have set the utxoProvider in the wallet it will be used for candidates selection internally
        return wallet.calculateAllSpendCandidates(true, true);
    }

    protected Set<Address> getAllAddressesFromActiveKeys() throws UTXOProviderException {
        return wallet.getActiveKeychain().getLeafKeys().stream().
                map(key -> new Address(params, key.getPubKeyHash())).
                collect(Collectors.toSet());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Get unused address
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Address getUnusedAddress() {
        //TODO check if current address was used, otherwise get fresh
        return wallet.freshReceiveAddress();
    }
}
