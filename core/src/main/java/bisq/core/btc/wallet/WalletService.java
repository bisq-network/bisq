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

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.listeners.AddressConfidenceListener;
import bisq.core.btc.listeners.BalanceListener;
import bisq.core.btc.listeners.OutputSpendConfidenceListener;
import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.http.MemPoolSpaceTxBroadcaster;
import bisq.core.crypto.LowRSigningKey;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Preferences;

import bisq.common.config.Config;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.signers.TransactionSigner;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.DecryptingKeyBag;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyBag;
import org.bitcoinj.wallet.RedeemData;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.bitcoinj.wallet.listeners.WalletReorganizeEventListener;

import javax.inject.Inject;

import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.SetMultimap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import org.bouncycastle.crypto.params.KeyParameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.BUILDING;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.DEAD;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.PENDING;

/**
 * Abstract base class for BTC and BSQ wallet. Provides all non-trade specific functionality.
 */
@Slf4j
public abstract class WalletService {
    protected final WalletsSetup walletsSetup;
    protected final Preferences preferences;
    protected final FeeService feeService;
    protected final NetworkParameters params;
    private final BisqWalletListener walletEventListener = new BisqWalletListener();
    private final CopyOnWriteArraySet<AddressConfidenceListener> addressConfidenceListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<TxConfidenceListener> txConfidenceListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<OutputSpendConfidenceListener> spendConfidenceListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<BalanceListener> balanceListeners = new CopyOnWriteArraySet<>();
    private final WalletChangeEventListener cacheInvalidationListener;
    private final AtomicReference<Multiset<Address>> txOutputAddressCache = new AtomicReference<>();
    private final AtomicReference<SetMultimap<Address, Transaction>> addressToMatchingTxSetCache = new AtomicReference<>();
    @Getter
    protected Wallet wallet;
    @Getter
    protected KeyParameter aesKey;
    @Getter
    protected IntegerProperty chainHeightProperty = new SimpleIntegerProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    WalletService(WalletsSetup walletsSetup,
                  Preferences preferences,
                  FeeService feeService) {
        this.walletsSetup = walletsSetup;
        this.preferences = preferences;
        this.feeService = feeService;

        params = walletsSetup.getParams();

        cacheInvalidationListener = wallet -> {
            txOutputAddressCache.set(null);
            addressToMatchingTxSetCache.set(null);
        };
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void addListenersToWallet() {
        wallet.addCoinsReceivedEventListener(walletEventListener);
        wallet.addCoinsSentEventListener(walletEventListener);
        wallet.addReorganizeEventListener(walletEventListener);
        wallet.addTransactionConfidenceEventListener(walletEventListener);
        wallet.addChangeEventListener(Threading.SAME_THREAD, cacheInvalidationListener);
    }

    public void shutDown() {
        if (wallet != null) {
            wallet.removeCoinsReceivedEventListener(walletEventListener);
            wallet.removeCoinsSentEventListener(walletEventListener);
            wallet.removeReorganizeEventListener(walletEventListener);
            wallet.removeTransactionConfidenceEventListener(walletEventListener);
            wallet.removeChangeEventListener(cacheInvalidationListener);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    void decryptWallet(@NotNull KeyParameter key) {
        wallet.decrypt(key);
        aesKey = null;
    }

    void encryptWallet(KeyCrypterScrypt keyCrypterScrypt, KeyParameter key) {
        if (this.aesKey != null) {
            log.warn("encryptWallet called but we have a aesKey already set. " +
                    "We decryptWallet with the old key before we apply the new key.");
            decryptWallet(this.aesKey);
        }

        wallet.encrypt(keyCrypterScrypt, key);
        aesKey = key;
    }

    void setAesKey(KeyParameter aesKey) {
        this.aesKey = aesKey;
    }

    abstract String getWalletAsString(boolean includePrivKeys);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addAddressConfidenceListener(AddressConfidenceListener listener) {
        addressConfidenceListeners.add(listener);
    }

    public void removeAddressConfidenceListener(AddressConfidenceListener listener) {
        addressConfidenceListeners.remove(listener);
    }

    public void addTxConfidenceListener(TxConfidenceListener listener) {
        txConfidenceListeners.add(listener);
    }

    public void removeTxConfidenceListener(TxConfidenceListener listener) {
        txConfidenceListeners.remove(listener);
    }

    public void addSpendConfidenceListener(OutputSpendConfidenceListener listener) {
        spendConfidenceListeners.add(listener);
    }

    public void removeSpendConfidenceListener(OutputSpendConfidenceListener listener) {
        spendConfidenceListeners.remove(listener);
    }

    public void addBalanceListener(BalanceListener listener) {
        balanceListeners.add(listener);
    }

    public void removeBalanceListener(BalanceListener listener) {
        balanceListeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Checks
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void checkWalletConsistency(Wallet wallet) throws WalletException {
        try {
            checkNotNull(wallet);
            checkState(wallet.isConsistent());
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new WalletException(t);
        }
    }

    public static void verifyTransaction(Transaction transaction) throws TransactionVerificationException {
        try {
            transaction.verify();
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new TransactionVerificationException(t);
        }
    }

    public static void checkAllScriptSignaturesForTx(Transaction transaction) throws TransactionVerificationException {
        for (int i = 0; i < transaction.getInputs().size(); i++) {
            WalletService.checkScriptSig(transaction, transaction.getInputs().get(i), i);
        }
    }

    public static void checkScriptSig(Transaction transaction,
                                      TransactionInput input,
                                      int inputIndex) throws TransactionVerificationException {
        try {
            checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
            input.getScriptSig().correctlySpends(transaction, inputIndex, input.getWitness(), input.getValue(), input.getConnectedOutput().getScriptPubKey(), Script.ALL_VERIFY_FLAGS);
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new TransactionVerificationException(t);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Sign tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void signTx(Wallet wallet,
                              @Nullable KeyParameter aesKey,
                              Transaction tx)
            throws WalletException, TransactionVerificationException {
        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInput(i);
            TransactionOutput connectedOutput = input.getConnectedOutput();
            if (connectedOutput == null) {
                log.error("connectedOutput is null");
                continue;
            }
            if (!connectedOutput.isMine(wallet)) {
                log.info("ConnectedOutput is not mine. This can be the case for BSQ transactions where the " +
                        "input gets signed by the other wallet. connectedOutput={}", connectedOutput);
                continue;
            }

            signTransactionInput(wallet, aesKey, tx, input, i);
            checkScriptSig(tx, input, i);
        }

        checkWalletConsistency(wallet);
        verifyTransaction(tx);
        printTx("Signed Tx", tx);
    }

    public static void signTransactionInput(Wallet wallet,
                                            @Nullable KeyParameter aesKey,
                                            Transaction tx,
                                            TransactionInput txIn,
                                            int index) {
        KeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(wallet, aesKey);
        if (txIn.getConnectedOutput() != null) {
            try {
                // We assume if it's already signed, it's hopefully got a SIGHASH type that will not invalidate when
                // we sign missing pieces (to check this would require either assuming any signatures are signing
                // standard output types or a way to get processed signatures out of script execution)
                txIn.getScriptSig().correctlySpends(tx, index, txIn.getWitness(), txIn.getValue(), txIn.getConnectedOutput().getScriptPubKey(), Script.ALL_VERIFY_FLAGS);
                log.warn("Input {} already correctly spends output, assuming SIGHASH type used will be safe and skipping signing.", index);
                return;
            } catch (ScriptException e) {
                // Expected.
            }

            Script scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();
            RedeemData redeemData = txIn.getConnectedRedeemData(maybeDecryptingKeyBag);
            checkNotNull(redeemData, "Transaction exists in wallet that we cannot redeem: %s", txIn.getOutpoint().getHash());
            txIn.setScriptSig(scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript));

            TransactionSigner.ProposedTransaction propTx = new TransactionSigner.ProposedTransaction(tx);
            Transaction partialTx = propTx.partialTx;
            txIn = partialTx.getInput(index);
            if (txIn.getConnectedOutput() != null) {
                // If we don't have a sig we don't do the check to avoid error reports of failed sig checks
                List<ScriptChunk> chunks = txIn.getConnectedOutput().getScriptPubKey().getChunks();
                byte[] pushData;
                if (!chunks.isEmpty() && (pushData = chunks.get(0).data) != null && pushData.length > 0) {
                    try {
                        // We assume if it's already signed, it's hopefully got a SIGHASH type that will not invalidate when
                        // we sign missing pieces (to check this would require either assuming any signatures are signing
                        // standard output types or a way to get processed signatures out of script execution)
                        txIn.getScriptSig().correctlySpends(tx, index, txIn.getWitness(), txIn.getValue(), txIn.getConnectedOutput().getScriptPubKey(), Script.ALL_VERIFY_FLAGS);
                        log.warn("Input {} already correctly spends output, assuming SIGHASH type used will be safe and skipping signing.", index);
                        return;
                    } catch (ScriptException e) {
                        // Expected.
                    }
                }

                redeemData = txIn.getConnectedRedeemData(maybeDecryptingKeyBag);
                scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();

                checkNotNull(redeemData, "redeemData must not be null");
                ECKey pubKey = redeemData.keys.get(0);
                if (pubKey instanceof DeterministicKey)
                    propTx.keyPaths.put(scriptPubKey, (((DeterministicKey) pubKey).getPath()));

                ECKey key = LowRSigningKey.from(redeemData.getFullKey());
                if (key == null) {
                    log.warn("No local key found for input {}", index);
                    return;
                }

                Script inputScript = txIn.getScriptSig();
                byte[] script = redeemData.redeemScript.getProgram();

                if (ScriptPattern.isP2PK(scriptPubKey) || ScriptPattern.isP2PKH(scriptPubKey)) {
                    try {
                        TransactionSignature signature = partialTx.calculateSignature(index, key, script, Transaction.SigHash.ALL, false);
                        inputScript = scriptPubKey.getScriptSigWithSignature(inputScript, signature.encodeToBitcoin(), 0);
                        txIn.setScriptSig(inputScript);
                    } catch (ECKey.KeyIsEncryptedException e1) {
                        throw e1;
                    } catch (ECKey.MissingPrivateKeyException e1) {
                        log.warn("No private key in keypair for input {}", index);
                    }
                } else if (ScriptPattern.isP2WPKH(scriptPubKey)) {
                    try {
                        // scriptCode is expected to have the format of a legacy P2PKH output script
                        Script scriptCode = ScriptBuilder.createP2PKHOutputScript(key);
                        Coin value = txIn.getValue();
                        TransactionSignature txSig = tx.calculateWitnessSignature(index, key, aesKey, scriptCode, value,
                                Transaction.SigHash.ALL, false);
                        txIn.setScriptSig(ScriptBuilder.createEmpty());
                        txIn.setWitness(TransactionWitness.redeemP2WPKH(txSig, key));
                    } catch (ECKey.KeyIsEncryptedException e1) {
                        log.error(e1.toString());
                        throw e1;
                    } catch (ECKey.MissingPrivateKeyException e1) {
                        log.warn("No private key in keypair for input {}", index);
                    }
                } else {
                    log.error("Unexpected script type.");
                    throw new RuntimeException("Unexpected script type.");
                }
            } else {
                log.warn("Missing connected output, assuming input {} is already signed.", index);
            }
        } else {
            log.error("Missing connected output, assuming already signed.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dust
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void verifyNonDustTxo(Transaction tx) {
        for (TransactionOutput txo : tx.getOutputs()) {
            Coin value = txo.getValue();
            // OpReturn outputs have value 0
            if (value.isPositive()) {
                checkArgument(Restrictions.isAboveDust(txo.getValue()),
                        "An output value is below dust limit. Transaction=" + tx);
            }
        }

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Broadcast tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void broadcastTx(Transaction tx, TxBroadcaster.Callback callback) {
        TxBroadcaster.broadcastTx(wallet, walletsSetup.getPeerGroup(), tx, callback);
    }

    public void broadcastTx(Transaction tx, TxBroadcaster.Callback callback, int timeOut) {
        TxBroadcaster.broadcastTx(wallet, walletsSetup.getPeerGroup(), tx, callback, timeOut);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TransactionConfidence
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public TransactionConfidence getConfidenceForAddress(Address address) {
        if (wallet != null) {
            Set<Transaction> transactions = getAddressToMatchingTxSetMultimap().get(address);
            return getMostRecentConfidence(transactions.stream()
                    .map(tx -> getTransactionConfidence(tx, address))
                    .collect(Collectors.toList()));
        }
        return null;
    }

    @Nullable
    public TransactionConfidence getConfidenceForAddressFromBlockHeight(Address address, long targetHeight) {
        if (wallet != null) {
            Set<Transaction> transactions = getAddressToMatchingTxSetMultimap().get(address);
            // "acceptable confidence" is either a new (pending) Tx, or a Tx confirmed after target block height
            return getMostRecentConfidence(transactions.stream()
                    .map(tx -> getTransactionConfidence(tx, address))
                    .filter(Objects::nonNull)
                    .filter(con -> con.getConfidenceType() == PENDING ||
                            (con.getConfidenceType() == BUILDING && con.getAppearedAtChainHeight() > targetHeight))
                    .collect(Collectors.toList()));
        }
        return null;
    }

    private SetMultimap<Address, Transaction> getAddressToMatchingTxSetMultimap() {
        return addressToMatchingTxSetCache.updateAndGet(map -> map != null ? map : computeAddressToMatchingTxSetMultimap());
    }

    private SetMultimap<Address, Transaction> computeAddressToMatchingTxSetMultimap() {
        return wallet.getTransactions(false).stream()
                .collect(ImmutableSetMultimap.flatteningToImmutableSetMultimap(
                        Function.identity(),
                        (Function<Transaction, Stream<Address>>) (
                                t -> getOutputsWithConnectedOutputs(t).stream()
                                        .map(WalletService::getAddressFromOutput)
                                        .filter(Objects::nonNull))))
                .inverse();
    }

    @Nullable
    public TransactionConfidence getConfidenceForTxId(@Nullable String txId) {
        if (wallet != null && txId != null && !txId.isEmpty()) {
            Sha256Hash hash = Sha256Hash.wrap(txId);
            Transaction tx = getTransaction(hash);
            TransactionConfidence confidence;
            if (tx != null && (confidence = tx.getConfidence()).getConfidenceType() != DEAD) {
                return confidence;
            }
        }
        return null;
    }

    @Nullable
    private static TransactionConfidence getTransactionConfidence(Transaction tx, Address address) {
        List<TransactionConfidence> transactionConfidenceList = getOutputsWithConnectedOutputs(tx).stream()
                .filter(output -> address != null && address.equals(getAddressFromOutput(output)))
                .flatMap(o -> Stream.ofNullable(o.getParentTransaction()))
                .map(Transaction::getConfidence)
                .collect(Collectors.toList());
        return getMostRecentConfidence(transactionConfidenceList);
    }


    private static List<TransactionOutput> getOutputsWithConnectedOutputs(Transaction tx) {
        List<TransactionOutput> transactionOutputs = tx.getOutputs();
        List<TransactionOutput> connectedOutputs = new ArrayList<>();

        // add all connected outputs from any inputs as well
        List<TransactionInput> transactionInputs = tx.getInputs();
        for (TransactionInput transactionInput : transactionInputs) {
            TransactionOutput transactionOutput = transactionInput.getConnectedOutput();
            if (transactionOutput != null) {
                connectedOutputs.add(transactionOutput);
            }
        }

        List<TransactionOutput> mergedOutputs = new ArrayList<>();
        mergedOutputs.addAll(transactionOutputs);
        mergedOutputs.addAll(connectedOutputs);
        return mergedOutputs;
    }

    @Nullable
    private static TransactionConfidence getMostRecentConfidence(List<TransactionConfidence> transactionConfidenceList) {
        TransactionConfidence transactionConfidence = null;
        for (TransactionConfidence confidence : transactionConfidenceList) {
            if (confidence != null) {
                if (transactionConfidence == null || confidence.getConfidenceType() == PENDING ||
                        (confidence.getConfidenceType() == BUILDING &&
                                transactionConfidence.getConfidenceType() == BUILDING &&
                                confidence.getDepthInBlocks() < transactionConfidence.getDepthInBlocks())) {
                    transactionConfidence = confidence;
                }
            }
        }
        return transactionConfidence;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Balance
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getAvailableBalance() {
        return wallet != null ? wallet.getBalance(Wallet.BalanceType.AVAILABLE) : Coin.ZERO;
    }

    public Coin getEstimatedBalance() {
        return wallet != null ? wallet.getBalance(Wallet.BalanceType.ESTIMATED) : Coin.ZERO;
    }

    public Coin getBalanceForAddress(Address address) {
        return wallet != null ? getBalance(wallet.calculateAllSpendCandidates(), address) : Coin.ZERO;
    }

    protected Coin getBalance(List<TransactionOutput> transactionOutputs, Address address) {
        Coin balance = Coin.ZERO;
        for (TransactionOutput output : transactionOutputs) {
            if (!isDustAttackUtxo(output)) {
                if (isOutputScriptConvertibleToAddress(output) &&
                        address != null &&
                        address.equals(getAddressFromOutput(output)))
                    balance = balance.add(output.getValue());
            }
        }
        return balance;
    }

    protected abstract boolean isDustAttackUtxo(TransactionOutput output);

    public Coin getBalance(TransactionOutput output) {
        return getBalanceForAddress(getAddressFromOutput(output));
    }

    public Coin getTxFeeForWithdrawalPerVbyte() {
        Coin fee = (preferences.isUseCustomWithdrawalTxFee()) ?
                Coin.valueOf(preferences.getWithdrawalTxFeeInVbytes()) :
                feeService.getTxFeePerVbyte();
        log.info("tx fee = " + fee.toFriendlyString());
        return fee;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tx outputs
    ///////////////////////////////////////////////////////////////////////////////////////////

    public int getNumTxOutputsForAddress(Address address) {
        return getTxOutputAddressMultiset().count(address);
    }

    private Multiset<Address> getTxOutputAddressMultiset() {
        return txOutputAddressCache.updateAndGet(set -> set != null ? set : computeTxOutputAddressMultiset());
    }

    private Multiset<Address> computeTxOutputAddressMultiset() {
        return wallet.getTransactions(false).stream()
                .flatMap(t -> t.getOutputs().stream())
                .map(WalletService::getAddressFromOutput)
                .filter(Objects::nonNull)
                .collect(ImmutableMultiset.toImmutableMultiset());
    }

    public boolean isAddressUnused(Address address) {
        return getNumTxOutputsForAddress(address) == 0;
    }

    public boolean isMine(TransactionOutput transactionOutput) {
        return transactionOutput.isMine(wallet);
    }

    // BISQ issue #4039: Prevent dust outputs from being created.
    // Check the outputs of a proposed transaction.  If any are below the dust threshold,
    // add up the dust, log the details, and return the cumulative dust amount.
    public Coin getDust(Transaction proposedTransaction) {
        Coin dust = Coin.ZERO;
        for (TransactionOutput transactionOutput : proposedTransaction.getOutputs()) {
            if (transactionOutput.getValue().isLessThan(Restrictions.getMinNonDustOutput())) {
                dust = dust.add(transactionOutput.getValue());
                log.info("Dust TXO = {}", transactionOutput);
            }
        }
        return dust;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Empty complete Wallet
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void emptyBtcWallet(String toAddress,
                               @Nullable KeyParameter aesKey,
                               ResultHandler resultHandler,
                               ErrorMessageHandler errorMessageHandler)
            throws InsufficientMoneyException, AddressFormatException {
        SendRequest sendRequest = SendRequest.emptyWallet(Address.fromString(params, toAddress));
        sendRequest.fee = Coin.ZERO;
        sendRequest.feePerKb = getTxFeeForWithdrawalPerVbyte().multiply(1000);
        sendRequest.aesKey = aesKey;
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        printTx("empty btc wallet", sendResult.tx);

        // For better redundancy in case the broadcast via BitcoinJ fails we also
        // publish the tx via mempool nodes.
        MemPoolSpaceTxBroadcaster.broadcastTx(sendResult.tx);

        Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<>() {
            @Override
            public void onSuccess(Transaction result) {
                log.info("emptyBtcWallet onSuccess Transaction={}", result);
                resultHandler.handleResult();
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                log.error("emptyBtcWallet onFailure " + t);
                errorMessageHandler.handleErrorMessage(t.getMessage());
            }
        }, MoreExecutors.directExecutor());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getTxFromSerializedTx(byte[] tx) {
        return new Transaction(params, tx);
    }

    public NetworkParameters getParams() {
        return params;
    }

    public int getBestChainHeight() {
        BlockChain chain = walletsSetup.getChain();
        return isWalletReady() && chain != null ? chain.getBestChainHeight() : 0;
    }

    public boolean isChainHeightSyncedWithinTolerance() {
        return walletsSetup.isChainHeightSyncedWithinTolerance();
    }

    public Transaction getClonedTransaction(Transaction tx) {
        return new Transaction(params, tx.bitcoinSerialize());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Wallet delegates to avoid direct access to wallet outside the service class
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addChangeEventListener(WalletChangeEventListener listener) {
        wallet.addChangeEventListener(Threading.USER_THREAD, listener);
    }

    public void removeChangeEventListener(WalletChangeEventListener listener) {
        wallet.removeChangeEventListener(listener);
    }

    public void addNewBestBlockListener(NewBestBlockListener listener) {
        BlockChain chain = walletsSetup.getChain();
        if (isWalletReady() && chain != null)
            chain.addNewBestBlockListener(listener);
    }

    public void removeNewBestBlockListener(NewBestBlockListener listener) {
        BlockChain chain = walletsSetup.getChain();
        if (isWalletReady() && chain != null)
            chain.removeNewBestBlockListener(listener);
    }

    public boolean isWalletReady() {
        return wallet != null;
    }

    public DeterministicSeed getKeyChainSeed() {
        return wallet.getKeyChainSeed();
    }

    @Nullable
    public KeyCrypter getKeyCrypter() {
        return wallet.getKeyCrypter();
    }

    public boolean checkAESKey(KeyParameter aesKey) {
        return wallet.checkAESKey(aesKey);
    }

    @Nullable
    public DeterministicKey findKeyFromPubKey(byte[] pubKey) {
        return (DeterministicKey) wallet.findKeyFromPubKey(pubKey);
    }

    public boolean isEncrypted() {
        return wallet.isEncrypted();
    }

    public List<Transaction> getAllRecentTransactions(boolean includeDead) {
        return getRecentTransactions(Integer.MAX_VALUE, includeDead);
    }

    public List<Transaction> getRecentTransactions(int numTransactions, boolean includeDead) {
        // Returns a list ordered by tx.getUpdateTime() desc
        return wallet.getRecentTransactions(numTransactions, includeDead);
    }

    public int getLastBlockSeenHeight() {
        return wallet.getLastBlockSeenHeight();
    }

    /**
     * Check if there are more than 20 unconfirmed transactions in the chain right now.
     *
     * @return true when queue is full
     */
    public boolean isUnconfirmedTransactionsLimitHit() {
        // For published delayed payout transactions we do not receive the tx confidence
        // so we cannot check if it is confirmed so we ignore it for that check. The check is any arbitrarily
        // using a limit of 20, so we don't need to be exact here. Should just reduce the likelihood of issues with
        // the too long chains of unconfirmed transactions.
        return getTransactions(false).stream()
                .filter(tx -> tx.getLockTime() == 0)
                .filter(Transaction::isPending)
                .count() > 20;
    }

    public Set<Transaction> getTransactions(boolean includeDead) {
        return wallet.getTransactions(includeDead);
    }

    public Coin getBalance(@SuppressWarnings("SameParameterValue") Wallet.BalanceType balanceType) {
        return wallet.getBalance(balanceType);
    }

    @Nullable
    public Transaction getTransaction(Sha256Hash hash) {
        return wallet.getTransaction(hash);
    }

    @Nullable
    public Transaction getTransaction(@Nullable String txId) {
        if (txId == null) {
            return null;
        }
        return getTransaction(Sha256Hash.wrap(txId));
    }


    public boolean isTransactionOutputMine(TransactionOutput transactionOutput) {
        return transactionOutput.isMine(wallet);
    }

   /* public boolean isTxOutputMine(TxOutput txOutput) {
        try {
            Script script = txOutput.getScript();
            if (script.isSentToRawPubKey()) {
                byte[] pubkey = script.getPubKey();
                return wallet.isPubKeyMine(pubkey);
            }
            if (script.isPayToScriptHash()) {
                return wallet.isPayToScriptHashMine(script.getPubKeyHash());
            } else {
                byte[] pubkeyHash = script.getPubKeyHash();
                return wallet.isPubKeyHashMine(pubkeyHash);
            }
        } catch (ScriptException e) {
            // Just means we didn't understand the output of this transaction: ignore it.
            log.debug("Could not parse tx output script: {}", e.toString());
            return false;
        }
    }*/

    public Coin getValueSentFromMeForTransaction(Transaction transaction) throws ScriptException {
        // Does the same thing as transaction.getValueSentFromMe(wallet), except that watched connected
        // outputs don't count towards the total, only outputs with pubKeys belonging to the wallet.
        long satoshis = transaction.getInputs().stream()
                .flatMap(input -> getConnectedOutput(input, WalletTransaction.Pool.UNSPENT)
                        .or(() -> getConnectedOutput(input, WalletTransaction.Pool.SPENT))
                        .or(() -> getConnectedOutput(input, WalletTransaction.Pool.PENDING))
                        .filter(o -> o.isMine(wallet))
                        .stream())
                .mapToLong(o -> o.getValue().value)
                .sum();
        return Coin.valueOf(satoshis);
    }

    private Optional<TransactionOutput> getConnectedOutput(TransactionInput input, WalletTransaction.Pool pool) {
        TransactionOutPoint outpoint = input.getOutpoint();
        return Optional.ofNullable(wallet.getTransactionPool(pool).get(outpoint.getHash()))
                .map(tx -> tx.getOutput(outpoint.getIndex()));
    }

    public Coin getValueSentToMeForTransaction(Transaction transaction) throws ScriptException {
        // Does the same thing as transaction.getValueSentToMe(wallet), except that watched outputs
        // don't count towards the total, only outputs with pubKeys belonging to the wallet.
        long satoshis = transaction.getOutputs().stream()
                .filter(o -> o.isMine(wallet))
                .mapToLong(o -> o.getValue().value)
                .sum();
        return Coin.valueOf(satoshis);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Util
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void printTx(String tracePrefix, Transaction tx) {
        log.info("\n" + tracePrefix + ":\n" + tx.toString());
    }

    public static boolean isOutputScriptConvertibleToAddress(TransactionOutput output) {
        return ScriptPattern.isP2PKH(output.getScriptPubKey()) ||
                ScriptPattern.isP2SH(output.getScriptPubKey()) ||
                ScriptPattern.isP2WH(output.getScriptPubKey());
    }

    @Nullable
    public static Address getAddressFromOutput(TransactionOutput output) {
        return isOutputScriptConvertibleToAddress(output) ?
                output.getScriptPubKey().getToAddress(Config.baseCurrencyNetworkParameters()) : null;
    }

    @Nullable
    public static String getAddressStringFromOutput(TransactionOutput output) {
        return isOutputScriptConvertibleToAddress(output) ?
                output.getScriptPubKey().getToAddress(Config.baseCurrencyNetworkParameters()).toString() : null;
    }


    /**
     * @param serializedTransaction The serialized transaction to be added to the wallet
     * @return The transaction we added to the wallet, which is different as the one we passed as argument!
     * @throws VerificationException if the transaction could not be parsed or fails sanity checks
     */
    public static Transaction maybeAddTxToWallet(byte[] serializedTransaction,
                                                 Wallet wallet,
                                                 TransactionConfidence.Source source) throws VerificationException {
        Transaction tx = new Transaction(wallet.getParams(), serializedTransaction);
        if (wallet.getTransaction(tx.getTxId()) == null) {
            // We need to recreate the transaction otherwise we get a null pointer...
            tx.getConfidence(Context.get()).setSource(source);
            wallet.receivePending(tx, null, true);
        }
        return wallet.getTransaction(tx.getTxId());
    }

    public static Transaction maybeAddNetworkTxToWallet(byte[] serializedTransaction,
                                                        Wallet wallet) throws VerificationException {
        return maybeAddTxToWallet(serializedTransaction, wallet, TransactionConfidence.Source.NETWORK);
    }

    public static Transaction maybeAddSelfTxToWallet(Transaction transaction,
                                                     Wallet wallet) throws VerificationException {
        return maybeAddTxToWallet(transaction, wallet, TransactionConfidence.Source.SELF);
    }

    public static Transaction maybeAddTxToWallet(Transaction transaction,
                                                 Wallet wallet,
                                                 TransactionConfidence.Source source) throws VerificationException {
        return maybeAddTxToWallet(transaction.bitcoinSerialize(), wallet, source);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // bisqWalletEventListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public class BisqWalletListener implements WalletCoinsReceivedEventListener,
            WalletCoinsSentEventListener,
            WalletReorganizeEventListener,
            TransactionConfidenceEventListener {
        @Override
        public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            notifyBalanceListeners(tx);
        }

        @Override
        public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            notifyBalanceListeners(tx);
        }

        @Override
        public void onReorganize(Wallet wallet) {
            log.warn("onReorganize");
        }

        @Override
        public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
            for (AddressConfidenceListener addressConfidenceListener : addressConfidenceListeners) {
                TransactionConfidence confidence = getTransactionConfidence(tx, addressConfidenceListener.getAddress());
                addressConfidenceListener.onTransactionConfidenceChanged(confidence);
            }
            for (OutputSpendConfidenceListener listener : spendConfidenceListeners) {
                TransactionInput spentBy = listener.getOutput().getSpentBy();
                if (spentBy != null && tx.equals(spentBy.getParentTransaction())) {
                    listener.onOutputSpendConfidenceChanged(tx.getConfidence());
                }
            }
            if (!txConfidenceListeners.isEmpty()) {
                String txId = tx.getTxId().toString();
                for (TxConfidenceListener listener : txConfidenceListeners) {
                    if (txId.equals(listener.getTxId())) {
                        listener.onTransactionConfidenceChanged(tx.getConfidence());
                    }
                }
            }
        }

        void notifyBalanceListeners(Transaction tx) {
            for (BalanceListener balanceListener : balanceListeners) {
                Coin balance;
                if (balanceListener.getAddress() != null)
                    balance = getBalanceForAddress(balanceListener.getAddress());
                else
                    balance = getAvailableBalance();

                balanceListener.onBalanceChanged(balance, tx);
            }
        }
    }
}
