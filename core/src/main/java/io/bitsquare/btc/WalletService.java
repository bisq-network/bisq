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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import io.bitsquare.btc.listeners.AddressConfidenceListener;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.listeners.TxConfidenceListener;
import io.bitsquare.btc.provider.fee.FeeService;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.user.Preferences;
import org.bitcoinj.core.*;
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

/**
 * WalletService handles all non trade specific wallet and bitcoin related services.
 * It startup the wallet app kit and initialized the wallet.
 */
public abstract class WalletService {
    protected static final Logger log = LoggerFactory.getLogger(WalletService.class);

    protected final CopyOnWriteArraySet<AddressConfidenceListener> addressConfidenceListeners = new CopyOnWriteArraySet<>();
    protected final CopyOnWriteArraySet<TxConfidenceListener> txConfidenceListeners = new CopyOnWriteArraySet<>();
    protected final CopyOnWriteArraySet<BalanceListener> balanceListeners = new CopyOnWriteArraySet<>();

    protected final WalletEventListener walletEventListener = new BitsquareWalletEventListener();
    protected final NetworkParameters params;

    protected WalletSetup walletSetup;
    protected final Preferences preferences;
    protected final FeeService feeService;

    protected Wallet wallet;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public WalletService(WalletSetup walletSetup,
                         Preferences preferences,
                         FeeService feeService) {
        this.walletSetup = walletSetup;
        this.preferences = preferences;
        this.feeService = feeService;

        params = walletSetup.getParams();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    public void shutDown() {
        if (wallet != null)
            wallet.removeEventListener(walletEventListener);
    }


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
    // TransactionConfidence
    ///////////////////////////////////////////////////////////////////////////////////////////

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

    public TransactionConfidence getConfidenceForTxId(String txId) {
        if (wallet != null) {
            Set<Transaction> transactions = wallet.getTransactions(true);
            for (Transaction tx : transactions) {
                if (tx.getHashAsString().equals(txId))
                    return tx.getConfidence();
            }
        }
        return null;
    }

    protected TransactionConfidence getTransactionConfidence(Transaction tx, Address address) {
        List<TransactionOutput> mergedOutputs = getOutputsWithConnectedOutputs(tx);
        List<TransactionConfidence> transactionConfidenceList = new ArrayList<>();

        mergedOutputs.stream().filter(e -> e.getScriptPubKey().isSentToAddress() ||
                e.getScriptPubKey().isPayToScriptHash()).forEach(transactionOutput -> {
            Address outputAddress = transactionOutput.getScriptPubKey().getToAddress(params);
            if (address.equals(outputAddress)) {
                transactionConfidenceList.add(tx.getConfidence());
            }
        });
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
        for (TransactionOutput transactionOutput : transactionOutputs) {
            if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isPayToScriptHash()) {
                Address addressOutput = transactionOutput.getScriptPubKey().getToAddress(params);
                if (addressOutput.equals(address))
                    balance = balance.add(transactionOutput.getValue());
            }
        }
        return balance;
    }

    public int getNumTxOutputsForAddress(Address address) {
        List<TransactionOutput> transactionOutputs = new ArrayList<>();
        wallet.getTransactions(true).stream().forEach(t -> transactionOutputs.addAll(t.getOutputs()));
        int outputs = 0;
        for (TransactionOutput transactionOutput : transactionOutputs) {
            if (transactionOutput.getScriptPubKey().isSentToAddress() || transactionOutput.getScriptPubKey().isPayToScriptHash()) {
                Address addressOutput = transactionOutput.getScriptPubKey().getToAddress(params);
                if (addressOutput.equals(address))
                    outputs++;
            }
        }
        return outputs;
    }

    protected Coin getTxFeeForWithdrawalPerByte() {
        Coin fee = (preferences.getUseCustomWithdrawalTxFee()) ?
                Coin.valueOf(preferences.getWithdrawalTxFeeInBytes()) :
                feeService.getTxFeePerByte();
        log.info("tx fee = " + fee.toFriendlyString());
        return fee;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Withdrawal Fee calculation
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Withdrawal Send
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String sendFunds(String fromAddress,
                            String toAddress,
                            Coin receiverAmount,
                            Coin fee,
                            @Nullable KeyParameter aesKey,
                            AddressEntry.Context context,
                            FutureCallback<Transaction> callback) throws AddressFormatException,
            AddressEntryException, InsufficientMoneyException {
        Wallet.SendRequest sendRequest = getSendRequest(fromAddress, toAddress, receiverAmount, fee, aesKey, context);
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        Futures.addCallback(sendResult.broadcastComplete, callback);

        printTxWithInputs("sendFunds", sendResult.tx);
        return sendResult.tx.getHashAsString();
    }

    public void emptyWallet(String toAddress, KeyParameter aesKey, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler)
            throws InsufficientMoneyException, AddressFormatException {
        Wallet.SendRequest sendRequest = Wallet.SendRequest.emptyWallet(new Address(params, toAddress));
        sendRequest.feePerKb = getTxFeeForWithdrawalPerByte().multiply(1000);
        sendRequest.aesKey = aesKey;
        Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
        printTxWithInputs("emptyWallet", sendResult.tx);
        Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(Transaction result) {
                resultHandler.handleResult();
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                errorMessageHandler.handleErrorMessage(t.getMessage());
            }
        });
    }

    abstract protected Wallet.SendRequest getSendRequest(String fromAddress,
                                                         String toAddress,
                                                         Coin amount,
                                                         Coin fee,
                                                         @Nullable KeyParameter aesKey,
                                                         AddressEntry.Context context) throws AddressFormatException,
            AddressEntryException, InsufficientMoneyException;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Wallet getWallet() {
        return wallet;
    }

    public Transaction getTransactionFromSerializedTx(byte[] tx) {
        return new Transaction(params, tx);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Util
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void printTxWithInputs(String tracePrefix, Transaction tx) {
        StringBuilder sb = new StringBuilder();
        sb.append(tracePrefix).append(": ").append(tx.toString()).append("\n").append(tracePrefix);
        for (TransactionInput input : tx.getInputs()) {
            if (input.getConnectedOutput() != null)
                sb.append(" input value: ").append(input.getConnectedOutput().getValue().toFriendlyString());
            else
                sb.append(": Transaction already has inputs but we don't have the connected outputs, so we don't know the value.");
        }
        sb.append("\n").append("Size: " + tx.bitcoinSerialize().length);
        log.info(sb.toString());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////////////////////


    protected class BitsquareWalletEventListener extends AbstractWalletEventListener {
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

        protected void notifyBalanceListeners(Transaction tx) {
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
