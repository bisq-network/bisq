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

package io.bisq.core.dao.compensation;

import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.Hash;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.exceptions.TransactionVerificationException;
import io.bisq.core.btc.exceptions.WalletException;
import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.ChangeBelowDustException;
import io.bisq.core.dao.DaoConstants;
import io.bisq.core.dao.DaoPeriodService;
import io.bisq.core.dao.blockchain.BsqBlockChainChangeDispatcher;
import io.bisq.core.dao.blockchain.BsqBlockChainListener;
import io.bisq.core.dao.blockchain.parse.BsqBlockChain;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.storage.HashMapChangedListener;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.DeterministicKey;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.PublicKey;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class CompensationRequestManager implements PersistedDataHost, BsqBlockChainListener, HashMapChangedListener {

    private final P2PService p2PService;
    private final DaoPeriodService daoPeriodService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final BsqBlockChain bsqBlockChain;
    private final Storage<CompensationRequestList> compensationRequestsStorage;
    private final PublicKey signaturePubKey;
    private final FeeService feeService;

    @Getter
    private final ObservableList<CompensationRequest> allRequests = FXCollections.observableArrayList();
    @Getter
    private final FilteredList<CompensationRequest> activeRequests = new FilteredList<>(allRequests);
    @Getter
    private final FilteredList<CompensationRequest> pastRequests = new FilteredList<>(allRequests);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public CompensationRequestManager(P2PService p2PService,
                                      BsqWalletService bsqWalletService,
                                      BtcWalletService btcWalletService,
                                      DaoPeriodService daoPeriodService,
                                      BsqBlockChain bsqBlockChain,
                                      BsqBlockChainChangeDispatcher bsqBlockChainChangeDispatcher,
                                      KeyRing keyRing,
                                      Storage<CompensationRequestList> compensationRequestsStorage,
                                      FeeService feeService) {
        this.p2PService = p2PService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.daoPeriodService = daoPeriodService;
        this.bsqBlockChain = bsqBlockChain;
        this.compensationRequestsStorage = compensationRequestsStorage;
        this.feeService = feeService;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
        bsqBlockChainChangeDispatcher.addBsqBlockChainListener(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        p2PService.addHashSetChangedListener(this);

        // At startup the P2PDataStorage initializes earlier, otherwise we ge the listener called.
        p2PService.getP2PDataStorage().getMap().values().forEach(e -> {
            final ProtectedStoragePayload protectedStoragePayload = e.getProtectedStoragePayload();
            if (protectedStoragePayload instanceof CompensationRequestPayload)
                createCompensationRequest((CompensationRequestPayload) protectedStoragePayload, false);
        });

        // Republish own active compensationRequests
        UserThread.runAfter(() -> {
            activeRequests.stream()
                    .filter(this::isMine)
                    .forEach(e -> addToP2PNetwork(e.getPayload()));
        }, 30);

        bsqWalletService.getChainHeightProperty().addListener((observable, oldValue, newValue) -> {
            onChainHeightChanged();
        });
        onChainHeightChanged();
    }

    public void addToP2PNetwork(CompensationRequestPayload compensationRequestPayload) {
        p2PService.addProtectedStorageEntry(compensationRequestPayload, true);
    }

    public CompensationRequest prepareCompensationRequest(CompensationRequestPayload compensationRequestPayload)
            throws InsufficientMoneyException, ChangeBelowDustException, TransactionVerificationException, WalletException, IOException, CompensationAmountException {

        final Coin minRequestAmount = Restrictions.getMinCompensationRequestAmount();
        if (compensationRequestPayload.getRequestedBsq().compareTo(minRequestAmount) < 0) {
            throw new CompensationAmountException(minRequestAmount, compensationRequestPayload.getRequestedBsq());
        }

        CompensationRequest compensationRequest = new CompensationRequest(compensationRequestPayload);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            compensationRequest.setCompensationRequestFee(feeService.getCreateCompensationRequestFee());
            final Transaction preparedBurnFeeTx = bsqWalletService.getPreparedBurnFeeTx(compensationRequest.getCompensationRequestFee());
            //compensationRequest.setFeeTx(preparedBurnFeeTx);
            checkArgument(!preparedBurnFeeTx.getInputs().isEmpty(), "preparedTx inputs must not be empty");

            // We use the key of the first BSQ input for signing the data
            TransactionOutput connectedOutput = preparedBurnFeeTx.getInputs().get(0).getConnectedOutput();
            checkNotNull(connectedOutput, "connectedOutput must not be null");
            DeterministicKey bsqKeyPair = bsqWalletService.findKeyFromPubKeyHash(connectedOutput.getScriptPubKey().getPubKeyHash());
            checkNotNull(bsqKeyPair, "bsqKeyPair must not be null");

            // We get the JSON of the object excluding signature and feeTxId
            String payloadAsJson = StringUtils.deleteWhitespace(Utilities.objectToJson(compensationRequestPayload));
            // Signs a text message using the standard Bitcoin messaging signing format and returns the signature as a base64
            // encoded string.
            String signature = bsqKeyPair.signMessage(payloadAsJson);
            compensationRequestPayload.setSignature(signature);

            String dataAndSig = payloadAsJson + signature;
            byte[] dataAndSigAsBytes = dataAndSig.getBytes();
            outputStream.write(DaoConstants.OP_RETURN_TYPE_COMPENSATION_REQUEST);
            outputStream.write(Version.COMPENSATION_REQUEST_VERSION);
            outputStream.write(Hash.getSha256Ripemd160hash(dataAndSigAsBytes));
            byte opReturnData[] = outputStream.toByteArray();

            //TODO should we store the hash in the compensationRequestPayload object?

            //TODO 1 Btc output (small payment to own compensation receiving address)
            final Transaction txWithBtcFee = btcWalletService.completePreparedCompensationRequestTx(
                    compensationRequest.getRequestedBsq(),
                    compensationRequest.getIssuanceAddress(bsqWalletService),
                    preparedBurnFeeTx,
                    opReturnData);
            compensationRequest.setTx(bsqWalletService.signTx(txWithBtcFee));
        }
        return compensationRequest;
    }

    public void commitCompensationRequest(CompensationRequest compensationRequest, FutureCallback<Transaction> callback) {
        // We need to create another instance, otherwise the tx would trigger an invalid state exception
        // if it gets committed 2 times
        // We clone before commit to avoid unwanted side effects
        final Transaction tx = compensationRequest.getTx();
        bsqWalletService.commitTx(tx);

        final Transaction clonedTx = btcWalletService.getClonedTransaction(tx);
        btcWalletService.commitTx(clonedTx);

        bsqWalletService.broadcastTx(tx, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@Nullable Transaction transaction) {
                checkNotNull(transaction, "Transaction must not be null at broadcastTx callback.");
                compensationRequest.getPayload().setTxId(transaction.getHashAsString());
                addToP2PNetwork(compensationRequest.getPayload());

                callback.onSuccess(transaction);
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    public boolean removeCompensationRequest(CompensationRequest compensationRequest) {
        final CompensationRequestPayload payload = compensationRequest.getPayload();
        // We allow removal which are not confirmed yet or if it we are in the right phase
        if (isInPhaseOrUnconfirmed(payload)) {
            if (isMine(compensationRequest)) {
                removeFromList(compensationRequest);
                compensationRequestsStorage.queueUpForSave(new CompensationRequestList(getAllRequests()), 500);
                return p2PService.removeData(payload, true);
            } else {
                final String msg = "removeCompensationRequest called for a CompensationRequest which is not ours.";
                log.warn(msg);
                if (DevEnv.isDevMode())
                    throw new RuntimeException(msg);
                return false;
            }
        } else {
            final String msg = "removeCompensationRequest called with a CompensationRequest which is outside of the CompensationRequest phase.";
            log.warn(msg);
            if (DevEnv.isDevMode())
                throw new RuntimeException(msg);
            return false;
        }
    }

    private boolean isInPhaseOrUnconfirmed(CompensationRequestPayload payload) {
        return bsqBlockChain.getTxMap().get(payload.getTxId()) == null || daoPeriodService.isInPhase(payload, DaoPeriodService.Phase.COMPENSATION_REQUESTS);
    }

    public boolean isMine(CompensationRequest compensationRequest) {
        return isMine(compensationRequest.getPayload());
    }

    public boolean isMine(CompensationRequestPayload compensationRequestPayload) {
        return signaturePubKey.equals(compensationRequestPayload.getOwnerPubKey());
    }

    //TODO prob not needed anymore
    public Optional<CompensationRequest> findByAddress(String address) {
        return allRequests.stream()
                .filter(e -> e.getPayload().getBsqAddress().equals(address))
                .findAny();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            CompensationRequestList persisted = compensationRequestsStorage.initAndGetPersistedWithFileName("CompensationRequestList", 100);
            if (persisted != null) {
                this.allRequests.clear();
                this.allRequests.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry data) {
        final ProtectedStoragePayload protectedStoragePayload = data.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof CompensationRequestPayload)
            createCompensationRequest((CompensationRequestPayload) protectedStoragePayload, true);
    }

    @Override
    public void onRemoved(ProtectedStorageEntry data) {
        final ProtectedStoragePayload protectedStoragePayload = data.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof CompensationRequestPayload) {
            findCompensationRequest((CompensationRequestPayload) protectedStoragePayload).ifPresent(compensationRequest -> {
                if (isInPhaseOrUnconfirmed(compensationRequest.getPayload())) {
                    removeFromList(compensationRequest);
                    compensationRequestsStorage.queueUpForSave(new CompensationRequestList(getAllRequests()), 500);
                } else {
                    final String msg = "onRemoved called of a CompensationRequest which is outside of the CompensationRequest phase is invalid and we ignore it.";
                    log.warn(msg);
                    if (DevEnv.isDevMode())
                        throw new RuntimeException(msg);
                }
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBlockChainListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBsqBlockChainChanged() {
        updateFilteredLists();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onChainHeightChanged() {
        updateFilteredLists();
    }

    private void createCompensationRequest(CompensationRequestPayload compensationRequestPayload, boolean storeLocally) {
        if (!contains(compensationRequestPayload)) {
            allRequests.add(new CompensationRequest(compensationRequestPayload));
            updateFilteredLists();

            if (storeLocally)
                compensationRequestsStorage.queueUpForSave(new CompensationRequestList(getAllRequests()), 500);
        } else {
            if (!isMine(compensationRequestPayload))
                log.warn("We already have an item with the same CompensationRequest.");
        }
    }

    private void updateFilteredLists() {
        // TODO: Does this only need to be set once to keep the list updated?
        pastRequests.setPredicate(daoPeriodService::isInPastCycle);
        activeRequests.setPredicate(compensationRequest -> {
            return daoPeriodService.isInCurrentCycle(compensationRequest) ||
                    (bsqBlockChain.getTxMap().get(compensationRequest.getPayload().getTxId()) == null &&
                            isMine(compensationRequest));
        });
    }

    private boolean contains(CompensationRequestPayload compensationRequestPayload) {
        return findCompensationRequest(compensationRequestPayload).isPresent();
    }

    private Optional<CompensationRequest> findCompensationRequest(CompensationRequestPayload compensationRequestPayload) {
        return allRequests.stream().filter(e -> e.getPayload().equals(compensationRequestPayload)).findAny();
    }

    private void removeFromList(CompensationRequest compensationRequest) {
        allRequests.remove(compensationRequest);
        updateFilteredLists();
    }
}
