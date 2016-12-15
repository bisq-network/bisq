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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import io.bitsquare.btc.*;
import io.bitsquare.btc.exceptions.TransactionVerificationException;
import io.bitsquare.btc.exceptions.WalletException;
import io.bitsquare.btc.provider.fee.FeeService;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.user.Preferences;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.script.ScriptBuilder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * WalletService handles all non trade specific wallet and bitcoin related services.
 * It startup the wallet app kit and initialized the wallet.
 */
public class BtcWalletService extends WalletService {
    private static final Logger log = LoggerFactory.getLogger(BtcWalletService.class);

    private final AddressEntryList addressEntryList;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BtcWalletService(WalletsSetup walletsSetup,
                            AddressEntryList addressEntryList,
                            Preferences preferences,
                            FeeService feeService) {
        super(walletsSetup,
                preferences,
                feeService);

        this.addressEntryList = addressEntryList;

        walletsSetup.addSetupCompletedHandler(() -> {
            wallet = walletsSetup.getBtcWallet();
            wallet.addEventListener(walletEventListener);
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Overridden Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    void decryptWallet(@NotNull KeyParameter key) {
        super.decryptWallet(key);

        addressEntryList.stream().forEach(e -> {
            final DeterministicKey keyPair = e.getKeyPair();
            if (keyPair != null && keyPair.isEncrypted())
                e.setDeterministicKey(keyPair.decrypt(key));
        });
        addressEntryList.queueUpForSave();
    }

    @Override
    void encryptWallet(KeyCrypterScrypt keyCrypterScrypt, KeyParameter key) {
        super.encryptWallet(keyCrypterScrypt, key);

        addressEntryList.stream().forEach(e -> {
            final DeterministicKey keyPair = e.getKeyPair();
            if (keyPair != null && keyPair.isEncrypted())
                e.setDeterministicKey(keyPair.encrypt(keyCrypterScrypt, key));
        });
        addressEntryList.queueUpForSave();
    }

    @Override
    String getWalletAsString(boolean includePrivKeys) {
        StringBuilder sb = new StringBuilder();
        getAddressEntryListAsImmutableList().stream().forEach(e -> sb.append(e.toString()).append("\n"));
        return "BitcoinJ wallet:\n" +
                wallet.toString(includePrivKeys, true, true, walletsSetup.getChain()) + "\n\n" +
                "Bitsquare address entry list:\n" +
                sb.toString() +
                "All pubkeys as hex:\n" +
                wallet.printAllPubKeysAsHex();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Add fee input to prepared SQU send tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction addFeeInputToPreparedSquSendTx(Transaction preparedSquTx) throws
            TransactionVerificationException, WalletException, InsufficientFundsException {
        log.trace("addFeeInputToPreparedSquSendTx called");
        try {
            int counter = 0;
            int txSize = 407; // typical size for a tx with 2 inputs and 3 outputs
            Coin txFeePerByte = getTxFeeForWithdrawalPerByte();
            Address feePaymentAddress = getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress();
            Address changeAddress = getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress();
            checkNotNull(feePaymentAddress, "feePaymentAddress must not be null");
            checkNotNull(changeAddress, "changeAddress must not be null");
            BtcCoinSelector coinSelector = new BtcCoinSelector(params, feePaymentAddress);
            final List<TransactionInput> preparedSquTxInputs = preparedSquTx.getInputs();
            final List<TransactionOutput> preparedSquTxOutputs = preparedSquTx.getOutputs();
            Transaction resultTx;
            do {
                counter++;
                final Transaction tx = new Transaction(params);
                preparedSquTxInputs.stream().forEach(tx::addInput);
                preparedSquTxOutputs.stream().forEach(tx::addOutput);
                Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
                sendRequest.shuffleOutputs = false;
                sendRequest.aesKey = aesKey;
                // Need to be false as it would try to sign all inputs (SQU inputs are not in this wallet)
                sendRequest.signInputs = false;
                sendRequest.ensureMinRequiredFee = false;
                sendRequest.feePerKb = Coin.ZERO;
                sendRequest.fee = txFeePerByte.multiply(txSize);
                sendRequest.coinSelector = coinSelector;
                sendRequest.changeAddress = changeAddress;
                wallet.completeTx(sendRequest);

                resultTx = sendRequest.tx;
                //TODO add estimated signature size
                txSize = resultTx.bitcoinSerialize().length;
            }
            while (counter < 10 && Math.abs(resultTx.getFee().value - txFeePerByte.multiply(txSize).value) > 1000);
            if (counter == 10) {
                log.error("Could not calculate the fee. Tx=" + resultTx);
            }

            // Sign all BTC inputs
            int startIndex = preparedSquTxInputs.size();
            int endIndex = resultTx.getInputs().size();
            for (int i = startIndex; i < endIndex; i++) {
                TransactionInput txIn = resultTx.getInputs().get(i);
                signTransactionInput(resultTx, txIn, i);
                checkScriptSig(resultTx, txIn, i);
            }

            checkWalletConsistency();
            verifyTransaction(resultTx);

            printTx("Signed resultTx", resultTx);
            return resultTx;
        } catch (InsufficientMoneyException e) {
            throw new InsufficientFundsException("The fees for that transaction exceed the available funds " +
                    "or the resulting output value is below the min. dust value:\n" +
                    "Missing " + (e.missing != null ? e.missing.toFriendlyString() : "null"));
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Add mining fee input and mandatory change output for prepared CompensationRequest fee tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction addInputAndOutputToPreparedSquCompensationRequestFeeTx(Transaction preparedSquTx, byte[] hash) throws
            TransactionVerificationException, WalletException, InsufficientFundsException {

        // preparedSquTx has following structure:
        // inputs [1-n] SQU inputs
        // outputs [0-1] SQU change output
        // mining fee: SQU CompensationRequest fee

        try {
            int counter = 0;
            final int sigSizePerInput = 106;
            int txSize = 203; // typical size for a tx with 2 unsigned inputs and 3 outputs
            final Coin txFeePerByte = feeService.getTxFeePerByte();
            Coin valueToForceChange = Coin.ZERO;
            Address changeAddress = getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress();
            checkNotNull(changeAddress, "changeAddress must not be null");
            final BtcCoinSelector coinSelector = new BtcCoinSelector(params, changeAddress);

            final List<TransactionInput> preparedSquTxInputs = preparedSquTx.getInputs();
            final List<TransactionOutput> preparedSquTxOutputs = preparedSquTx.getOutputs();
            Transaction resultTx = null;
            int numInputs = preparedSquTxInputs.size() + 1;
            boolean validTx;
            do {
                counter++;
                if (counter >= 10) {
                    checkNotNull(resultTx, "resultTx must not be null");
                    log.error("Could not calculate the fee. Tx=" + resultTx);
                    break;
                }

                Transaction tx = new Transaction(params);
                preparedSquTxInputs.stream().forEach(tx::addInput);

                if (valueToForceChange.isZero()) {
                    preparedSquTxOutputs.stream().forEach(tx::addOutput);
                } else {
                    //TODO test that case
                    // We have only 1 output at preparedSquTxOutputs otherwise the valueToForceChange must be 0
                    checkArgument(preparedSquTxOutputs.size() == 1);
                    tx.addOutput(valueToForceChange, changeAddress);
                    tx.addOutput(preparedSquTxOutputs.get(0));
                }

                Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
                sendRequest.shuffleOutputs = false;
                sendRequest.aesKey = aesKey;
                // Need to be false as it would try to sign all inputs (SQU inputs are not in this wallet)
                sendRequest.signInputs = false;
                sendRequest.ensureMinRequiredFee = false;
                sendRequest.feePerKb = Coin.ZERO;
                Coin fee = txFeePerByte.multiply(txSize + sigSizePerInput * numInputs);
                sendRequest.fee = fee;
                sendRequest.coinSelector = coinSelector;
                sendRequest.changeAddress = changeAddress;
                wallet.completeTx(sendRequest);

                Transaction txWithBtcInputs = sendRequest.tx;
                // add OP_RETURN output
                txWithBtcInputs.addOutput(new TransactionOutput(params, txWithBtcInputs, Coin.ZERO, ScriptBuilder.createOpReturnScript(hash).getProgram()));


                // As we cannot remove an outut we recreate the tx
                resultTx = new Transaction(params);
                txWithBtcInputs.getInputs().stream().forEach(resultTx::addInput);
                txWithBtcInputs.getOutputs().stream().forEach(resultTx::addOutput);

                numInputs = resultTx.getInputs().size();

                txSize = resultTx.bitcoinSerialize().length + sigSizePerInput * numInputs;
                // We need min. 1 output beside op_return
                // calculated fee must be inside of a tolerance range with tx fee
                final int tolerance = 1000;
                final long txFeeAsLong = resultTx.getFee().value;
                final long calculatedFeeAsLong = txFeePerByte.multiply(txSize + sigSizePerInput * numInputs).value;
                final boolean correctOutputs = resultTx.getOutputs().size() > 1;
                validTx = correctOutputs && Math.abs(txFeeAsLong - calculatedFeeAsLong) < tolerance;

                if (resultTx.getOutputs().size() == 1) {
                    // We have the rare case that both inputs equalled the required fees, so both did not render 
                    // a change output.
                    // We need to add artificially a change output as the OP_RETURN is not allowed as only output
                    valueToForceChange = Transaction.MIN_NONDUST_OUTPUT;
                }
            }
            while (!validTx);

            // Sign all BTC inputs
            int startIndex = preparedSquTxInputs.size();
            int endIndex = resultTx.getInputs().size();
            for (int i = startIndex; i < endIndex; i++) {
                TransactionInput txIn = resultTx.getInputs().get(i);
                signTransactionInput(resultTx, txIn, i);
                checkScriptSig(resultTx, txIn, i);
            }

            checkWalletConsistency();
            verifyTransaction(resultTx);

            printTx("Signed resultTx", resultTx);
            return resultTx;
        } catch (InsufficientMoneyException e) {
            throw new InsufficientFundsException("The fees for that preparedSquTx exceed the available funds " +
                    "or the resulting output value is below the min. dust value:\n" +
                    "Missing " + (e.missing != null ? e.missing.toFriendlyString() : "null"));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send funds to a CompensationRequest
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void fundCompensationRequest(Coin amount, String btcAddress, Address squAddressForCompensationRequestFunding, FutureCallback<Transaction> callback) {
        //TODO
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AddressEntry
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AddressEntry getOrCreateAddressEntry(String offerId, AddressEntry.Context context) {
        Optional<AddressEntry> addressEntry = getAddressEntryListAsImmutableList().stream()
                .filter(e -> offerId.equals(e.getOfferId()))
                .filter(e -> context == e.getContext())
                .findAny();
        if (addressEntry.isPresent())
            return addressEntry.get();
        else
            return addressEntryList.addAddressEntry(new AddressEntry(wallet.freshReceiveKey(), wallet.getParams(), context, offerId));
    }

    public AddressEntry getOrCreateAddressEntry(AddressEntry.Context context) {
        Optional<AddressEntry> addressEntry = getAddressEntryListAsImmutableList().stream()
                .filter(e -> context == e.getContext())
                .findAny();
        if (addressEntry.isPresent())
            return addressEntry.get();
        else
            return addressEntryList.addAddressEntry(new AddressEntry(wallet.freshReceiveKey(), wallet.getParams(), context));
    }

    public AddressEntry getOrCreateUnusedAddressEntry(AddressEntry.Context context) {
        Optional<AddressEntry> addressEntry = getAddressEntryListAsImmutableList().stream()
                .filter(e -> context == e.getContext())
                .filter(e -> getNumTxOutputsForAddress(e.getAddress()) == 0)
                .findAny();
        if (addressEntry.isPresent())
            return addressEntry.get();
        else
            return addressEntryList.addAddressEntry(new AddressEntry(wallet.freshReceiveKey(), wallet.getParams(), context));
    }

    private Optional<AddressEntry> findAddressEntry(String address, AddressEntry.Context context) {
        return getAddressEntryListAsImmutableList().stream()
                .filter(e -> address.equals(e.getAddressString()))
                .filter(e -> context == e.getContext())
                .findAny();
    }

    public List<AddressEntry> getAvailableAddressEntries() {
        return getAddressEntryListAsImmutableList().stream()
                .filter(addressEntry -> AddressEntry.Context.AVAILABLE == addressEntry.getContext())
                .collect(Collectors.toList());
    }

    public List<AddressEntry> getAddressEntries(AddressEntry.Context context) {
        return getAddressEntryListAsImmutableList().stream()
                .filter(addressEntry -> context == addressEntry.getContext())
                .collect(Collectors.toList());
    }

    public List<AddressEntry> getFundedAvailableAddressEntries() {
        return getAvailableAddressEntries().stream()
                .filter(addressEntry -> getBalanceForAddress(addressEntry.getAddress()).isPositive())
                .collect(Collectors.toList());
    }

    private List<AddressEntry> getAddressEntryListAsImmutableList() {
        return ImmutableList.copyOf(addressEntryList);
    }

    public void swapTradeEntryToAvailableEntry(String offerId, AddressEntry.Context context) {
        Optional<AddressEntry> addressEntryOptional = getAddressEntryListAsImmutableList().stream()
                .filter(e -> offerId.equals(e.getOfferId()))
                .filter(e -> context == e.getContext())
                .findAny();
        addressEntryOptional.ifPresent(addressEntryList::swapToAvailable);
    }

    public void swapAnyTradeEntryContextToAvailableEntry(String offerId) {
        swapTradeEntryToAvailableEntry(offerId, AddressEntry.Context.OFFER_FUNDING);
        swapTradeEntryToAvailableEntry(offerId, AddressEntry.Context.RESERVED_FOR_TRADE);
        swapTradeEntryToAvailableEntry(offerId, AddressEntry.Context.MULTI_SIG);
        swapTradeEntryToAvailableEntry(offerId, AddressEntry.Context.TRADE_PAYOUT);
    }

    public void saveAddressEntryList() {
        addressEntryList.queueUpForSave();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Balance
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getSavingWalletBalance() {
        return Coin.valueOf(getFundedAvailableAddressEntries().stream()
                .mapToLong(addressEntry -> getBalanceForAddress(addressEntry.getAddress()).value)
                .sum());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Double spend unconfirmed transaction (unlock in case we got into a tx with a too low mining fee)
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void doubleSpendTransaction(String txId, Runnable resultHandler, ErrorMessageHandler errorMessageHandler)
            throws InsufficientFundsException {
        AddressEntry addressEntry = getOrCreateUnusedAddressEntry(AddressEntry.Context.AVAILABLE);
        checkNotNull(addressEntry.getAddress(), "addressEntry.getAddress() must not be null");
        Optional<Transaction> transactionOptional = wallet.getTransactions(true).stream()
                .filter(t -> t.getHashAsString().equals(txId))
                .findAny();
        if (transactionOptional.isPresent()) {
            Transaction txToDoubleSpend = transactionOptional.get();
            Address toAddress = addressEntry.getAddress();
            final TransactionConfidence.ConfidenceType confidenceType = txToDoubleSpend.getConfidence().getConfidenceType();
            if (confidenceType == TransactionConfidence.ConfidenceType.PENDING) {
                log.debug("txToDoubleSpend no. of inputs " + txToDoubleSpend.getInputs().size());

                Transaction newTransaction = new Transaction(params);
                txToDoubleSpend.getInputs().stream().forEach(input -> {
                            final TransactionOutput connectedOutput = input.getConnectedOutput();
                            if (connectedOutput != null &&
                                    connectedOutput.isMine(wallet) &&
                                    connectedOutput.getParentTransaction() != null &&
                                    connectedOutput.getParentTransaction().getConfidence() != null &&
                                    input.getValue() != null) {
                                //if (connectedOutput.getParentTransaction().getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
                                newTransaction.addInput(new TransactionInput(params,
                                        newTransaction,
                                        new byte[]{},
                                        new TransactionOutPoint(params, input.getOutpoint().getIndex(),
                                                new Transaction(params, connectedOutput.getParentTransaction().bitcoinSerialize())),
                                        Coin.valueOf(input.getValue().value)));
                               /* } else {
                                    log.warn("Confidence of parent tx is not of type BUILDING: ConfidenceType=" +
                                            connectedOutput.getParentTransaction().getConfidence().getConfidenceType());
                                }*/
                            }
                        }
                );

                log.info("newTransaction no. of inputs " + newTransaction.getInputs().size());
                log.info("newTransaction size in kB " + newTransaction.bitcoinSerialize().length / 1024);

                if (!newTransaction.getInputs().isEmpty()) {
                    Coin amount = Coin.valueOf(newTransaction.getInputs().stream()
                            .mapToLong(input -> input.getValue() != null ? input.getValue().value : 0)
                            .sum());
                    newTransaction.addOutput(amount, toAddress);

                    try {
                        Coin fee;
                        int counter = 0;
                        int txSize = 0;
                        Transaction tx;
                        Wallet.SendRequest sendRequest;
                        Coin txFeeForWithdrawalPerByte = getTxFeeForWithdrawalPerByte();
                        do {
                            counter++;
                            fee = txFeeForWithdrawalPerByte.multiply(txSize);
                            if (fee.compareTo(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE) < 0)
                                fee = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;

                            newTransaction.clearOutputs();
                            newTransaction.addOutput(amount.subtract(fee), toAddress);

                            sendRequest = Wallet.SendRequest.forTx(newTransaction);
                            sendRequest.fee = fee;
                            sendRequest.feePerKb = Coin.ZERO;
                            sendRequest.aesKey = aesKey;
                            sendRequest.coinSelector = new TradeWalletCoinSelector(params, toAddress);
                            sendRequest.changeAddress = toAddress;
                            wallet.completeTx(sendRequest);
                            tx = sendRequest.tx;
                            txSize = tx.bitcoinSerialize().length;
                            printTx("FeeEstimationTransaction", tx);
                            sendRequest.tx.getOutputs().stream().forEach(o -> log.debug("Output value " + o.getValue().toFriendlyString()));
                        }
                        while (counter < 10 && Math.abs(tx.getFee().value - txFeeForWithdrawalPerByte.multiply(txSize).value) > 1000);
                        if (counter == 10)
                            log.error("Could not calculate the fee. Tx=" + tx);


                        Wallet.SendResult sendResult = null;
                        try {
                            sendRequest = Wallet.SendRequest.forTx(newTransaction);
                            sendRequest.fee = fee;
                            sendRequest.feePerKb = Coin.ZERO;
                            sendRequest.aesKey = aesKey;
                            sendRequest.coinSelector = new TradeWalletCoinSelector(params, toAddress);
                            sendRequest.changeAddress = toAddress;
                            sendResult = wallet.sendCoins(sendRequest);
                        } catch (InsufficientMoneyException e) {
                            // in some cases getFee did not calculate correctly and we still get an InsufficientMoneyException
                            log.warn("We still have a missing fee " + (e.missing != null ? e.missing.toFriendlyString() : ""));

                            amount = amount.subtract(e.missing);
                            newTransaction.clearOutputs();
                            newTransaction.addOutput(amount, toAddress);

                            sendRequest = Wallet.SendRequest.forTx(newTransaction);
                            sendRequest.fee = fee;
                            sendRequest.feePerKb = Coin.ZERO;
                            sendRequest.aesKey = aesKey;
                            sendRequest.coinSelector = new TradeWalletCoinSelector(params, toAddress, false);
                            sendRequest.changeAddress = toAddress;

                            try {
                                sendResult = wallet.sendCoins(sendRequest);
                                printTx("FeeEstimationTransaction", newTransaction);
                            } catch (InsufficientMoneyException e2) {
                                errorMessageHandler.handleErrorMessage("We did not get the correct fee calculated. " + (e2.missing != null ? e2.missing.toFriendlyString() : ""));
                            }
                        }
                        if (sendResult != null) {
                            log.debug("Broadcasting double spending transaction. " + newTransaction);
                            Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<Transaction>() {
                                @Override
                                public void onSuccess(Transaction result) {
                                    log.info("Double spending transaction published. " + result);
                                    resultHandler.run();
                                }

                                @Override
                                public void onFailure(@NotNull Throwable t) {
                                    log.info("Broadcasting double spending transaction failed. " + t.getMessage());
                                    errorMessageHandler.handleErrorMessage(t.getMessage());
                                }
                            });
                        }

                    } catch (InsufficientMoneyException e) {
                        throw new InsufficientFundsException("The fees for that transaction exceed the available funds " +
                                "or the resulting output value is below the min. dust value:\n" +
                                "Missing " + (e.missing != null ? e.missing.toFriendlyString() : "null"));
                    }
                } else {
                    errorMessageHandler.handleErrorMessage("We could not find inputs we control in the transaction we want to double spend.");
                }
            } else if (confidenceType == TransactionConfidence.ConfidenceType.BUILDING) {
                errorMessageHandler.handleErrorMessage("That transaction is already in the blockchain so we cannot double spend it.");
            } else if (confidenceType == TransactionConfidence.ConfidenceType.DEAD) {
                errorMessageHandler.handleErrorMessage("One of the inputs of that transaction has been already double spent.");
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Withdrawal Fee calculation
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Transaction getFeeEstimationTransaction(String fromAddress,
                                                   String toAddress,
                                                   Coin amount,
                                                   AddressEntry.Context context)
            throws AddressFormatException, AddressEntryException, InsufficientFundsException {

        Optional<AddressEntry> addressEntry = findAddressEntry(fromAddress, context);
        if (!addressEntry.isPresent())
            throw new AddressEntryException("WithdrawFromAddress is not found in our wallet.");

        checkNotNull(addressEntry.get().getAddress(), "addressEntry.get().getAddress() must nto be null");

        try {
            Coin fee;
            int counter = 0;
            int txSize = 0;
            Transaction tx;
            Coin txFeeForWithdrawalPerByte = getTxFeeForWithdrawalPerByte();
            do {
                counter++;
                fee = txFeeForWithdrawalPerByte.multiply(txSize);
                if (fee.compareTo(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE) < 0)
                    fee = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;

                Wallet.SendRequest sendRequest = getSendRequest(fromAddress, toAddress, amount, fee, aesKey, context);
                wallet.completeTx(sendRequest);
                tx = sendRequest.tx;
                txSize = tx.bitcoinSerialize().length;
                printTx("FeeEstimationTransaction", tx);
            }
            while (counter < 10 && Math.abs(tx.getFee().value - txFeeForWithdrawalPerByte.multiply(txSize).value) > 1000);
            if (counter == 10)
                log.error("Could not calculate the fee. Tx=" + tx);

            return tx;
        } catch (InsufficientMoneyException e) {
            throw new InsufficientFundsException("The fees for that transaction exceed the available funds " +
                    "or the resulting output value is below the min. dust value:\n" +
                    "Missing " + (e.missing != null ? e.missing.toFriendlyString() : "null"));
        }
    }

    public Transaction getFeeEstimationTransactionForMultipleAddresses(Set<String> fromAddresses,
                                                                       String toAddress,
                                                                       Coin amount)
            throws AddressFormatException, AddressEntryException, InsufficientFundsException {
        Set<AddressEntry> addressEntries = fromAddresses.stream()
                .map(address -> {
                    Optional<AddressEntry> addressEntryOptional = findAddressEntry(address, AddressEntry.Context.AVAILABLE);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.OFFER_FUNDING);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.TRADE_PAYOUT);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.ARBITRATOR);
                    return addressEntryOptional;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        if (addressEntries.isEmpty())
            throw new AddressEntryException("No Addresses for withdraw  found in our wallet");

        try {
            Coin fee;
            int counter = 0;
            int txSize = 0;
            Transaction tx;
            Coin txFeeForWithdrawalPerByte = getTxFeeForWithdrawalPerByte();
            do {
                counter++;
                fee = txFeeForWithdrawalPerByte.multiply(txSize);
                if (fee.compareTo(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE) < 0)
                    fee = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;
                Wallet.SendRequest sendRequest = getSendRequestForMultipleAddresses(fromAddresses, toAddress, amount, fee, null, aesKey);
                wallet.completeTx(sendRequest);
                tx = sendRequest.tx;
                txSize = tx.bitcoinSerialize().length;
                printTx("FeeEstimationTransactionForMultipleAddresses", tx);
            }
            while (counter < 10 && Math.abs(tx.getFee().value - txFeeForWithdrawalPerByte.multiply(txSize).value) > 1000);
            if (counter == 10)
                log.error("Could not calculate the fee. Tx=" + tx);

            return tx;
        } catch (InsufficientMoneyException e) {
            throw new InsufficientFundsException("The fees for that transaction exceed the available funds " +
                    "or the resulting output value is below the min. dust value:\n" +
                    "Missing " + (e.missing != null ? e.missing.toFriendlyString() : "null"));
        }
    }

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

        printTx("sendFunds", sendResult.tx);
        return sendResult.tx.getHashAsString();
    }

    public String sendFundsForMultipleAddresses(Set<String> fromAddresses,
                                                String toAddress,
                                                Coin receiverAmount,
                                                Coin fee,
                                                @Nullable String changeAddress,
                                                @Nullable KeyParameter aesKey,
                                                FutureCallback<Transaction> callback) throws AddressFormatException,
            AddressEntryException, InsufficientMoneyException {

        Wallet.SendRequest request = getSendRequestForMultipleAddresses(fromAddresses, toAddress, receiverAmount, fee, changeAddress, aesKey);
        Wallet.SendResult sendResult = wallet.sendCoins(request);
        Futures.addCallback(sendResult.broadcastComplete, callback);

        printTx("sendFunds", sendResult.tx);
        return sendResult.tx.getHashAsString();
    }

    private Wallet.SendRequest getSendRequest(String fromAddress,
                                              String toAddress,
                                              Coin amount,
                                              Coin fee,
                                              @Nullable KeyParameter aesKey,
                                              AddressEntry.Context context) throws AddressFormatException,
            AddressEntryException {
        Transaction tx = new Transaction(params);
        checkArgument(Restrictions.isAboveDust(amount, fee),
                "The amount is too low (dust limit).");
        tx.addOutput(amount.subtract(fee), new Address(params, toAddress));

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.fee = fee;
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.aesKey = aesKey;
        sendRequest.shuffleOutputs = false;
        Optional<AddressEntry> addressEntry = findAddressEntry(fromAddress, context);
        if (!addressEntry.isPresent())
            throw new AddressEntryException("WithdrawFromAddress is not found in our wallet.");

        checkNotNull(addressEntry.get(), "addressEntry.get() must not be null");
        checkNotNull(addressEntry.get().getAddress(), "addressEntry.get().getAddress() must not be null");
        sendRequest.coinSelector = new TradeWalletCoinSelector(params, addressEntry.get().getAddress());
        sendRequest.changeAddress = addressEntry.get().getAddress();
        return sendRequest;
    }

    private Wallet.SendRequest getSendRequestForMultipleAddresses(Set<String> fromAddresses,
                                                                  String toAddress,
                                                                  Coin amount,
                                                                  Coin fee,
                                                                  @Nullable String changeAddress,
                                                                  @Nullable KeyParameter aesKey) throws
            AddressFormatException, AddressEntryException {
        Transaction tx = new Transaction(params);
        checkArgument(Restrictions.isAboveDust(amount),
                "The amount is too low (dust limit).");
        tx.addOutput(amount.subtract(fee), new Address(params, toAddress));

        Wallet.SendRequest sendRequest = Wallet.SendRequest.forTx(tx);
        sendRequest.fee = fee;
        sendRequest.feePerKb = Coin.ZERO;
        sendRequest.aesKey = aesKey;
        sendRequest.shuffleOutputs = false;
        Set<AddressEntry> addressEntries = fromAddresses.stream()
                .map(address -> {
                    Optional<AddressEntry> addressEntryOptional = findAddressEntry(address, AddressEntry.Context.AVAILABLE);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.OFFER_FUNDING);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.TRADE_PAYOUT);
                    if (!addressEntryOptional.isPresent())
                        addressEntryOptional = findAddressEntry(address, AddressEntry.Context.ARBITRATOR);
                    return addressEntryOptional;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        if (addressEntries.isEmpty())
            throw new AddressEntryException("No Addresses for withdraw found in our wallet");

        sendRequest.coinSelector = new MultiAddressesCoinSelector(params, addressEntries);
        Optional<AddressEntry> addressEntryOptional = Optional.empty();
        AddressEntry changeAddressAddressEntry = null;
        if (changeAddress != null)
            addressEntryOptional = findAddressEntry(changeAddress, AddressEntry.Context.AVAILABLE);

        if (addressEntryOptional.isPresent()) {
            changeAddressAddressEntry = addressEntryOptional.get();
        } else {
            ArrayList<AddressEntry> list = new ArrayList<>(addressEntries);
            if (!list.isEmpty())
                changeAddressAddressEntry = list.get(0);
        }
        checkNotNull(changeAddressAddressEntry, "change address must not be null");
        sendRequest.changeAddress = changeAddressAddressEntry.getAddress();
        return sendRequest;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Util
    ///////////////////////////////////////////////////////////////////////////////////////////


}
