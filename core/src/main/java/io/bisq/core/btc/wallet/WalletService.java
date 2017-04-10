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
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.util.Utilities;
import io.bisq.core.btc.exceptions.TransactionVerificationException;
import io.bisq.core.btc.exceptions.WalletException;
import io.bisq.core.btc.listeners.AddressConfidenceListener;
import io.bisq.core.btc.listeners.BalanceListener;
import io.bisq.core.btc.listeners.TxConfidenceListener;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.user.Preferences;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.signers.TransactionSigner;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Abstract base class for BTC and BSQ wallet. Provides all non-trade specific functionality.
 */
public abstract class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    protected final WalletsSetup walletsSetup;
    protected final Preferences preferences;
    protected final FeeService feeService;
    protected final NetworkParameters params;
    protected final WalletEventListener walletEventListener = new BisqWalletEventListener();
    protected final CopyOnWriteArraySet<AddressConfidenceListener> addressConfidenceListeners = new CopyOnWriteArraySet<>();
    protected final CopyOnWriteArraySet<TxConfidenceListener> txConfidenceListeners = new CopyOnWriteArraySet<>();
    protected final CopyOnWriteArraySet<BalanceListener> balanceListeners = new CopyOnWriteArraySet<>();
    protected Wallet wallet;
    protected KeyParameter aesKey;


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
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown() {
        if (wallet != null)
            wallet.removeEventListener(walletEventListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    void decryptWallet(@NotNull KeyParameter key) {
        wallet.decrypt(key);
        // Overwrite first with random bytes before setting to null
        Utilities.overwriteWithRandomBytes(key.getKey());
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
            log.trace("Check if wallet is consistent before commit.");
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
            log.trace("Verify transaction " + transaction);
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

    public static void checkScriptSig(Transaction transaction, TransactionInput input, int inputIndex) throws TransactionVerificationException {
        try {
            log.trace("Verifies that this script (interpreted as a scriptSig) correctly spends the given scriptPubKey. Check input at index: " + inputIndex);
            checkNotNull(input.getConnectedOutput(), "input.getConnectedOutput() must not be null");
            input.getScriptSig().correctlySpends(transaction, inputIndex, input.getConnectedOutput().getScriptPubKey());
        } catch (Throwable t) {
            t.printStackTrace();
            log.error(t.getMessage());
            throw new TransactionVerificationException(t);
        }
    }

    public static void removeSignatures(Transaction transaction) {
        for (TransactionInput input : transaction.getInputs()) {
            input.setScriptSig(new Script(new byte[]{}));
        }
    }
    

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Sign tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void signTransactionInput(Wallet wallet, KeyParameter aesKey, Transaction tx, TransactionInput txIn, int index) {
        KeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(wallet, aesKey);
        if (txIn.getConnectedOutput() != null) {
            try {
                // We assume if its already signed, its hopefully got a SIGHASH type that will not invalidate when
                // we sign missing pieces (to check this would require either assuming any signatures are signing
                // standard output types or a way to get processed signatures out of script execution)
                txIn.getScriptSig().correctlySpends(tx, index, txIn.getConnectedOutput().getScriptPubKey());
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
                try {
                    // We assume if its already signed, its hopefully got a SIGHASH type that will not invalidate when
                    // we sign missing pieces (to check this would require either assuming any signatures are signing
                    // standard output types or a way to get processed signatures out of script execution)
                    txIn.getScriptSig().correctlySpends(tx, index, txIn.getConnectedOutput().getScriptPubKey());
                    log.warn("Input {} already correctly spends output, assuming SIGHASH type used will be safe and skipping signing.", index);
                    return;
                } catch (ScriptException e) {
                    // Expected.
                }

                redeemData = txIn.getConnectedRedeemData(maybeDecryptingKeyBag);
                scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();

                checkNotNull(redeemData, "redeemData must not be null");
                ECKey pubKey = redeemData.keys.get(0);
                if (pubKey instanceof DeterministicKey)
                    propTx.keyPaths.put(scriptPubKey, (((DeterministicKey) pubKey).getPath()));

                ECKey key;
                if ((key = redeemData.getFullKey()) == null) {
                    log.warn("No local key found for input {}", index);
                    return;
                }

                Script inputScript = txIn.getScriptSig();
                byte[] script = redeemData.redeemScript.getProgram();
                try {
                    TransactionSignature signature = partialTx.calculateSignature(index, key, script, Transaction.SigHash.ALL, false);
                    inputScript = scriptPubKey.getScriptSigWithSignature(inputScript, signature.encodeToBitcoin(), 0);
                    txIn.setScriptSig(inputScript);
                } catch (ECKey.KeyIsEncryptedException e1) {
                    throw e1;
                } catch (ECKey.MissingPrivateKeyException e1) {
                    log.warn("No private key in keypair for input {}", index);
                }
            } else {
                log.warn("Missing connected output, assuming input {} is already signed.", index);
            }
        } else {
            log.error("Missing connected output, assuming already signed.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TransactionConfidence
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Nullable
    public TransactionConfidence getConfidenceForAddress(Address address) {
        List<TransactionConfidence> transactionConfidenceList = new ArrayList<>();
        if (wallet != null) {
            Set<Transaction> transactions = wallet.getTransactions(true);
            if (transactions != null) {
                transactionConfidenceList.addAll(transactions.stream().map(tx ->
                        getTransactionConfidence(tx, address)).collect(Collectors.toList()));
            }
        }
        return getMostRecentConfidence(transactionConfidenceList);
    }

    @Nullable
    public TransactionConfidence getConfidenceForTxId(String txId) {
        if (wallet != null) {
            // TODO includeDead txs?
            Set<Transaction> transactions = wallet.getTransactions(true);
            for (Transaction tx : transactions) {
                if (tx.getHashAsString().equals(txId))
                    return tx.getConfidence();
            }
        }
        return null;
    }

    protected TransactionConfidence getTransactionConfidence(Transaction tx, Address address) {
        List<TransactionConfidence> transactionConfidenceList = getOutputsWithConnectedOutputs(tx)
                .stream()
                .filter(WalletUtils::isOutputScriptConvertableToAddress)
                .filter(output -> address.equals(WalletUtils.getAddressFromOutput(output)))
                .map(o -> tx.getConfidence())
                .collect(Collectors.toList());
        return getMostRecentConfidence(transactionConfidenceList);
    }


    protected List<TransactionOutput> getOutputsWithConnectedOutputs(Transaction tx) {
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
    protected TransactionConfidence getMostRecentConfidence(List<TransactionConfidence> transactionConfidenceList) {
        TransactionConfidence transactionConfidence = null;
        for (TransactionConfidence confidence : transactionConfidenceList) {
            if (confidence != null) {
                if (transactionConfidence == null ||
                        confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.PENDING) ||
                        (confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING) &&
                                transactionConfidence.getConfidenceType().equals(
                                        TransactionConfidence.ConfidenceType.BUILDING) &&
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

    // BalanceType.AVAILABLE
    public Coin getAvailableBalance() {
        return wallet != null ? wallet.getBalance(Wallet.BalanceType.AVAILABLE) : Coin.ZERO;
    }

    public Coin getBalanceForAddress(Address address) {
        return wallet != null ? getBalance(wallet.calculateAllSpendCandidates(), address) : Coin.ZERO;
    }

    protected Coin getBalance(List<TransactionOutput> transactionOutputs, Address address) {
        Coin balance = Coin.ZERO;
        for (TransactionOutput output : transactionOutputs) {
            if (WalletUtils.isOutputScriptConvertableToAddress(output) &&
                    address.equals(WalletUtils.getAddressFromOutput(output)))
                balance = balance.add(output.getValue());
        }
        return balance;
    }

    public int getNumTxOutputsForAddress(Address address) {
        List<TransactionOutput> transactionOutputs = new ArrayList<>();
        wallet.getTransactions(true).stream().forEach(t -> transactionOutputs.addAll(t.getOutputs()));
        int outputs = 0;
        for (TransactionOutput output : transactionOutputs) {
            if (WalletUtils.isOutputScriptConvertableToAddress(output) &&
                    address.equals(WalletUtils.getAddressFromOutput(output)))
                outputs++;
        }
        return outputs;
    }

    Coin getTxFeeForWithdrawalPerByte() {
        Coin fee = (preferences.getUseCustomWithdrawalTxFee()) ?
                Coin.valueOf(preferences.getWithdrawalTxFeeInBytes()) :
                feeService.getTxFeePerByte();
        log.info("tx fee = " + fee.toFriendlyString());
        return fee;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Empty complete Wallet
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void emptyWallet(String toAddress, KeyParameter aesKey, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler)
            throws InsufficientMoneyException, AddressFormatException {
        Wallet.SendRequest sendRequest = Wallet.SendRequest.emptyWallet(new Address(params, toAddress));
        sendRequest.feePerKb = getTxFeeForWithdrawalPerByte().multiply(1000);
        sendRequest.aesKey = aesKey;
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        printTx("empty wallet", sendResult.tx);
        Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(Transaction result) {
                log.info("emptyWallet onSuccess Transaction=" + result);
                resultHandler.handleResult();
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                log.error("emptyWallet onFailure " + t.toString());
                errorMessageHandler.handleErrorMessage(t.getMessage());
            }
        });
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
        return walletsSetup.getChain().getBestChainHeight();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Wallet delegates to avoid direct access to wallet outside the service class
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addEventListener(WalletEventListener listener) {
        wallet.addEventListener(listener, Threading.USER_THREAD);
    }

    public boolean removeEventListener(WalletEventListener listener) {
        return wallet.removeEventListener(listener);
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

    public DeterministicKey freshKey(KeyChain.KeyPurpose purpose) {
        return wallet.freshKey(purpose);
    }

    public DeterministicKey findKeyFromPubKeyHash(byte[] pubKeyHash) {
        return wallet.getActiveKeychain().findKeyFromPubHash(pubKeyHash);
    }

    public Address freshReceiveAddress() {
        return wallet.freshReceiveAddress();
    }

    public boolean isEncrypted() {
        return wallet.isEncrypted();
    }

    public List<Transaction> getRecentTransactions(int numTransactions, boolean includeDead) {
        return wallet.getRecentTransactions(numTransactions, includeDead);
    }

    public int getLastBlockSeenHeight() {
        return wallet.getLastBlockSeenHeight();
    }

    public Set<Transaction> getTransactions(boolean includeDead) {
        return wallet.getTransactions(includeDead);
    }

    public Coin getBalance(Wallet.BalanceType balanceType) {
        return wallet.getBalance(balanceType);
    }

    @Nullable
    public Transaction getTransaction(Sha256Hash hash) {
        return wallet.getTransaction(hash);
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
        return transaction.getValueSentFromMe(wallet);
    }

    public Coin getValueSentToMeForTransaction(Transaction transaction) throws ScriptException {
        return transaction.getValueSentToMe(wallet);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Util
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void printTx(String tracePrefix, Transaction tx) {
        int size = tx.bitcoinSerialize().length;
        log.info("\n" + tracePrefix + ":\n" +
                tx.toString() +
                "Satoshi/byte: " + (tx.getFee() != null ? tx.getFee().value / size : "No fee set yet") +
                " (size: " + size + ")");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // bisqWalletEventListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public class BisqWalletEventListener extends AbstractWalletEventListener {
        @Override
        public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            notifyBalanceListeners(tx);
        }

        @Override
        public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            notifyBalanceListeners(tx);
        }

        @Override
        public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
            for (AddressConfidenceListener addressConfidenceListener : addressConfidenceListeners) {
                List<TransactionConfidence> transactionConfidenceList = new ArrayList<>();
                transactionConfidenceList.add(getTransactionConfidence(tx, addressConfidenceListener.getAddress()));

                TransactionConfidence transactionConfidence = getMostRecentConfidence(transactionConfidenceList);
                addressConfidenceListener.onTransactionConfidenceChanged(transactionConfidence);
            }
            txConfidenceListeners.stream()
                    .filter(txConfidenceListener -> tx != null &&
                            tx.getHashAsString() != null &&
                            txConfidenceListener != null &&
                            tx.getHashAsString().equals(txConfidenceListener.getTxID()))
                    .forEach(txConfidenceListener ->
                            txConfidenceListener.onTransactionConfidenceChanged(tx.getConfidence()));
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
