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

package io.bisq.btc.wallet;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import io.bisq.btc.exceptions.TransactionVerificationException;
import io.bisq.btc.exceptions.WalletException;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.dao.blockchain.BsqBlockchainManager;
import io.bisq.dao.blockchain.BsqUTXO;
import io.bisq.messages.btc.Restrictions;
import io.bisq.messages.btc.provider.fee.FeeService;
import io.bisq.messages.user.Preferences;
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

public class BsqWalletService extends WalletService {
    private static final Logger log = LoggerFactory.getLogger(BsqWalletService.class);

    private final BsqBlockchainManager bsqBlockchainManager;
    private final BsqCoinSelector bsqCoinSelector;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqWalletService(WalletsSetup walletsSetup,
                            BsqBlockchainManager bsqBlockchainManager,
                            Preferences preferences,
                            FeeService feeService) {
        super(walletsSetup,
                preferences,
                feeService);

        this.bsqBlockchainManager = bsqBlockchainManager;
        this.bsqCoinSelector = new BsqCoinSelector(true);

        walletsSetup.addSetupCompletedHandler(() -> {
            wallet = walletsSetup.getBsqWallet();
            wallet.setCoinSelector(bsqCoinSelector);

            wallet.addEventListener(walletEventListener);
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
        return "BSQ wallet:\n" +
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

    public void requestBsqUtxo(@Nullable ResultHandler resultHandler, @Nullable ErrorMessageHandler errorMessageHandler) {
        if (bsqBlockchainManager.isUtxoAvailable()) {
            applyUtxoSetToUTXOProvider(bsqBlockchainManager.getUtxoByTxIdMap());
            if (resultHandler != null)
                resultHandler.handleResult();
        } else {
            bsqBlockchainManager.addUtxoListener(utxoByTxIdMap -> {
                applyUtxoSetToUTXOProvider(utxoByTxIdMap);
                if (resultHandler != null)
                    resultHandler.handleResult();
            });
        }
    }

    private void applyUtxoSetToUTXOProvider(Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap) {
        Set<BsqUTXO> utxoSet = new HashSet<>();
        utxoByTxIdMap.entrySet().stream()
                .forEach(e -> e.getValue().entrySet().stream()
                        .forEach(u -> utxoSet.add(u.getValue())));
        bsqCoinSelector.setUtxoSet(utxoSet);
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
        // printTx("BSQ wallet: Signed Tx", tx);
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
        CoinSelection coinSelection = bsqCoinSelector.select(fee, getTransactionOutputsFromUtxoProvider());
        coinSelection.gathered.stream().forEach(tx::addInput);
        Coin change = bsqCoinSelector.getChange(fee, coinSelection);
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
