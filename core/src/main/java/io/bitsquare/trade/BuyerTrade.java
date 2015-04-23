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

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Peer;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.protocol.trade.BuyerProtocol;

import org.bitcoinj.core.Coin;

import java.io.Serializable;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BuyerTrade extends Trade implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    transient private static final Logger log = LoggerFactory.getLogger(BuyerAsOffererTrade.class);

    public BuyerTrade(Offer offer, Coin tradeAmount, Peer tradingPeer, Storage<? extends TradableList> storage) {
        super(offer, tradeAmount, tradingPeer, storage);
        log.trace("Created by constructor");
    }

    public BuyerTrade(Offer offer, Storage<? extends TradableList> storage) {
        super(offer, storage);
        log.trace("Created by constructor");
    }

    @Override
    protected void initStates() {
        if (tradeState == null)
            tradeState = TradeState.BuyerState.PREPARATION;
        initStateProperties();
    }

    public void onFiatPaymentStarted() {
        assert tradeProtocol instanceof BuyerProtocol;
        ((BuyerProtocol) tradeProtocol).onFiatPaymentStarted();
    }


    @Override
    public boolean isFailedState() {
        return tradeState == TradeState.BuyerState.FAILED;
    }

    @Override
    public void setFailedState() {
        TradeState tradeState = TradeState.BuyerState.FAILED;
        // We store the phase of the last state into the failed state
        tradeState.setPhase(tradeState.getPhase());
        setTradeState(tradeState);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setTradeState(TradeState tradeState) {
        super.setTradeState(tradeState);

        switch ((TradeState.BuyerState) tradeState) {
            case PREPARATION:
                break;

            case DEPOSIT_PUBLISHED:
                takeOfferDate = new Date();

                if (this instanceof OffererTrade)
                    openOfferManager.closeOpenOffer(getOffer());
                break;
            case DEPOSIT_PUBLISHED_MSG_SENT:
                break;
            case DEPOSIT_CONFIRMED:
                break;

            case FIAT_PAYMENT_STARTED:
                break;
            case FIAT_PAYMENT_STARTED_MSG_SENT:
                break;

            case FIAT_PAYMENT_RECEIPT_MSG_RECEIVED:
                break;

            case PAYOUT_TX_COMMITTED:
                break;
            case PAYOUT_TX_SENT:
                break;

            case PAYOUT_BROAD_CASTED:
                break;

            case WITHDRAW_COMPLETED:
                disposeProtocol();
                break;

            case FAILED:
                disposeProtocol();
                break;

            default:
                log.error("Unhandled state " + tradeState);
                break;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void handleConfidenceResult() {
        if (((TradeState.BuyerState) tradeState).ordinal() < TradeState.BuyerState.DEPOSIT_CONFIRMED.ordinal())
            setTradeState(TradeState.BuyerState.DEPOSIT_CONFIRMED);
    }
}
