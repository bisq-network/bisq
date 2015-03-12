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

package io.bitsquare.trade;

import io.bitsquare.account.AccountSettings;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.btc.BlockChainService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.SignatureService;
import io.bitsquare.network.Message;
import io.bitsquare.network.Peer;
import io.bitsquare.offer.Direction;
import io.bitsquare.offer.Offer;
import io.bitsquare.offer.OfferBookService;
import io.bitsquare.offer.OpenOffer;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferProtocol;
import io.bitsquare.trade.protocol.trade.OfferMessage;
import io.bitsquare.trade.protocol.trade.offerer.BuyerAsOffererModel;
import io.bitsquare.trade.protocol.trade.offerer.BuyerAsOffererProtocol;
import io.bitsquare.trade.protocol.trade.offerer.messages.IsOfferAvailableResponseMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.IsOfferAvailableResponse;
import io.bitsquare.trade.protocol.trade.taker.RequestIsOfferAvailableProtocol;
import io.bitsquare.trade.protocol.trade.taker.SellerAsTakerModel;
import io.bitsquare.trade.protocol.trade.taker.SellerAsTakerProtocol;
import io.bitsquare.trade.protocol.trade.taker.messages.RequestIsOfferAvailableMessage;
import io.bitsquare.user.User;
import io.bitsquare.util.handlers.ErrorMessageHandler;
import io.bitsquare.util.handlers.ResultHandler;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The domain for the trading
 * TODO: Too messy, need to be improved a lot....
 */
public class TradeManager {
    private static final Logger log = LoggerFactory.getLogger(TradeManager.class);

    private final User user;
    private final AccountSettings accountSettings;
    private final Persistence persistence;
    private final TradeMessageService tradeMessageService;
    private final BlockChainService blockChainService;
    private final WalletService walletService;
    private final SignatureService signatureService;
    private final OfferBookService offerBookService;

    //TODO store TakerAsSellerProtocol in trade
    private final Map<String, SellerAsTakerProtocol> takerAsSellerProtocolMap = new HashMap<>();
    private final Map<String, BuyerAsOffererProtocol> offererAsBuyerProtocolMap = new HashMap<>();
    private final Map<String, RequestIsOfferAvailableProtocol> requestIsOfferAvailableProtocolMap = new HashMap<>();

    private final ObservableMap<String, OpenOffer> openOffers = FXCollections.observableHashMap();
    private final ObservableMap<String, Trade> pendingTrades = FXCollections.observableHashMap();
    private final ObservableMap<String, Trade> closedTrades = FXCollections.observableHashMap();

    // the latest pending trade
    private Trade currentPendingTrade;
    final StringProperty featureNotImplementedWarning = new SimpleStringProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TradeManager(User user, AccountSettings accountSettings, Persistence persistence,
                        TradeMessageService tradeMessageService, BlockChainService blockChainService,
                        WalletService walletService, SignatureService signatureService,
                        OfferBookService offerBookService) {
        this.user = user;
        this.accountSettings = accountSettings;
        this.persistence = persistence;
        this.tradeMessageService = tradeMessageService;
        this.blockChainService = blockChainService;
        this.walletService = walletService;
        this.signatureService = signatureService;
        this.offerBookService = offerBookService;

        Object openOffersObject = persistence.read(this, "openOffers");
        if (openOffersObject instanceof Map) {
            openOffers.putAll((Map<String, OpenOffer>) openOffersObject);
        }

        Object pendingTradesObject = persistence.read(this, "pendingTrades");
        if (pendingTradesObject instanceof Map) {
            pendingTrades.putAll((Map<String, Trade>) pendingTradesObject);
        }

        Object closedTradesObject = persistence.read(this, "closedTrades");
        if (closedTradesObject instanceof Map) {
            closedTrades.putAll((Map<String, Trade>) closedTradesObject);
        }

        tradeMessageService.addMessageHandler(this::handleNewMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cleanup() {
        tradeMessageService.removeMessageHandler(this::handleNewMessage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Manage offers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestPlaceOffer(String id,
                                  Direction direction,
                                  Fiat price,
                                  Coin amount,
                                  Coin minAmount,
                                  TransactionResultHandler resultHandler,
                                  ErrorMessageHandler errorMessageHandler) {

        BankAccount currentBankAccount = user.getCurrentBankAccount().get();
        Offer offer = new Offer(id,
                user.getMessagePublicKey(),
                direction,
                price.getValue(),
                amount,
                minAmount,
                currentBankAccount.getBankAccountType(),
                currentBankAccount.getCurrency(),
                currentBankAccount.getCountry(),
                currentBankAccount.getUid(),
                accountSettings.getAcceptedArbitrators(),
                accountSettings.getSecurityDeposit(),
                accountSettings.getAcceptedCountries(),
                accountSettings.getAcceptedLanguageLocales());


        PlaceOfferProtocol placeOfferProtocol = new PlaceOfferProtocol(
                offer,
                walletService,
                offerBookService,
                (transaction) -> {
                    createOpenOffer(offer);
                    resultHandler.handleResult(transaction);
                },
                (message, throwable) -> errorMessageHandler.handleErrorMessage(message)
        );

        placeOfferProtocol.placeOffer();
    }

    private void createOpenOffer(Offer offer) {
        OpenOffer openOffer = new OpenOffer(offer);
        openOffers.put(openOffer.getId(), openOffer);
        persistOpenOffers();

        createOffererAsBuyerProtocol(openOffer);
    }

    public void requestRemoveOpenOffer(String offerId, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        offerBookService.removeOffer(openOffers.get(offerId).getOffer(),
                () -> {
                    if (openOffers.containsKey(offerId)) {
                        openOffers.remove(offerId);
                        persistOpenOffers();
                        resultHandler.handleResult();
                    }
                    else {
                        log.error("Locally stored offers does not contain the offer with the ID " + offerId);
                        errorMessageHandler.handleErrorMessage("Locally stored offers does not contain the offer with the ID " + offerId);
                    }
                },
                (message, throwable) -> errorMessageHandler.handleErrorMessage(message));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Manage trades
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Trade createTrade(Offer offer) {
        if (pendingTrades.containsKey(offer.getId()))
            log.error("trades contains already an trade with the ID " + offer.getId());

        Trade trade = new Trade(offer);
        pendingTrades.put(offer.getId(), trade);
        persistPendingTrades();

        currentPendingTrade = trade;

        return trade;
    }

    public void closeTrade(Trade trade) {
        if (!pendingTrades.containsKey(trade.getId()))
            log.error("trades does not contain the trade with the ID " + trade.getId());

        pendingTrades.remove(trade.getId());
        persistPendingTrades();

        if (takerAsSellerProtocolMap.containsKey(trade.getId()))
            takerAsSellerProtocolMap.remove(trade.getId());
        else if (offererAsBuyerProtocolMap.containsKey(trade.getId()))
            offererAsBuyerProtocolMap.remove(trade.getId());

        closedTrades.put(trade.getId(), trade);
        persistClosedTrades();
    }

    private void removeFailedTrade(Trade trade) {
        if (!pendingTrades.containsKey(trade.getId()))
            log.error("trades does not contain the trade with the ID " + trade.getId());

        pendingTrades.remove(trade.getId());
        persistPendingTrades();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trading protocols
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void createOffererAsBuyerProtocol(OpenOffer openOffer) {
        BuyerAsOffererModel model = new BuyerAsOffererModel(
                openOffer,
                tradeMessageService,
                walletService,
                blockChainService,
                signatureService,
                user);

        openOffer.stateProperty().addListener((ov, oldValue, newValue) -> {
            log.debug("trade state = " + newValue);
            switch (newValue) {
                case OPEN:
                    break;
                case OFFER_ACCEPTED:
                    requestRemoveOpenOffer(openOffer.getId(),
                            () -> log.debug("remove offer was successful"),
                            (message) -> log.error(message));

                    Trade trade = model.getTrade();
                    pendingTrades.put(trade.getId(), trade);
                    persistPendingTrades();
                    currentPendingTrade = trade;

                    // TODO check, remove listener
                    trade.stateProperty().addListener((ov2, oldValue2, newValue2) -> {
                        log.debug("trade state = " + newValue);
                        switch (newValue2) {
                            case OPEN:
                                break;
                            case OFFERER_ACCEPTED: // only taker side
                            case DEPOSIT_PUBLISHED:
                            case DEPOSIT_CONFIRMED:
                            case FIAT_PAYMENT_STARTED:
                            case FIAT_PAYMENT_RECEIVED:
                            case PAYOUT_PUBLISHED:
                                persistPendingTrades();
                                break;
                            case OFFERER_REJECTED:
                            case FAILED:
                                removeFailedTrade(trade);
                                break;
                            default:
                                log.error("Unhandled trade state: " + newValue);
                                break;
                        }
                    });
                    break;
                default:
                    log.error("Unhandled trade state: " + newValue);
                    break;
            }
        });

        BuyerAsOffererProtocol buyerAcceptsOfferProtocol = new BuyerAsOffererProtocol(model);
        offererAsBuyerProtocolMap.put(openOffer.getId(), buyerAcceptsOfferProtocol);
        buyerAcceptsOfferProtocol.start();
    }


    public Trade requestTakeOffer(Coin amount, Offer offer) {
        Trade trade = createTrade(offer);
        trade.setTradeAmount(amount);

        // TODO check
        trade.stateProperty().addListener((ov, oldValue, newValue) -> {
            log.debug("trade state = " + newValue);
            switch (newValue) {
                case OPEN:
                    break;
                case OFFERER_ACCEPTED:
                case DEPOSIT_PUBLISHED:
                case DEPOSIT_CONFIRMED:
                case FIAT_PAYMENT_STARTED:
                case FIAT_PAYMENT_RECEIVED:
                case PAYOUT_PUBLISHED:
                    persistPendingTrades();
                    break;
                case OFFERER_REJECTED:
                case FAILED:
                    removeFailedTrade(trade);
                    break;
                default:
                    log.error("Unhandled trade state: " + newValue);
                    break;
            }
        });

        SellerAsTakerModel model = new SellerAsTakerModel(
                trade,
                tradeMessageService,
                walletService,
                blockChainService,
                signatureService,
                user);

        SellerAsTakerProtocol sellerTakesOfferProtocol = new SellerAsTakerProtocol(model);
        takerAsSellerProtocolMap.put(trade.getId(), sellerTakesOfferProtocol);

        sellerTakesOfferProtocol.handleRequestTakeOfferUIEvent();

        return trade;
    }

    //TODO we don't support interruptions yet.
    // If the user has shut down the app we lose the offererAsBuyerProtocolMap
    // Also we don't support yet offline messaging (mail box)
    public void fiatPaymentStarted(String tradeId) {
        if (offererAsBuyerProtocolMap.get(tradeId) != null) {
            offererAsBuyerProtocolMap.get(tradeId).handleBankTransferStartedUIEvent();
            persistPendingTrades();
        }
        else {
            featureNotImplementedWarning.set("Sorry, you cannot continue. You have restarted the application in the " +
                    "meantime. Interruption of the trade process is not supported yet. Will need more time to be " +
                    "implemented.");
        }
    }

    public void fiatPaymentReceived(String tradeId) {
        takerAsSellerProtocolMap.get(tradeId).handleFiatReceivedUIEvent();
    }

    public void requestIsOfferAvailable(Offer offer) {
        if (!requestIsOfferAvailableProtocolMap.containsKey(offer.getId())) {
            RequestIsOfferAvailableProtocol protocol = new RequestIsOfferAvailableProtocol(offer, tradeMessageService);
            requestIsOfferAvailableProtocolMap.put(offer.getId(), protocol);
            protocol.start();
        }
        else {
            log.warn("requestIsOfferAvailable already called for offer with ID:" + offer.getId());
        }
    }

    // When closing take offer view, we are not interested in the requestIsOfferAvailable result anymore, so remove from the map
    public void stopRequestIsOfferAvailableRequest(Offer offer) {
        requestIsOfferAvailableProtocolMap.remove(offer.getId());
    }

    public void onOfferRemovedFromRemoteOfferBook(Offer offer) {
        requestIsOfferAvailableProtocolMap.remove(offer.getId());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Process new tradeMessages
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Routes the incoming messages to the responsible protocol
    private void handleNewMessage(Message message, Peer sender) {
        log.trace("handleNewMessage: message = " + message.getClass().getSimpleName());
        log.trace("handleNewMessage: sender = " + sender);

        // TODO remove
        if (message instanceof OfferMessage) {
            OfferMessage offerMessage = (OfferMessage) message;
            // Before starting any take offer activity we check if the offer is still available.
            if (offerMessage instanceof RequestIsOfferAvailableMessage) {
                // That message arrives at the offerer and he returns if the offer is still available (if there is no trade already created with that offerId).
                String offerId = offerMessage.getOfferId();
                checkNotNull(offerId);
                boolean isOfferOpen = getTrade(offerId) == null;
                // no handling of results or faults needed
                IsOfferAvailableResponse.run(sender, tradeMessageService, offerId, isOfferOpen);
            }
            else if (offerMessage instanceof IsOfferAvailableResponseMessage) {
                // That message arrives at the taker in response to a previous requestIsOfferAvailable call.
                // It might be that the offer got removed form the offer book, so lets check if its still there.
                if (requestIsOfferAvailableProtocolMap.containsKey(offerMessage.getOfferId())) {
                    RequestIsOfferAvailableProtocol protocol = requestIsOfferAvailableProtocolMap.get(offerMessage.getOfferId());
                    protocol.handleIsOfferAvailableResponseMessage((IsOfferAvailableResponseMessage) offerMessage);
                    requestIsOfferAvailableProtocolMap.remove(offerMessage.getOfferId());
                }
                else {
                    log.info("Offer might have been removed in the meantime. No protocol found for offer with ID:" + offerMessage.getOfferId());
                }
            }
            else {
                log.error("Incoming offerMessage not supported. " + offerMessage);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setFeatureNotImplementedWarning(String featureNotImplementedWarning) {
        this.featureNotImplementedWarning.set(featureNotImplementedWarning);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObservableMap<String, OpenOffer> getOpenOffers() {
        return openOffers;
    }

    public ObservableMap<String, Trade> getPendingTrades() {
        return pendingTrades;
    }

    public ObservableMap<String, Trade> getClosedTrades() {
        return closedTrades;
    }

    public Trade getCurrentPendingTrade() {
        return currentPendingTrade;
    }

    public String getFeatureNotImplementedWarning() {
        return featureNotImplementedWarning.get();
    }

    public StringProperty featureNotImplementedWarningProperty() {
        return featureNotImplementedWarning;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void persistOpenOffers() {
        persistence.write(this, "openOffers", (Map<String, OpenOffer>) new HashMap<>(openOffers));
    }

    private void persistPendingTrades() {
        persistence.write(this, "pendingTrades", (Map<String, Trade>) new HashMap<>(pendingTrades));
    }

    private void persistClosedTrades() {
        persistence.write(this, "closedTrades", (Map<String, Trade>) new HashMap<>(closedTrades));
    }


    @Nullable
    public Trade getTrade(String tradeId) {
        if (pendingTrades.containsKey(tradeId)) {
            return pendingTrades.get(tradeId);
        }
        else {
            return null;
        }
    }


}
