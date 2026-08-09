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

package bisq.core.provider.mempool;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.burningman.BtcFeeReceiverService;
import bisq.core.dao.burningman.BurningManAddressList;
import bisq.core.dao.burningman.BurningManAddressListService;
import bisq.core.dao.burningman.BurningManPresentationService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.filter.FilterPolicyService;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.user.Preferences;

import bisq.network.Socks5ProxyProvider;
import bisq.network.http.HttpException;

import bisq.common.UserThread;
import bisq.common.config.Config;

import org.bitcoinj.core.Coin;

import com.google.inject.Inject;

import javax.inject.Singleton;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
@Singleton
public class MempoolService {
    // Upper bound for the cause chain walk in isTxUnknownResponse.
    private static final int MAX_CAUSE_CHAIN_DEPTH = 20;

    private final Socks5ProxyProvider socks5ProxyProvider;
    private final Config config;
    private final Preferences preferences;
    private final FilterPolicyService filterPolicyService;
    private final DaoFacade daoFacade;
    private final DaoStateService daoStateService;
    private final BurningManAddressListService burningManAddressListService;
    private final BurningManPresentationService burningManPresentationService;
    @Getter
    private int outstandingRequests = 0;

    @Inject
    public MempoolService(Socks5ProxyProvider socks5ProxyProvider,
                          Config config,
                          Preferences preferences,
                          FilterPolicyService filterPolicyService,
                          DaoFacade daoFacade,
                          DaoStateService daoStateService,
                          BurningManAddressListService burningManAddressListService,
                          BurningManPresentationService burningManPresentationService) {
        this.socks5ProxyProvider = socks5ProxyProvider;
        this.config = config;
        this.preferences = preferences;
        this.filterPolicyService = filterPolicyService;
        this.daoFacade = daoFacade;
        this.daoStateService = daoStateService;
        this.burningManAddressListService = burningManAddressListService;
        this.burningManPresentationService = burningManPresentationService;
    }

    public void onAllServicesInitialized() {
    }

    public boolean canRequestBeMade() {
        return daoStateService.isParseBlockChainComplete() && outstandingRequests < 5; // limit max simultaneous lookups
    }

    public boolean canRequestBeMade(OfferPayload offerPayload) {
        // when validating a new offer, wait 1 block for the tx to propagate
        return offerPayload.getBlockHeightAtOfferCreation() < daoStateService.getChainHeight() && canRequestBeMade();
    }

    public void validateOfferMakerTx(OfferPayload offerPayload, Consumer<TxValidator> resultHandler) {
        validateOfferMakerTx(new TxValidator(daoStateService, offerPayload.getOfferFeePaymentTxId(), Coin.valueOf(offerPayload.getAmount()),
                offerPayload.isCurrencyForMakerFeeBtc(), offerPayload.getBlockHeightAtOfferCreation(), filterPolicyService), resultHandler);
    }

    public void validateOfferMakerTx(TxValidator txValidator, Consumer<TxValidator> resultHandler) {
        if (!isServiceSupported()) {
            UserThread.runAfter(() -> resultHandler.accept(txValidator.endResult(FeeValidationStatus.ACK_CHECK_BYPASSED)), 1);
            return;
        }
        MempoolRequest mempoolRequest = new MempoolRequest(preferences, socks5ProxyProvider, config.allowLanForHttpRequests, config.allowClearnetHttpRequests);
        validateOfferMakerTx(mempoolRequest, txValidator, resultHandler);
    }

    public void validateOfferTakerTx(Trade trade, Consumer<TxValidator> resultHandler) {
        validateOfferTakerTx(new TxValidator(daoStateService, trade.getTakerFeeTxId(), trade.getAmount(),
                trade.isCurrencyForTakerFeeBtc(), trade.getLockTime(), filterPolicyService), resultHandler);
    }

    public void validateOfferTakerTx(TxValidator txValidator, Consumer<TxValidator> resultHandler) {
        if (!isServiceSupported()) {
            UserThread.runAfter(() -> resultHandler.accept(txValidator.endResult(FeeValidationStatus.ACK_CHECK_BYPASSED)), 1);
            return;
        }
        MempoolRequest mempoolRequest = new MempoolRequest(preferences, socks5ProxyProvider, config.allowLanForHttpRequests, config.allowClearnetHttpRequests);
        validateOfferTakerTx(mempoolRequest, txValidator, resultHandler);
    }

    public void checkTxIsConfirmed(String txId, Consumer<TxValidator> resultHandler) {
        TxValidator txValidator = new TxValidator(daoStateService, txId, filterPolicyService);
        if (!isServiceSupported()) {
            UserThread.runAfter(() -> resultHandler.accept(txValidator.endResult(FeeValidationStatus.ACK_CHECK_BYPASSED)), 1);
            return;
        }
        MempoolRequest mempoolRequest = new MempoolRequest(preferences, socks5ProxyProvider, config.allowLanForHttpRequests, config.allowClearnetHttpRequests);
        checkTxIsConfirmed(mempoolRequest, txValidator, new AtomicBoolean(true), resultHandler);
    }

    public CompletableFuture<String> requestTxAsHex(String txId) {
        outstandingRequests++;
        return new MempoolRequest(preferences, socks5ProxyProvider, config.allowLanForHttpRequests, config.allowClearnetHttpRequests)
                .requestTxAsHex(txId)
                .whenComplete((result, throwable) -> outstandingRequests--);
    }

    private void validateOfferMakerTx(MempoolRequest mempoolRequest,
                                      TxValidator txValidator,
                                      Consumer<TxValidator> resultHandler) {
        SettableFuture<String> future = SettableFuture.create();
        Futures.addCallback(future, callbackForMakerTxValidation(mempoolRequest, txValidator, resultHandler), MoreExecutors.directExecutor());
        mempoolRequest.getTxStatus(future, txValidator.getTxId());
    }

    private void validateOfferTakerTx(MempoolRequest mempoolRequest,
                                      TxValidator txValidator,
                                      Consumer<TxValidator> resultHandler) {
        SettableFuture<String> future = SettableFuture.create();
        Futures.addCallback(future, callbackForTakerTxValidation(mempoolRequest, txValidator, resultHandler), MoreExecutors.directExecutor());
        mempoolRequest.getTxStatus(future, txValidator.getTxId());
    }

    private void checkTxIsConfirmed(MempoolRequest mempoolRequest,
                                    TxValidator txValidator,
                                    AtomicBoolean everyProviderAnswered404,
                                    Consumer<TxValidator> resultHandler) {
        SettableFuture<String> future = SettableFuture.create();
        Futures.addCallback(future, callbackForTxRequest(mempoolRequest, txValidator, everyProviderAnswered404, resultHandler),
                MoreExecutors.directExecutor());
        try {
            mempoolRequest.getTxStatus(future, txValidator.getTxId());
        } catch (RuntimeException e) {
            // The request could not even be dispatched. Feed that through the callback so the
            // outstanding request accounting stays balanced and the caller still gets a result.
            future.setException(e);
        }
    }

    private FutureCallback<String> callbackForMakerTxValidation(MempoolRequest theRequest,
                                                                TxValidator txValidator,
                                                                Consumer<TxValidator> resultHandler) {
        outstandingRequests++;
        FutureCallback<String> myCallback = new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable String jsonTxt) {
                UserThread.execute(() -> {
                    outstandingRequests--;
                    if (txValidator.getIsFeeCurrencyBtc() != null && txValidator.getIsFeeCurrencyBtc()) {
                        resultHandler.accept(txValidator.parseJsonValidateMakerFeeTx(jsonTxt, getAllBtcFeeReceivers()));
                    } else {
                        resultHandler.accept(txValidator.validateBsqFeeTx(true));
                    }
                });
            }

            @Override
            public void onFailure(Throwable throwable) {
                log.warn("onFailure - {}", throwable.toString());
                UserThread.execute(() -> {
                    outstandingRequests--;
                    if (theRequest.switchToAnotherProvider()) {
                        validateOfferMakerTx(theRequest, txValidator, resultHandler);
                    } else {
                        // exhausted all providers, let user know of failure
                        resultHandler.accept(txValidator.endResult(FeeValidationStatus.NACK_BTC_TX_NOT_FOUND));
                    }
                });
            }
        };
        return myCallback;
    }

    private FutureCallback<String> callbackForTakerTxValidation(MempoolRequest theRequest,
                                                                TxValidator txValidator,
                                                                Consumer<TxValidator> resultHandler) {
        outstandingRequests++;
        FutureCallback<String> myCallback = new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable String jsonTxt) {
                UserThread.execute(() -> {
                    outstandingRequests--;
                    if (txValidator.getIsFeeCurrencyBtc() != null && txValidator.getIsFeeCurrencyBtc()) {
                        resultHandler.accept(txValidator.parseJsonValidateTakerFeeTx(jsonTxt, getAllBtcFeeReceivers()));
                    } else {
                        resultHandler.accept(txValidator.validateBsqFeeTx(false));
                    }
                });
            }

            @Override
            public void onFailure(Throwable throwable) {
                log.warn("onFailure - {}", throwable.toString());
                UserThread.execute(() -> {
                    outstandingRequests--;
                    if (theRequest.switchToAnotherProvider()) {
                        validateOfferTakerTx(theRequest, txValidator, resultHandler);
                    } else {
                        // exhausted all providers, let user know of failure
                        resultHandler.accept(txValidator.endResult(FeeValidationStatus.NACK_BTC_TX_NOT_FOUND));
                    }
                });
            }
        };
        return myCallback;
    }

    private FutureCallback<String> callbackForTxRequest(MempoolRequest theRequest,
                                                        TxValidator txValidator,
                                                        AtomicBoolean everyProviderAnswered404,
                                                        Consumer<TxValidator> resultHandler) {
        outstandingRequests++;
        FutureCallback<String> myCallback = new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable String jsonTxt) {
                UserThread.execute(() -> {
                    outstandingRequests--;
                    txValidator.setJsonTxt(jsonTxt);
                    resultHandler.accept(txValidator);
                });
            }

            @Override
            public void onFailure(Throwable throwable) {
                log.warn("onFailure - {}", throwable.toString());
                UserThread.execute(() -> {
                    outstandingRequests--;
                    // Only a definitive "tx unknown" (HTTP 404) answer tells us anything about the tx. Any
                    // other failure is a transport problem, so we must not conclude the tx does not exist.
                    if (!isTxUnknownResponse(throwable)) {
                        everyProviderAnswered404.set(false);
                    }
                    if (theRequest.switchToAnotherProvider()) {
                        checkTxIsConfirmed(theRequest, txValidator, everyProviderAnswered404, resultHandler);
                    } else {
                        // exhausted all providers, let user know of failure
                        resultHandler.accept(txValidator.endResult(everyProviderAnswered404.get() ?
                                FeeValidationStatus.NACK_BTC_TX_NOT_FOUND :
                                FeeValidationStatus.NACK_TX_LOOKUP_UNREACHABLE));
                    }
                });

            }
        };
        return myCallback;
    }

    // The block explorers answer with HTTP 404 if they do not know the tx. The HttpException carrying that
    // response code is wrapped into an IOException by the http client, so we walk the cause chain. The
    // depth is bounded so that a self referencing cause cannot spin the calling thread. Giving up early
    // reports the failure as a transport problem, which is the safe direction.
    @VisibleForTesting
    static boolean isTxUnknownResponse(Throwable throwable) {
        Throwable candidate = throwable;
        for (int depth = 0; candidate != null && depth < MAX_CAUSE_CHAIN_DEPTH; candidate = candidate.getCause(), depth++) {
            if (candidate instanceof HttpException && ((HttpException) candidate).getResponseCode() == 404) {
                return true;
            }
        }
        return false;
    }

    // /////////////////////////////

    @VisibleForTesting
    List<String> getAllBtcFeeReceivers() {
        List<String> btcFeeReceivers = new ArrayList<>();
        btcFeeReceivers.addAll(daoFacade.getAllDonationAddresses());
        addBurningManAddressListLegacyAddress(btcFeeReceivers);
        btcFeeReceivers.addAll(BtcFeeReceiverService.getConfiguredReceiverAddresses(
                filterPolicyService.getBtcFeeReceiverAddresses()));

        // We use all BM who had ever had burned BSQ to avoid if a BM just got "deactivated" due decayed burn amounts
        // that it would trigger a failure here. There is still a small risk that new BM used for the trade fee payment
        // is not yet visible to the other peer, but that should be very unlikely.
        // We also get all addresses related to comp. requests, so this list is still rather long, but much shorter
        // than if we would use all addresses of all BM.
        Set<String> distributedBMAddresses = burningManPresentationService.getBurningManCandidatesByName().values().stream()
                .filter(burningManCandidate -> burningManCandidate.getAccumulatedBurnAmount() > 0)
                .flatMap(burningManCandidate -> burningManCandidate.getAllAddresses().stream())
                .collect(Collectors.toSet());
        btcFeeReceivers.addAll(distributedBMAddresses);

        return btcFeeReceivers;
    }

    private void addBurningManAddressListLegacyAddress(List<String> btcFeeReceivers) {
        int latestVersion = burningManAddressListService.getLatestVersion();
        BurningManAddressList addressList = burningManAddressListService.getAddressList(latestVersion);
        if (addressList.isForCurrentNetwork()) {
            btcFeeReceivers.add(addressList.getLegacyBurningManAddress());
        }
    }

    private boolean isServiceSupported() {
        if (filterPolicyService.isMempoolValidationDisabled()) {
            log.info("MempoolService bypassed by filter setting disableMempoolValidation=true");
            return false;
        }
        if (config.bypassMempoolValidation) {
            log.info("MempoolService bypassed by config setting bypassMempoolValidation=true");
            return false;
        }
        if (!Config.baseCurrencyNetwork().isMainnet()) {
            log.info("MempoolService only supports mainnet");
            return false;
        }
        if (!canRequestBeMade()) {
            log.info("Tx Validation bypassed as service is not ready");
            return false;
        }
        return true;
    }
}
