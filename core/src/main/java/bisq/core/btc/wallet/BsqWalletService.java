/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.btc.wallet;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.exceptions.InsufficientBsqException;
import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ScriptException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.AbstractWalletEventListener;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.BUILDING;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.PENDING;

@Slf4j
public class BsqWalletService extends WalletService implements DaoStateListener {
    private final BsqCoinSelector bsqCoinSelector;
    private final NonBsqCoinSelector nonBsqCoinSelector;
    private final DaoStateService daoStateService;
    private final ObservableList<Transaction> walletTransactions = FXCollections.observableArrayList();
    private final CopyOnWriteArraySet<BsqBalanceListener> bsqBalanceListeners = new CopyOnWriteArraySet<>();

    //TODO add prog arg to override that value
    private final int numPreferredUtxoPoolSize = 5;

    // balance of non BSQ satoshis
    @Getter
    private Coin availableNonBsqBalance = Coin.ZERO;
    @Getter
    private Coin availableBalance = Coin.ZERO;
    @Getter
    private Coin unverifiedBalance = Coin.ZERO;
    @Getter
    private Coin lockedForVotingBalance = Coin.ZERO;
    @Getter
    private Coin lockupBondsBalance = Coin.ZERO;
    @Getter
    private Coin unlockingBondsBalance = Coin.ZERO;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqWalletService(WalletsSetup walletsSetup,
                            BsqCoinSelector bsqCoinSelector,
                            NonBsqCoinSelector nonBsqCoinSelector,
                            DaoStateService daoStateService,
                            Preferences preferences,
                            FeeService feeService) {
        super(walletsSetup,
                preferences,
                feeService);

        this.bsqCoinSelector = bsqCoinSelector;
        this.nonBsqCoinSelector = nonBsqCoinSelector;
        this.daoStateService = daoStateService;

        if (BisqEnvironment.isBaseCurrencySupportingBsq()) {
            walletsSetup.addSetupCompletedHandler(() -> {
                wallet = walletsSetup.getBsqWallet();
                if (wallet != null) {
                    wallet.setCoinSelector(bsqCoinSelector);
                    wallet.addEventListener(walletEventListener);

                    //noinspection deprecation
                    wallet.addEventListener(new AbstractWalletEventListener() {
                        @Override
                        public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                            updateBsqWalletTransactions();
                        }

                        @Override
                        public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                            updateBsqWalletTransactions();
                        }

                        @Override
                        public void onReorganize(Wallet wallet) {
                            log.warn("onReorganize ");
                            updateBsqWalletTransactions();
                        }

                        @Override
                        public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
                            updateBsqWalletTransactions();
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
                }

                final BlockChain chain = walletsSetup.getChain();
                if (chain != null) {
                    chain.addNewBestBlockListener(block -> chainHeightProperty.set(block.getHeight()));
                    chainHeightProperty.set(chain.getBestChainHeight());
                    updateBsqWalletTransactions();
                }
            });
        }

        daoStateService.addBsqStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
        if (isWalletReady())
            updateBsqWalletTransactions();
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Overridden Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    String getWalletAsString(boolean includePrivKeys) {
        return wallet.toString(includePrivKeys, true, true, walletsSetup.getChain()) + "\n\n" +
                "All pubKeys as hex:\n" +
                wallet.printAllPubKeysAsHex();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Balance
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateBsqBalance() {
        unverifiedBalance = Coin.valueOf(
                getTransactions(false).stream()
                        .filter(tx -> tx.getConfidence().getConfidenceType() == PENDING)
                        .mapToLong(tx -> {
                            // Sum up outputs into BSQ wallet and subtract the inputs using lockup or unlocking
                            // outputs since those inputs will be accounted for in lockupBondsBalance and
                            // unlockingBondsBalance
                            long outputs = tx.getOutputs().stream()
                                    .filter(out -> out.isMine(wallet))
                                    .mapToLong(out -> out.getValue().value)
                                    .sum();
                            // Account for spending of locked connectedOutputs
                            long lockedInputs = tx.getInputs().stream()
                                    .filter(in -> {
                                        TransactionOutput connectedOutput = in.getConnectedOutput();
                                        if (connectedOutput != null) {
                                            Transaction parentTransaction = connectedOutput.getParentTransaction();
                                            // TODO SQ
                                            if (parentTransaction != null/* &&
                                                    parentTransaction.getConfidence().getConfidenceType() == BUILDING*/) {
                                                TxOutputKey key = new TxOutputKey(parentTransaction.getHashAsString(),
                                                        connectedOutput.getIndex());

                                                return (connectedOutput.isMine(wallet)
                                                        && (daoStateService.isLockupOutput(key)
                                                        || daoStateService.isUnlockingAndUnspent(key)));
                                            }
                                        }
                                        return false;
                                    })
                                    .mapToLong(in -> in != null ? in.getValue().value : 0)
                                    .sum();
                            return outputs - lockedInputs;
                        })
                        .sum()
        );
        Set<String> confirmedTxIdSet = getTransactions(false).stream()
                .filter(tx -> tx.getConfidence().getConfidenceType() == BUILDING)
                .map(Transaction::getHashAsString)
                .collect(Collectors.toSet());

        lockedForVotingBalance = Coin.valueOf(daoStateService.getUnspentBlindVoteStakeTxOutputs().stream()
                .filter(txOutput -> confirmedTxIdSet.contains(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum());
        lockupBondsBalance = Coin.valueOf(daoStateService.getLockupTxOutputs().stream()
                .filter(txOutput -> daoStateService.isUnspent(txOutput.getKey()))
                /*.filter(txOutput -> !daoStateService.isSpentByUnlockTx(txOutput))*/ // TODO SQ
                .filter(txOutput -> confirmedTxIdSet.contains(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum());

        unlockingBondsBalance = Coin.valueOf(daoStateService.getUnspentUnlockingTxOutputsStream()
                .filter(txOutput -> confirmedTxIdSet.contains(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum());

        availableBalance = bsqCoinSelector.select(NetworkParameters.MAX_MONEY,
                wallet.calculateAllSpendCandidates()).valueGathered;

        if (availableBalance.isNegative())
            availableBalance = Coin.ZERO;

        availableNonBsqBalance = nonBsqCoinSelector.select(NetworkParameters.MAX_MONEY,
                wallet.calculateAllSpendCandidates()).valueGathered;

        bsqBalanceListeners.forEach(e -> e.onUpdateBalances(availableBalance, availableNonBsqBalance, unverifiedBalance,
                lockedForVotingBalance, lockupBondsBalance, unlockingBondsBalance));
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

    public ObservableList<Transaction> getWalletTransactions() {
        return walletTransactions;
    }

    public Stream<Transaction> getPendingWalletTransactionsStream() {
        return walletTransactions.stream()
                .filter(transaction -> transaction.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING);
    }

    private void updateBsqWalletTransactions() {
        walletTransactions.setAll(getTransactions(false));
        // walletTransactions.setAll(getBsqWalletTransactions());
        updateBsqBalance();
    }

    private Set<Transaction> getBsqWalletTransactions() {
        return getTransactions(false).stream()
                .filter(transaction -> transaction.getConfidence().getConfidenceType() == PENDING ||
                        daoStateService.containsTx(transaction.getHashAsString()))
                .collect(Collectors.toSet());
    }

    public Set<Transaction> getUnverifiedBsqTransactions() {
        Set<Transaction> bsqWalletTransactions = getBsqWalletTransactions();
        Set<Transaction> walletTxs = new HashSet<>(getTransactions(false));
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

    @Override
    public Coin getValueSentFromMeForTransaction(Transaction transaction) throws ScriptException {
        Coin result = Coin.ZERO;
        // We check all our inputs and get the connected outputs.
        for (int i = 0; i < transaction.getInputs().size(); i++) {
            TransactionInput input = transaction.getInputs().get(i);
            // We grab the connected output for that input
            TransactionOutput connectedOutput = input.getConnectedOutput();
            if (connectedOutput != null) {
                // We grab the parent tx of the connected output
                final Transaction parentTransaction = connectedOutput.getParentTransaction();
                final boolean isConfirmed = parentTransaction != null &&
                        parentTransaction.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING;
                if (connectedOutput.isMineOrWatched(wallet)) {
                    if (isConfirmed) {
                        // We lookup if we have a BSQ tx matching the parent tx
                        // We cannot make that findTx call outside of the loop as the parent tx can change at each iteration
                        Optional<Tx> txOptional = daoStateService.getTx(parentTransaction.getHash().toString());
                        if (txOptional.isPresent()) {
                            TxOutput txOutput = txOptional.get().getTxOutputs().get(connectedOutput.getIndex());
                            if (daoStateService.isBsqTxOutputType(txOutput)) {
                                //TODO check why values are not the same
                                if (txOutput.getValue() != connectedOutput.getValue().value)
                                    log.warn("getValueSentToMeForTransaction: Value of BSQ output do not match BitcoinJ tx output. " +
                                                    "txOutput.getValue()={}, output.getValue().value={}, txId={}",
                                            txOutput.getValue(), connectedOutput.getValue().value, txOptional.get().getId());

                                // If it is a valid BSQ output we add it
                                result = result.add(Coin.valueOf(txOutput.getValue()));
                            }
                        }
                    } /*else {
                        // TODO atm we don't display amounts of unconfirmed txs but that might change so we leave that code
                        // if it will be required
                        // If the tx is not confirmed yet we add the value and assume it is a valid BSQ output.
                        result = result.add(connectedOutput.getValue());
                    }*/
                }
            }
        }
        return result;
    }

    @Override
    public Coin getValueSentToMeForTransaction(Transaction transaction) throws ScriptException {
        Coin result = Coin.ZERO;
        final String txId = transaction.getHashAsString();
        // We check if we have a matching BSQ tx. We do that call here to avoid repeated calls in the loop.
        Optional<Tx> txOptional = daoStateService.getTx(txId);
        // We check all the outputs of our tx
        for (int i = 0; i < transaction.getOutputs().size(); i++) {
            TransactionOutput output = transaction.getOutputs().get(i);
            final boolean isConfirmed = output.getParentTransaction() != null &&
                    output.getParentTransaction().getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING;
            if (output.isMineOrWatched(wallet)) {
                if (isConfirmed) {
                    if (txOptional.isPresent()) {
                        // The index of the BSQ tx outputs are the same like the bitcoinj tx outputs
                        TxOutput txOutput = txOptional.get().getTxOutputs().get(i);
                        if (daoStateService.isBsqTxOutputType(txOutput)) {
                            //TODO check why values are not the same
                            if (txOutput.getValue() != output.getValue().value) {
                                log.warn("getValueSentToMeForTransaction: Value of BSQ output do not match BitcoinJ tx output. " +
                                                "txOutput.getValue()={}, output.getValue().value={}, txId={}",
                                        txOutput.getValue(), output.getValue().value, txId);
                            }

                            // If it is a valid BSQ output we add it
                            result = result.add(Coin.valueOf(txOutput.getValue()));
                        }
                    }
                } /*else {
                    // TODO atm we don't display amounts of unconfirmed txs but that might change so we leave that code
                    // if it will be required
                    // If the tx is not confirmed yet we add the value and assume it is a valid BSQ output.
                    result = result.add(output.getValue());
                }*/
            }
        }
        return result;
    }

    public Optional<Transaction> isWalletTransaction(String txId) {
        return getWalletTransactions().stream().filter(e -> e.getHashAsString().equals(txId)).findAny();
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
    // Send BSQ with BTC fee
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedSendTx(String receiverAddress, Coin receiverAmount)
            throws AddressFormatException, InsufficientBsqException, WalletException, TransactionVerificationException {
        int numMissingUtxos = getNumMissingUtxos();
        Transaction tx = new Transaction(params);
        checkArgument(Restrictions.isAboveDust(receiverAmount),
                "The amount is too low (dust limit).");
        tx.addOutput(receiverAmount, Address.fromBase58(params, receiverAddress));
        Transaction prepareSendTx = completePreparedSendTx(tx);

        // If we have a change output and not enough utxo in our wallet we add additional outputs
        if (numMissingUtxos > 0 && prepareSendTx.getOutputs().size() == 2) {
            Coin change = prepareSendTx.getOutputs().get(1).getValue();

            // We recreate the base tx with the receiver
            tx = new Transaction(params);
            tx.addOutput(receiverAmount, Address.fromBase58(params, receiverAddress));

            // And add additional outputs. We will not get a change output anymore at completePreparedSendTx as we
            // spend all available change to custom outputs.
            boolean wasAnyOutputAdded = addChangeOutputs(tx, change, numMissingUtxos);
            if (wasAnyOutputAdded)
                prepareSendTx = completePreparedSendTx(tx);
        }

        long sumInPuts = tx.getInputs().stream()
                .filter(e -> e.getValue() != null)
                .mapToLong(e -> e.getValue().getValue())
                .sum();
        long sumOutPuts = tx.getOutputs().stream()
                .mapToLong(e -> e.getValue().getValue())
                .sum();
        checkArgument(sumInPuts == sumOutPuts, "Sum of all inputs must match sum of all outputs.");

        printTx("prepareSendTx", prepareSendTx);
        return prepareSendTx;
    }

    private boolean addChangeOutputs(Transaction tx, Coin change, int numMissingUtxos) {
        long remaining = change.value;
        long dust = Restrictions.getMinNonDustOutput().value;
        boolean wasAnyOutputAdded = false;
        for (int i = 0; i < numMissingUtxos; i++) {
            if (remaining > 0) {
                long outputValue;
                if (i < numMissingUtxos - 1) {
                    long average = remaining / (numMissingUtxos - i);
                    int random = new Random().nextInt(1000);
                    // We add dust to be sure to not get too low outputs, as we want
                    // outputs not too close to dust we don't care if the average + random would be already covering
                    // dust limit
                    outputValue = average + dust + random;
                    remaining -= outputValue;
                    if (remaining <= dust) {
                        if (remaining > 0) {
                            log.info("We would have a remaining value which would create a dust output, so we add it to our " +
                                    "current change output. remaining={}", remaining);
                        }
                        // We also get negative values here if we would have spent too much, so that gets corrected here
                        // as well, but we don't log that.
                        outputValue += remaining;
                        remaining = 0;
                    }
                } else {
                    // at last output we use the remaining value
                    outputValue = remaining;
                }

                // We don't use the getUnusedAddress methods as we want to guarantee that the addresses are
                // different
                tx.addOutput(Coin.valueOf(outputValue), getNewAddress());
                wasAnyOutputAdded = true;
            }
        }
        return wasAnyOutputAdded;
    }

    private Transaction completePreparedSendTx(Transaction tx)
            throws AddressFormatException, InsufficientBsqException, WalletException, TransactionVerificationException {

        SendRequest sendRequest = SendRequest.forTx(tx);
        sendRequest.fee = Coin.ZERO;
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.ensureMinRequiredFee = false;
        sendRequest.aesKey = aesKey;
        sendRequest.shuffleOutputs = false;
        sendRequest.signInputs = false;
        sendRequest.ensureMinRequiredFee = false;
        sendRequest.changeAddress = getNewAddress();
        try {
            wallet.completeTx(sendRequest);
        } catch (InsufficientMoneyException e) {
            throw new InsufficientBsqException(e.missing);
        }
        checkWalletConsistency(wallet);
        verifyTransaction(tx);
        return tx;
    }

    private int getNumMissingUtxos() {
        return Math.max(0, numPreferredUtxoPoolSize - getNumUtxos());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send BTC (non-BSQ) with BTC fee (e.g. the issuance output from a  lost comp. request)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedSendBtcTx(String receiverAddress, Coin receiverAmount)
            throws AddressFormatException, InsufficientBsqException, WalletException, TransactionVerificationException {
        Transaction tx = new Transaction(params);
        checkArgument(Restrictions.isAboveDust(receiverAmount),
                "The amount is too low (dust limit).");
        tx.addOutput(receiverAmount, Address.fromBase58(params, receiverAddress));

        SendRequest sendRequest = SendRequest.forTx(tx);
        sendRequest.fee = Coin.ZERO;
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.ensureMinRequiredFee = false;
        sendRequest.aesKey = aesKey;
        sendRequest.shuffleOutputs = false;
        sendRequest.signInputs = false;
        sendRequest.ensureMinRequiredFee = false;
        sendRequest.changeAddress = getUnusedAddress();
        sendRequest.coinSelector = nonBsqCoinSelector;
        try {
            wallet.completeTx(sendRequest);
        } catch (InsufficientMoneyException e) {
            throw new InsufficientBsqException(e.missing);
        }
        checkWalletConsistency(wallet);
        verifyTransaction(tx);
        // printTx("prepareSendTx", tx);
        return tx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Burn fee tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We create a tx with Bsq inputs for the fee and optional BSQ change output.
    // As the fee amount will be missing in the output those BSQ fees are burned.

    public Transaction getPreparedTradeFeeTx(Coin fee) throws InsufficientBsqException {
        return getTxWithMultipleChangeOutputs(fee);
    }

    public Transaction getPreparedProposalTx(Coin fee) throws InsufficientBsqException {
        return getTxWithMultipleChangeOutputs(fee);
    }

    private Transaction getTxWithMultipleChangeOutputs(Coin fee) throws InsufficientBsqException {
        Transaction tx = new Transaction(params);
        addInputsAndGetOptionalChange(tx, fee, bsqCoinSelector).ifPresent(change -> {
            // We add multiple outputs for the change in case we don't have sufficient utxos.
            // That way we avoid that users have to wait for one confirmation to make another tx in case there was only
            // one utxo available and used up in the tx.
            // We only add the change as output, the fee gets spent to miners as BTC satoshis.
            addChangeOutputs(tx, change, getNumMissingUtxos());
        });
        printTx("getPreparedFeeTx", tx);
        return tx;
    }

    public Transaction getPreparedIssuanceCandidateTx(Coin fee) throws InsufficientBsqException {
        // Compensation requests require defined output structure so we cannot add multiple change outputs.
        return getTxWithSingleChangeOutput(fee);
    }

    public Transaction getPreparedProofOfBurnTx(Coin fee) throws InsufficientBsqException {
        // We don't add multiple change outputs as the consensus rules for Proof of burn requires a single BSQ output!
        return getTxWithSingleChangeOutput(fee);
    }

    public Transaction getPreparedAssetListingFeeTx(Coin fee) throws InsufficientBsqException {
        // We don't add multiple change outputs as the consensus rules for Asset listing fee requires a single BSQ output!
        return getTxWithSingleChangeOutput(fee);
    }

    private Transaction getTxWithSingleChangeOutput(Coin fee) throws InsufficientBsqException {
        Transaction tx = new Transaction(params);
        addInputsAndGetOptionalChange(tx, fee, bsqCoinSelector)
                .ifPresent(change -> addChangeOutput(tx, change));
        return tx;
    }

    private void addChangeOutput(Transaction tx, Coin change) {
        if (change.isPositive()) {
            checkArgument(Restrictions.isAboveDust(change), "We must not get dust output here.");
            tx.addOutput(change, getUnusedAddress());
        }
    }

    private Optional<Coin> addInputsAndGetOptionalChange(Transaction tx, Coin amountToSpend, BsqCoinSelector bsqCoinSelector)
            throws InsufficientBsqException {
        try {
            Coin requiredInput;
            // If our fee is less then dust limit we increase it so we are sure to not get any dust output.
            if (Restrictions.isDust(amountToSpend))
                requiredInput = Restrictions.getMinNonDustOutput().add(amountToSpend);
            else
                requiredInput = amountToSpend;

            CoinSelection coinSelection = bsqCoinSelector.select(requiredInput, wallet.calculateAllSpendCandidates());
            coinSelection.gathered.forEach(tx::addInput);
            // Fee is target value in getChange method. We check what is remaining from available input and the amount
            // we want to spend, which is the fee in our case here.
            Coin change = bsqCoinSelector.getChange(amountToSpend, coinSelection);
            return change.isPositive() ? Optional.of(change) : Optional.empty();
        } catch (InsufficientMoneyException e) {
            throw new InsufficientBsqException(e.missing);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Blind vote tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We create a tx with Bsq inputs for the fee, one output for the stake and optional one BSQ change output.
    // As the fee amount will be missing in the output those BSQ fees are burned.
    public Transaction getPreparedBlindVoteTx(Coin fee, Coin stake) throws InsufficientBsqException {
        Transaction tx = new Transaction(params);
        tx.addOutput(new TransactionOutput(params, tx, stake, getUnusedAddress()));
        Coin amountToSpend = fee.add(stake);
        addInputsAndGetOptionalChange(tx, amountToSpend, bsqCoinSelector)
                .ifPresent(change -> addChangeOutputs(tx, change, getNumMissingUtxos()));
        printTx("getPreparedBlindVoteTx", tx);
        return tx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MyVote reveal tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedVoteRevealTx(TxOutput stakeTxOutput) {
        Transaction tx = new Transaction(params);
        final Coin stake = Coin.valueOf(stakeTxOutput.getValue());
        Transaction blindVoteTx = getTransaction(stakeTxOutput.getTxId());
        checkNotNull(blindVoteTx, "blindVoteTx must not be null");
        TransactionOutPoint outPoint = new TransactionOutPoint(params, stakeTxOutput.getIndex(), blindVoteTx);
        // Input is not signed yet so we use new byte[]{}
        tx.addInput(new TransactionInput(params, tx, new byte[]{}, outPoint, stake));
        tx.addOutput(new TransactionOutput(params, tx, stake, getUnusedAddress()));
        // printTx("getPreparedVoteRevealTx", tx);
        return tx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lockup bond tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedLockupTx(Coin lockupAmount) throws AddressFormatException, InsufficientBsqException {
        Transaction tx = new Transaction(params);
        checkArgument(Restrictions.isAboveDust(lockupAmount), "The amount is too low (dust limit).");
        tx.addOutput(new TransactionOutput(params, tx, lockupAmount, getUnusedAddress()));
        addInputsAndGetOptionalChange(tx, lockupAmount, bsqCoinSelector)
                .ifPresent(change -> addChangeOutputs(tx, change, getNumMissingUtxos()));
        printTx("prepareLockupTx", tx);
        return tx;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Unlock bond tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedUnlockTx(TxOutput lockupTxOutput) throws AddressFormatException {
        Transaction tx = new Transaction(params);
        // Unlocking means spending the full value of the locked txOutput to another txOutput with the same value
        Coin amountToUnlock = Coin.valueOf(lockupTxOutput.getValue());
        checkArgument(Restrictions.isAboveDust(amountToUnlock), "The amount is too low (dust limit).");
        Transaction lockupTx = getTransaction(lockupTxOutput.getTxId());
        checkNotNull(lockupTx, "lockupTx must not be null");
        TransactionOutPoint outPoint = new TransactionOutPoint(params, lockupTxOutput.getIndex(), lockupTx);
        // Input is not signed yet so we use new byte[]{}
        tx.addInput(new TransactionInput(params, tx, new byte[]{}, outPoint, amountToUnlock));
        tx.addOutput(new TransactionOutput(params, tx, amountToUnlock, getUnusedAddress()));
        printTx("prepareUnlockTx", tx);
        return tx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Addresses
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected Set<Address> getAllAddressesFromActiveKeys() {
        return wallet.getActiveKeychain().getLeafKeys().stream().
                map(key -> Address.fromP2SHHash(params, key.getPubKeyHash())).
                collect(Collectors.toSet());
    }

    public Address getUnusedAddress() {
        return wallet.getIssuedReceiveAddresses().stream()
                .filter(this::isAddressUnused)
                .findAny()
                .orElse(wallet.freshReceiveAddress());
    }

    public Address getNewAddress() {
        return wallet.freshReceiveAddress();
    }

    public String getUnusedBsqAddressAsString() {
        return "B" + getUnusedAddress().toBase58();
    }
}
