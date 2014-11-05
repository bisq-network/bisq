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

package io.bitsquare.trade.protocol.createoffer;

import io.bitsquare.btc.WalletFacade;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.offer.Offer;
import io.bitsquare.offer.OfferRepository;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.trade.handlers.TransactionResultHandler;
import io.bitsquare.trade.protocol.createoffer.tasks.BroadCastOfferFeeTx;
import io.bitsquare.util.task.FaultHandler;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;

import java.io.Serializable;

import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for coordinating tasks involved in the create offer process.
 * It holds the model.state of the current process and support recovery if possible.
 */
//TODO recover policy, timer

@Immutable
public class CreateOfferCoordinator {
    public enum State {
        INITED,
        STARTED,
        VALIDATED,
        OFFER_FEE_TX_CREATED,
        OFFER_FEE_BROAD_CASTED,
        OFFER_PUBLISHED_TO_DHT
    }

    /**
     * The model is not immutable but only exposed to the CreateOfferCoordinator
     */
    static class Model implements Serializable {
        private static final long serialVersionUID = 3027720554200858916L;

        private final Persistence persistence;
        private State state;
        //TODO use tx id
        Transaction transaction;

        Model(Persistence persistence) {
            this.persistence = persistence;
        }

        public State getState() {
            return state;
        }

        public void setState(State state) {
            this.state = state;

            //TODO will have performance issues, but could be handled inside the persistence solution (queue up save
            // requests and exec. them on dedicated thread)
            persistence.write(this, "state", state);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(CreateOfferCoordinator.class);

    private final Offer offer;
    private final WalletFacade walletFacade;
    private final MessageFacade messageFacade;
    private final TransactionResultHandler resultHandler;
    private final FaultHandler faultHandler;
    private final Model model;
    private final OfferRepository offerRepository;

    public CreateOfferCoordinator(Persistence persistence, Offer offer, WalletFacade walletFacade,
                                  MessageFacade messageFacade, TransactionResultHandler resultHandler,
                                  FaultHandler faultHandler, OfferRepository offerRepository) {
        this(offer, walletFacade, messageFacade, resultHandler, faultHandler, new Model(persistence), offerRepository);
    }

    // for recovery from model
    public CreateOfferCoordinator(Offer offer, WalletFacade walletFacade, MessageFacade messageFacade,
                                  TransactionResultHandler resultHandler, FaultHandler faultHandler, Model model,
                                  OfferRepository offerRepository) {
        this.offer = offer;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
        this.resultHandler = resultHandler;
        this.faultHandler = faultHandler;
        this.model = model;
        this.offerRepository = offerRepository;

        model.setState(State.INITED);
    }

    public void start() {
        model.setState(State.STARTED);

        try {
            offer.validate();
            model.setState(State.VALIDATED);
        } catch (Exception ex) {
            faultHandler.handleFault("Offer validation failed", ex);
            return;
        }

        try {
            model.transaction = walletFacade.createOfferFeeTx(offer.getId());
            model.setState(State.OFFER_FEE_TX_CREATED);
            offer.setOfferFeePaymentTxID(model.transaction.getHashAsString());
        } catch (InsufficientMoneyException ex) {
            faultHandler.handleFault(
                    "Offer fee payment failed because there is insufficient money in the trade wallet", ex);
            return;
        } catch (Throwable ex) {
            faultHandler.handleFault("Offer fee payment failed because of an exception occurred", ex);
            return;
        }

        BroadCastOfferFeeTx.run(this::onOfferFeeTxBroadCasted, faultHandler, walletFacade, model.transaction);
    }

    private void onOfferFeeTxBroadCasted() {
        model.setState(State.OFFER_FEE_BROAD_CASTED);
        offerRepository.addOffer(offer, this::addOfferResultHandler, faultHandler);
    }

    private void addOfferResultHandler() {
        model.setState(State.OFFER_PUBLISHED_TO_DHT);
        resultHandler.onResult(model.transaction);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Recovery
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void recover() {
        switch (model.getState()) {
            case INITED:
            case STARTED:
            case VALIDATED:
            case OFFER_FEE_TX_CREATED:
                // we start over again, no critical and expensive work done yet
                start();
                break;
            case OFFER_FEE_BROAD_CASTED:
                // actually the only replay case here, tx publish was successful but storage to dht failed.
                // Republish the offer to DHT
                offerRepository.addOffer(offer, this::addOfferResultHandler, faultHandler);
                break;
            case OFFER_PUBLISHED_TO_DHT:
                // should be impossible
                log.warn("That case must not happen.");
                break;
            default:
                log.error("Illegal state passes. That must not happen");
                break;
        }
    }

}
