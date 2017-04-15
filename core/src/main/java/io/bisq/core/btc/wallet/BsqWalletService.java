/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.btc.wallet;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import io.bisq.core.btc.Restrictions;
import io.bisq.core.btc.exceptions.TransactionVerificationException;
import io.bisq.core.btc.exceptions.WalletException;
import io.bisq.core.dao.blockchain.BsqBlockchainManager;
import io.bisq.core.dao.blockchain.BsqChainState;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.user.Preferences;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.CoinSelection;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.PENDING;

@Slf4j
public class BsqWalletService extends WalletService {

    private final BsqCoinSelector bsqCoinSelector;
    private BsqChainState bsqChainState;
    @Getter
    private final ObservableList<Transaction> walletTransactions = FXCollections.observableArrayList();
    private final CopyOnWriteArraySet<BsqBalanceListener> bsqBalanceListeners = new CopyOnWriteArraySet<>();
    private Coin availableBsqBalance = Coin.ZERO;
    private Coin unverifiedBalance = Coin.ZERO;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqWalletService(WalletsSetup walletsSetup,
                            BsqCoinSelector bsqCoinSelector,
                            BsqChainState bsqChainState,
                            BsqBlockchainManager bsqBlockchainManager,
                            Preferences preferences,
                            FeeService feeService) {
        super(walletsSetup,
                preferences,
                feeService);

        this.bsqCoinSelector = bsqCoinSelector;
        this.bsqChainState = bsqChainState;

        walletsSetup.addSetupCompletedHandler(() -> {
            wallet = walletsSetup.getBsqWallet();
            wallet.setCoinSelector(bsqCoinSelector);

            wallet.addEventListener(walletEventListener);

            wallet.addEventListener(new AbstractWalletEventListener() {
                @Override
                public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                    //TODO do we need updateWalletBsqTransactions(); here?
                }

                @Override
                public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                    //TODO do we need updateWalletBsqTransactions(); here?
                }

                @Override
                public void onReorganize(Wallet wallet) {
                    log.warn("onReorganize ");
                    updateBsqWalletTransactions();
                }

                @Override
                public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                }

                @Override
                public void onKeysAdded(List<ECKey> keys) {
                    updateBsqWalletTransactions();
                }

                @Override
                public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {
                    updateBsqWalletTransactions();
                }

                @Override
                public void onWalletChanged(Wallet wallet) {
                    updateBsqWalletTransactions();
                }

            });
        });

        bsqBlockchainManager.addTxOutputMapListener(() -> {
            updateBsqWalletTransactions();
            updateBsqBalance();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Overridden Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    String getWalletAsString(boolean includePrivKeys) {
        return "BSQ wallet:\n" +
                wallet.toString(includePrivKeys, true, true, walletsSetup.getChain()) + "\n\n" +
                "All pubkeys as hex:\n" +
                wallet.printAllPubKeysAsHex();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Balance
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateBsqBalance() {
        availableBsqBalance = bsqCoinSelector.select(NetworkParameters.MAX_MONEY,
                wallet.calculateAllSpendCandidates()).valueGathered;

        unverifiedBalance = Coin.valueOf(getTransactions(false).stream()
                .flatMap(tx -> tx.getOutputs().stream())
                .filter(out -> {
                    final Transaction parentTx = out.getParentTransaction();
                    return parentTx != null &&
                            out.isMine(wallet) &&
                            parentTx.getConfidence().getConfidenceType() == PENDING;
                })
                .mapToLong(out -> out.getValue().value).sum());

        bsqBalanceListeners.stream().forEach(e -> e.updateAvailableBalance(availableBsqBalance, unverifiedBalance));
    }

    @Override
    public Coin getAvailableBalance() {
        return availableBsqBalance;
    }

    public Coin getUnverifiedBalance() {
        return unverifiedBalance;
    }

    public void addBsqBalanceListener(BsqBalanceListener listener) {
        bsqBalanceListeners.add(listener);
    }

    public void removeBsqBalanceListener(BsqBalanceListener listener) {
        bsqBalanceListeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BSQ TransactionOutputs and Transactions
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateBsqWalletTransactions() {
        walletTransactions.setAll(getBsqWalletTransactions());
        updateBsqBalance();
    }

    private Set<Transaction> getBsqWalletTransactions() {
        return getTransactions(false).stream()
                .filter(transaction -> transaction.getConfidence().getConfidenceType() == PENDING ||
                        bsqChainState.containsTx(transaction.getHashAsString()))
                .collect(Collectors.toSet());
    }

    public Set<Transaction> getInvalidBsqTransactions() {
        Set<Transaction> bsqWalletTransactions = getBsqWalletTransactions();
        Set<Transaction> walletTxs = getTransactions(false).stream().collect(Collectors.toSet());
        checkArgument(walletTxs.size() >= bsqWalletTransactions.size(),
                "We cannot have more txsWithOutputsFoundInBsqTxo than walletTxs");
        if (walletTxs.size() == bsqWalletTransactions.size()) {
            // As expected
            return new HashSet<>();
        } else {
            Map<String, Transaction> map = walletTxs.stream()
                    .collect(Collectors.toMap(Transaction::getHashAsString, Function.identity()));

            Set<String> walletTxIds = walletTxs.stream()
                    .map(Transaction::getHashAsString).collect(Collectors.toSet());
            Set<String> bsqTxIds = bsqWalletTransactions.stream()
                    .map(Transaction::getHashAsString).collect(Collectors.toSet());

            walletTxIds.stream()
                    .filter(bsqTxIds::contains)
                    .forEach(map::remove);
            return new HashSet<>(map.values());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Sign tx 
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction signTx(Transaction tx) throws WalletException, TransactionVerificationException {
        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput txIn = tx.getInputs().get(i);
            TransactionOutput connectedOutput = txIn.getConnectedOutput();
            if (connectedOutput != null && connectedOutput.isMine(wallet)) {
                signTransactionInput(wallet, aesKey, tx, txIn, i);
                checkScriptSig(tx, txIn, i);
            }
        }

        checkWalletConsistency(wallet);
        verifyTransaction(tx);
        printTx("BSQ wallet: Signed Tx", tx);
        return tx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Commit tx 
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void commitTx(Transaction tx) {
        wallet.commitTx(tx);
        //printTx("BSQ commit Tx", tx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Broadcast tx 
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void broadcastTx(Transaction tx, FutureCallback<Transaction> callback) throws WalletException, TransactionVerificationException {
        Futures.addCallback(walletsSetup.getPeerGroup().broadcastTransaction(tx).future(), callback);
        printTx("BSQ broadcast Tx", tx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send BSQ with BTC fee
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
        checkWalletConsistency(wallet);
        verifyTransaction(tx);
        // printTx("prepareSendTx", tx);
        return tx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Burn fee tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedBurnFeeTx(Coin fee) throws WalletException, TransactionVerificationException,
            InsufficientMoneyException, ChangeBelowDustException {
        Transaction tx = new Transaction(params);

        // We might have no output if inputs match fee.
        // It will be checked in the final BTC tx that we have min. 1 output by increasing the BTC inputs to force a 
        // non dust BTC output.

        // TODO check dust output
        CoinSelection coinSelection = bsqCoinSelector.select(fee, wallet.calculateAllSpendCandidates());
        coinSelection.gathered.stream().forEach(tx::addInput);
        Coin change = bsqCoinSelector.getChange(fee, coinSelection);
        if (change.isPositive())
            tx.addOutput(change, getUnusedAddress());

        //printTx("getPreparedBurnFeeTx", tx);
        return tx;
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
