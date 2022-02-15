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

import bisq.core.btc.exceptions.BsqChangeBelowDustException;
import bisq.core.btc.exceptions.InsufficientBsqException;
import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.listeners.BsqBalanceListener;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.dao.DaoKillSwitch;
import bisq.core.dao.state.DaoStateListener;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.unconfirmed.UnconfirmedBsqChangeOutputListService;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;
import bisq.core.util.coin.BsqFormatter;

import bisq.common.UserThread;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.SendRequest;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.BUILDING;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.PENDING;

@Slf4j
public class BsqWalletService extends WalletService implements DaoStateListener {

    public interface WalletTransactionsChangeListener {

        void onWalletTransactionsChange();
    }

    private final DaoKillSwitch daoKillSwitch;
    private final BsqCoinSelector bsqCoinSelector;
    private final NonBsqCoinSelector nonBsqCoinSelector;
    private final DaoStateService daoStateService;
    private final UnconfirmedBsqChangeOutputListService unconfirmedBsqChangeOutputListService;
    private final List<Transaction> walletTransactions = new ArrayList<>();
    private final CopyOnWriteArraySet<BsqBalanceListener> bsqBalanceListeners = new CopyOnWriteArraySet<>();
    private final List<WalletTransactionsChangeListener> walletTransactionsChangeListeners = new ArrayList<>();
    private boolean updateBsqWalletTransactionsPending;
    @Getter
    private final BsqFormatter bsqFormatter;


    // balance of non BSQ satoshis
    @Getter
    private Coin availableNonBsqBalance = Coin.ZERO;
    @Getter
    private Coin availableBalance = Coin.ZERO;
    @Getter
    private Coin unverifiedBalance = Coin.ZERO;
    @Getter
    private Coin verifiedBalance = Coin.ZERO;
    @Getter
    private Coin unconfirmedChangeBalance = Coin.ZERO;
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
                            UnconfirmedBsqChangeOutputListService unconfirmedBsqChangeOutputListService,
                            Preferences preferences,
                            FeeService feeService,
                            DaoKillSwitch daoKillSwitch,
                            BsqFormatter bsqFormatter) {
        super(walletsSetup,
                preferences,
                feeService);

        this.bsqCoinSelector = bsqCoinSelector;
        this.nonBsqCoinSelector = nonBsqCoinSelector;
        this.daoStateService = daoStateService;
        this.unconfirmedBsqChangeOutputListService = unconfirmedBsqChangeOutputListService;
        this.daoKillSwitch = daoKillSwitch;
        this.bsqFormatter = bsqFormatter;

        nonBsqCoinSelector.setPreferences(preferences);

        walletsSetup.addSetupCompletedHandler(() -> {
            wallet = walletsSetup.getBsqWallet();
            if (wallet != null) {
                wallet.setCoinSelector(bsqCoinSelector);
                addListenersToWallet();
            }

            BlockChain chain = walletsSetup.getChain();
            if (chain != null) {
                chain.addNewBestBlockListener(block -> chainHeightProperty.set(block.getHeight()));
                chainHeightProperty.set(chain.getBestChainHeight());
            }
        });

        daoStateService.addDaoStateListener(this);
    }

    @Override
    protected void addListenersToWallet() {
        super.addListenersToWallet();

        wallet.addCoinsReceivedEventListener((wallet, tx, prevBalance, newBalance) ->
                updateBsqWalletTransactions()
        );
        wallet.addCoinsSentEventListener((wallet, tx, prevBalance, newBalance) ->
                updateBsqWalletTransactions()
        );
        wallet.addReorganizeEventListener(wallet -> {
            log.warn("onReorganize ");
            updateBsqWalletTransactions();
            unconfirmedBsqChangeOutputListService.onReorganize();
        });
        wallet.addTransactionConfidenceEventListener((wallet, tx) -> {
            // We are only interested in updates from unconfirmed txs and confirmed txs at the
            // time when it gets into a block. Otherwise we would get called
            // updateBsqWalletTransactions for each tx as the block depth changes for all.
            if (tx != null && tx.getConfidence() != null && tx.getConfidence().getDepthInBlocks() <= 1 &&
                    daoStateService.isParseBlockChainComplete()) {
                updateBsqWalletTransactions();
            }
            unconfirmedBsqChangeOutputListService.onTransactionConfidenceChanged(tx);
        });
        wallet.addKeyChainEventListener(keys ->
                updateBsqWalletTransactions()
        );
        wallet.addScriptsChangeEventListener((wallet, scripts, isAddingScripts) ->
                updateBsqWalletTransactions()
        );
        wallet.addChangeEventListener(wallet ->
                updateBsqWalletTransactions()
        );
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onParseBlockCompleteAfterBatchProcessing(Block block) {
        if (isWalletReady()) {
            wallet.getTransactions(false).forEach(unconfirmedBsqChangeOutputListService::onTransactionConfidenceChanged);
            updateBsqWalletTransactions();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Overridden Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    String getWalletAsString(boolean includePrivKeys) {
        return wallet.toString(true, includePrivKeys, this.aesKey, true, true, walletsSetup.getChain()) + "\n\n" +
                "All pubKeys as hex:\n" +
                wallet.printAllPubKeysAsHex();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Balance
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void updateBsqBalance() {
        long ts = System.currentTimeMillis();
        unverifiedBalance = Coin.valueOf(
                getTransactions(false).stream()
                        .filter(tx -> tx.getConfidence().getConfidenceType() == PENDING)
                        .mapToLong(tx -> {
                            // Sum up outputs into BSQ wallet and subtract the inputs using lockup or unlocking
                            // outputs since those inputs will be accounted for in lockupBondsBalance and
                            // unlockingBondsBalance
                            long outputs = tx.getOutputs().stream()
                                    .filter(out -> out.isMine(wallet))
                                    .filter(TransactionOutput::isAvailableForSpending)
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
                                                TxOutputKey key = new TxOutputKey(parentTransaction.getTxId().toString(),
                                                        connectedOutput.getIndex());

                                                return (connectedOutput.isMine(wallet)
                                                        && (daoStateService.isLockupOutput(key)
                                                        || daoStateService.isUnlockingAndUnspent(key)));
                                            }
                                        }
                                        return false;
                                    })
                                    .mapToLong(in -> in.getValue() != null ? in.getValue().value : 0)
                                    .sum();
                            return outputs - lockedInputs;
                        })
                        .sum()
        );

        Set<String> confirmedTxIdSet = getTransactions(false).stream()
                .filter(tx -> tx.getConfidence().getConfidenceType() == BUILDING)
                .map(Transaction::getTxId)
                .map(Sha256Hash::toString)
                .collect(Collectors.toSet());

        lockedForVotingBalance = Coin.valueOf(daoStateService.getUnspentBlindVoteStakeTxOutputs().stream()
                .filter(txOutput -> confirmedTxIdSet.contains(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum());

        lockupBondsBalance = Coin.valueOf(daoStateService.getLockupTxOutputs().stream()
                .filter(txOutput -> daoStateService.isUnspent(txOutput.getKey()))
                .filter(txOutput -> !daoStateService.isConfiscatedLockupTxOutput(txOutput.getTxId()))
                .filter(txOutput -> confirmedTxIdSet.contains(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum());

        unlockingBondsBalance = Coin.valueOf(daoStateService.getUnspentUnlockingTxOutputsStream()
                .filter(txOutput -> confirmedTxIdSet.contains(txOutput.getTxId()))
                .filter(txOutput -> !daoStateService.isConfiscatedUnlockTxOutput(txOutput.getTxId()))
                .mapToLong(TxOutput::getValue)
                .sum());

        availableBalance = bsqCoinSelector.select(NetworkParameters.MAX_MONEY,
                wallet.calculateAllSpendCandidates()).valueGathered;

        if (availableBalance.isNegative())
            availableBalance = Coin.ZERO;

        unconfirmedChangeBalance = unconfirmedBsqChangeOutputListService.getBalance();

        availableNonBsqBalance = nonBsqCoinSelector.select(NetworkParameters.MAX_MONEY,
                wallet.calculateAllSpendCandidates()).valueGathered;

        verifiedBalance = availableBalance.subtract(unconfirmedChangeBalance);

        bsqBalanceListeners.forEach(e -> e.onUpdateBalances(availableBalance, availableNonBsqBalance, unverifiedBalance,
                unconfirmedChangeBalance, lockedForVotingBalance, lockupBondsBalance, unlockingBondsBalance));
        log.info("updateBsqBalance took {} ms", System.currentTimeMillis() - ts);
    }

    public void addBsqBalanceListener(BsqBalanceListener listener) {
        bsqBalanceListeners.add(listener);
    }

    public void removeBsqBalanceListener(BsqBalanceListener listener) {
        bsqBalanceListeners.remove(listener);
    }

    public void addWalletTransactionsChangeListener(WalletTransactionsChangeListener listener) {
        walletTransactionsChangeListeners.add(listener);
    }

    public void removeWalletTransactionsChangeListener(WalletTransactionsChangeListener listener) {
        walletTransactionsChangeListeners.remove(listener);
    }

    public List<TransactionOutput> getSpendableBsqTransactionOutputs() {
        return new ArrayList<>(bsqCoinSelector.select(NetworkParameters.MAX_MONEY,
                wallet.calculateAllSpendCandidates()).gathered);
    }

    public List<TransactionOutput> getSpendableNonBsqTransactionOutputs() {
        return new ArrayList<>(nonBsqCoinSelector.select(NetworkParameters.MAX_MONEY,
                wallet.calculateAllSpendCandidates()).gathered);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BSQ TransactionOutputs and Transactions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<Transaction> getClonedWalletTransactions() {
        return new ArrayList<>(walletTransactions);
    }

    public Stream<Transaction> getPendingWalletTransactionsStream() {
        return walletTransactions.stream()
                .filter(transaction -> transaction.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING);
    }

    private void updateBsqWalletTransactions() {
        if (daoStateService.isParseBlockChainComplete()) {
            // We get called updateBsqWalletTransactions multiple times from onWalletChanged, onTransactionConfidenceChanged
            // and from onParseBlockCompleteAfterBatchProcessing. But as updateBsqBalance is an expensive operation we do
            // not want to call it in a short interval series so we use a flag and a delay to not call it multiple times
            // in a 100 ms period.
            if (!updateBsqWalletTransactionsPending) {
                updateBsqWalletTransactionsPending = true;
                UserThread.runAfter(() -> {
                    walletTransactions.clear();
                    walletTransactions.addAll(getTransactions(false));
                    walletTransactionsChangeListeners.forEach(WalletTransactionsChangeListener::onWalletTransactionsChange);
                    updateBsqBalance();
                    updateBsqWalletTransactionsPending = false;
                }, 100, TimeUnit.MILLISECONDS);
            }
        }
    }

    private Set<Transaction> getBsqWalletTransactions() {
        return getTransactions(false).stream()
                .filter(transaction -> transaction.getConfidence().getConfidenceType() == PENDING ||
                        daoStateService.containsTx(transaction.getTxId().toString()))
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
                    .collect(Collectors.toMap(t -> t.getTxId().toString(), Function.identity()));

            Set<String> walletTxIds = walletTxs.stream()
                    .map(Transaction::getTxId).map(Sha256Hash::toString).collect(Collectors.toSet());
            Set<String> bsqTxIds = bsqWalletTransactions.stream()
                    .map(Transaction::getTxId).map(Sha256Hash::toString).collect(Collectors.toSet());

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
                        Optional<Tx> txOptional = daoStateService.getTx(parentTransaction.getTxId().toString());
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
        final String txId = transaction.getTxId().toString();
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
        return walletTransactions.stream().filter(e -> e.getTxId().toString().equals(txId)).findAny();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Sign tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction signTxAndVerifyNoDustOutputs(Transaction tx)
            throws WalletException, TransactionVerificationException {
        WalletService.signTx(wallet, aesKey, tx);
        WalletService.verifyNonDustTxo(tx);
        return tx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Commit tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void commitTx(Transaction tx, TxType txType) {
        wallet.commitTx(tx);
        //printTx("BSQ commit Tx", tx);

        unconfirmedBsqChangeOutputListService.onCommitTx(tx, txType, wallet);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send BSQ with BTC fee
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedSendBsqTx(String receiverAddress, Coin receiverAmount)
            throws AddressFormatException, InsufficientBsqException, WalletException,
            TransactionVerificationException, BsqChangeBelowDustException {
        return getPreparedSendTx(receiverAddress, receiverAmount, bsqCoinSelector);
    }

    public Transaction getPreparedSendBsqTx(String receiverAddress,
                                            Coin receiverAmount,
                                            @Nullable Set<TransactionOutput> utxoCandidates)
            throws AddressFormatException, InsufficientBsqException, WalletException,
            TransactionVerificationException, BsqChangeBelowDustException {
        if (utxoCandidates != null) {
            bsqCoinSelector.setUtxoCandidates(utxoCandidates);
        }
        return getPreparedSendTx(receiverAddress, receiverAmount, bsqCoinSelector);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send BTC (non-BSQ) with BTC fee (e.g. the issuance output from a  lost comp. request)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedSendBtcTx(String receiverAddress, Coin receiverAmount)
            throws AddressFormatException, InsufficientBsqException, WalletException,
            TransactionVerificationException, BsqChangeBelowDustException {
        return getPreparedSendTx(receiverAddress, receiverAmount, nonBsqCoinSelector);
    }

    public Transaction getPreparedSendBtcTx(String receiverAddress,
                                            Coin receiverAmount,
                                            @Nullable Set<TransactionOutput> utxoCandidates)
            throws AddressFormatException, InsufficientBsqException, WalletException,
            TransactionVerificationException, BsqChangeBelowDustException {
        if (utxoCandidates != null) {
            nonBsqCoinSelector.setUtxoCandidates(utxoCandidates);
        }
        return getPreparedSendTx(receiverAddress, receiverAmount, nonBsqCoinSelector);
    }

    private Transaction getPreparedSendTx(String receiverAddress,
                                          Coin receiverAmount,
                                          BisqDefaultCoinSelector coinSelector)
            throws AddressFormatException, InsufficientBsqException, WalletException, TransactionVerificationException, BsqChangeBelowDustException {
        daoKillSwitch.assertDaoIsNotDisabled();
        Transaction tx = new Transaction(params);
        checkArgument(Restrictions.isAboveDust(receiverAmount),
                "The amount is too low (dust limit).");
        tx.addOutput(receiverAmount, Address.fromString(params, receiverAddress));
        try {
            var selection = coinSelector.select(receiverAmount, wallet.calculateAllSpendCandidates());
            var change = coinSelector.getChange(receiverAmount, selection);
            if (Restrictions.isAboveDust(change)) {
                tx.addOutput(change, getChangeAddress());
            } else if (!change.isZero()) {
                String msg = "BSQ change output is below dust limit. outputValue=" + change.value / 100 + " BSQ";
                log.warn(msg);
                throw new BsqChangeBelowDustException(msg, change);
            }

            SendRequest sendRequest = SendRequest.forTx(tx);
            sendRequest.fee = Coin.ZERO;
            sendRequest.feePerKb = Coin.ZERO;
            sendRequest.ensureMinRequiredFee = false;
            sendRequest.aesKey = aesKey;
            sendRequest.shuffleOutputs = false;
            sendRequest.signInputs = false;
            sendRequest.changeAddress = getChangeAddress();
            sendRequest.coinSelector = coinSelector;
            wallet.completeTx(sendRequest);
            checkWalletConsistency(wallet);
            verifyTransaction(tx);
            coinSelector.setUtxoCandidates(null);   // We reuse the selectors. Reset the transactionOutputCandidates field
            return tx;
        } catch (InsufficientMoneyException e) {
            log.error("getPreparedSendTx: tx={}", tx.toString());
            log.error(e.toString());
            throw new InsufficientBsqException(e.missing);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Burn fee txs
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedTradeFeeTx(Coin fee) throws InsufficientBsqException {
        daoKillSwitch.assertDaoIsNotDisabled();

        Transaction tx = new Transaction(params);
        addInputsAndChangeOutputForTx(tx, fee, bsqCoinSelector);
        return tx;
    }

    // We create a tx with Bsq inputs for the fee and optional BSQ change output.
    // As the fee amount will be missing in the output those BSQ fees are burned.
    public Transaction getPreparedProposalTx(Coin fee) throws InsufficientBsqException {
        return getPreparedTxWithMandatoryBsqChangeOutput(fee);
    }

    public Transaction getPreparedIssuanceTx(Coin fee) throws InsufficientBsqException {
        return getPreparedTxWithMandatoryBsqChangeOutput(fee);
    }

    public Transaction getPreparedProofOfBurnTx(Coin fee) throws InsufficientBsqException {
        return getPreparedTxWithMandatoryBsqChangeOutput(fee);
    }

    public Transaction getPreparedBurnFeeTxForAssetListing(Coin fee) throws InsufficientBsqException {
        return getPreparedTxWithMandatoryBsqChangeOutput(fee);
    }

    // We need to require one BSQ change output as we could otherwise not be able to distinguish between 2
    // structurally same transactions where only the BSQ fee is different. In case of asset listing fee and proof of
    // burn it is a user input, so it is not known to the parser, instead we derive the burned fee from the parser.

    // In case of proposal fee we could derive it from the params.

    // For issuance txs we also require a BSQ change output before the issuance output gets added. There was a
    // minor bug with the old version that multiple inputs would have caused an exception in case there was no
    // change output (e.g. inputs of 21 and 6 BSQ for BSQ fee of 21 BSQ would have caused that only 1 input was used
    // and then caused an error as we enforced a change output. This new version handles such cases correctly.

    // Examples for the structurally indistinguishable transactions:
    // Case 1: 10 BSQ fee to burn
    // In: 17 BSQ
    // Out: BSQ change 7 BSQ -> valid BSQ
    // Out: OpReturn
    // Miner fee: 1000 sat  (10 BSQ burned)

    // Case 2: 17 BSQ fee to burn
    // In: 17 BSQ
    // Out: burned BSQ change 7 BSQ -> BTC (7 BSQ burned)
    // Out: OpReturn
    // Miner fee: 1000 sat  (10 BSQ burned)

    private Transaction getPreparedTxWithMandatoryBsqChangeOutput(Coin fee) throws InsufficientBsqException {
        daoKillSwitch.assertDaoIsNotDisabled();

        Transaction tx = new Transaction(params);
        // We look for inputs covering out BSQ fee we want to pay.
        CoinSelection coinSelection = bsqCoinSelector.select(fee, wallet.calculateAllSpendCandidates());
        try {
            Coin change = bsqCoinSelector.getChange(fee, coinSelection);
            if (change.isZero() || Restrictions.isDust(change)) {
                // If change is zero or below dust we increase required input amount to enforce a BSQ change output.
                // All outputs after that are considered BTC and therefore would be burned BSQ if BSQ is left from what
                // we use for miner fee.

                Coin minDustThreshold = Coin.valueOf(preferences.getIgnoreDustThreshold());
                Coin increasedRequiredInput = fee.add(minDustThreshold);
                coinSelection = bsqCoinSelector.select(increasedRequiredInput, wallet.calculateAllSpendCandidates());
                change = bsqCoinSelector.getChange(fee, coinSelection);

                log.warn("We increased required input as change output was zero or dust: New change value={}", change);
                String info = "Available BSQ balance=" + coinSelection.valueGathered.value / 100 + " BSQ. " +
                        "Intended fee to burn=" + fee.value / 100 + " BSQ. " +
                        "Please increase your balance to at least " + (fee.value + minDustThreshold.value) / 100 + " BSQ.";
                checkArgument(coinSelection.valueGathered.compareTo(fee) > 0,
                        "This transaction require a change output of at least " + minDustThreshold.value / 100 + " BSQ (dust limit). " +
                                info);

                checkArgument(!Restrictions.isDust(change),
                        "This transaction would create a dust output of " + change.value / 100 + " BSQ. " +
                                "It requires a change output of at least " + minDustThreshold.value / 100 + " BSQ (dust limit). " +
                                info);
            }

            coinSelection.gathered.forEach(tx::addInput);
            tx.addOutput(change, getChangeAddress());

            return tx;

        } catch (InsufficientMoneyException e) {
            log.error("coinSelection.gathered={}", coinSelection.gathered);
            throw new InsufficientBsqException(e.missing);
        }
    }

    private void addInputsAndChangeOutputForTx(Transaction tx,
                                               Coin fee,
                                               BsqCoinSelector bsqCoinSelector)
            throws InsufficientBsqException {
        Coin requiredInput;
        // If our fee is less then dust limit we increase it so we are sure to not get any dust output.
        if (Restrictions.isDust(fee)) {
            requiredInput = fee.add(Restrictions.getMinNonDustOutput());
        } else {
            requiredInput = fee;
        }

        CoinSelection coinSelection = bsqCoinSelector.select(requiredInput, wallet.calculateAllSpendCandidates());
        coinSelection.gathered.forEach(tx::addInput);
        try {
            Coin change = bsqCoinSelector.getChange(fee, coinSelection);
            // Change can be ZERO, then no change output is created so don't rely on a BSQ change output
            if (change.isPositive()) {
                checkArgument(Restrictions.isAboveDust(change),
                        "The change output of " + change.value / 100d + " BSQ is below the min. dust value of "
                                + Restrictions.getMinNonDustOutput().value / 100d +
                                ". At least " + Restrictions.getMinNonDustOutput().add(fee).value / 100d +
                                " BSQ is needed for this transaction");
                tx.addOutput(change, getChangeAddress());
            }
        } catch (InsufficientMoneyException e) {
            log.error(tx.toString());
            throw new InsufficientBsqException(e.missing);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqSwap tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Tuple2<List<RawTransactionInput>, Coin> getBuyersBsqInputsForBsqSwapTx(Coin required)
            throws InsufficientBsqException {
        daoKillSwitch.assertDaoIsNotDisabled();
        // As unconfirmed BSQ inputs cannot be verified by the peer we can only use confirmed BSQ.
        boolean prev = bsqCoinSelector.isAllowSpendMyOwnUnconfirmedTxOutputs();
        bsqCoinSelector.setAllowSpendMyOwnUnconfirmedTxOutputs(false);
        CoinSelection coinSelection = bsqCoinSelector.select(required, wallet.calculateAllSpendCandidates());
        Coin change;
        try {
            change = bsqCoinSelector.getChange(required, coinSelection);
        } catch (InsufficientMoneyException e) {
            throw new InsufficientBsqException(e.missing);
        } finally {
            bsqCoinSelector.setAllowSpendMyOwnUnconfirmedTxOutputs(prev);
        }

        Transaction dummyTx = new Transaction(params);
        coinSelection.gathered.forEach(dummyTx::addInput);
        List<RawTransactionInput> inputs = dummyTx.getInputs().stream()
                .map(RawTransactionInput::new)
                .collect(Collectors.toList());
        return new Tuple2<>(inputs, change);
    }

    public void signBsqSwapTransaction(Transaction transaction, List<TransactionInput> myInputs)
            throws TransactionVerificationException {
        for (TransactionInput input : myInputs) {
            TransactionOutput connectedOutput = input.getConnectedOutput();
            checkNotNull(connectedOutput, "connectedOutput must not be null");
            checkArgument(connectedOutput.isMine(wallet), "connectedOutput is not mine");
            signTransactionInput(wallet, aesKey, transaction, input, input.getIndex());
            checkScriptSig(transaction, input, input.getIndex());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Blind vote tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We create a tx with Bsq inputs for the fee, one output for the stake and optional one BSQ change output.
    // As the fee amount will be missing in the output those BSQ fees are burned.
    public Transaction getPreparedBlindVoteTx(Coin fee, Coin stake) throws InsufficientBsqException {
        daoKillSwitch.assertDaoIsNotDisabled();
        Transaction tx = new Transaction(params);
        tx.addOutput(new TransactionOutput(params, tx, stake, getUnusedAddress()));
        addInputsAndChangeOutputForTx(tx, fee.add(stake), bsqCoinSelector);
        //printTx("getPreparedBlindVoteTx", tx);
        return tx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MyVote reveal tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedVoteRevealTx(TxOutput stakeTxOutput) {
        daoKillSwitch.assertDaoIsNotDisabled();
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
        daoKillSwitch.assertDaoIsNotDisabled();
        Transaction tx = new Transaction(params);
        checkArgument(Restrictions.isAboveDust(lockupAmount), "The amount is too low (dust limit).");
        tx.addOutput(new TransactionOutput(params, tx, lockupAmount, getUnusedAddress()));
        addInputsAndChangeOutputForTx(tx, lockupAmount, bsqCoinSelector);
        printTx("prepareLockupTx", tx);
        return tx;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Unlock bond tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getPreparedUnlockTx(TxOutput lockupTxOutput) throws AddressFormatException {
        daoKillSwitch.assertDaoIsNotDisabled();
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

    private Address getChangeAddress() {
        return getUnusedAddress();
    }

    public Address getUnusedAddress() {
        return wallet.getIssuedReceiveAddresses().stream()
                .filter(address -> Script.ScriptType.P2WPKH.equals(address.getOutputScriptType()))
                .filter(this::isAddressUnused)
                .findAny()
                .orElse(wallet.freshReceiveAddress());
    }

    public String getUnusedBsqAddressAsString() {
        return "B" + getUnusedAddress().toString();
    }

    // For BSQ we do not check for dust attack utxos as they are 5.46 BSQ and a considerable value.
    // The default 546 sat dust limit is handled in the BitcoinJ side anyway.
    @Override
    protected boolean isDustAttackUtxo(TransactionOutput output) {
        return false;
    }
}
