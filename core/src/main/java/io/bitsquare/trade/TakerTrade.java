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

import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.Peer;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.protocol.trade.ProcessModel;
import io.bitsquare.trade.protocol.trade.taker.models.TakerProcessModel;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import java.io.IOException;
import java.io.Serializable;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TakerTrade extends Trade implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = 1L;

    transient private static final Logger log = LoggerFactory.getLogger(TakerTrade.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface LifeCycleState extends Trade.LifeCycleState {
    }

    public interface ProcessState extends Trade.ProcessState {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Immutable
    protected final Coin tradeAmount;
    protected final Peer tradingPeer;
    transient protected ObjectProperty<Coin> tradeAmountProperty;
    transient protected ObjectProperty<Fiat> tradeVolumeProperty;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor, initialization
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected TakerTrade(Offer offer, Coin tradeAmount, Peer tradingPeer,
                         Storage<? extends TradeList> storage) {
        super(offer, storage);
        log.trace("Created by constructor");

        this.tradeAmount = tradeAmount;
        this.tradingPeer = tradingPeer;

        initStates();
        initStateProperties();
        initAmountProperty();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        log.trace("Created from serialized form.");

        initStateProperties();
        initAmountProperty();
    }

    @Override
    public ProcessModel createProcessModel() {
        return new TakerProcessModel();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter only
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Coin getTradeAmount() {
        return tradeAmount;
    }

    @Override
    public Fiat getTradeVolume() {
        return offer.getVolumeByAmount(tradeAmount);
    }

    public Peer getTradingPeer() {
        return tradingPeer;
    }

    public TakerProcessModel getProcessModel() {
        return (TakerProcessModel) processModel;
    }

    public ReadOnlyObjectProperty<Coin> tradeAmountProperty() {
        return tradeAmountProperty;
    }

    public ReadOnlyObjectProperty<Fiat> tradeVolumeProperty() {
        return tradeVolumeProperty;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void initAmountProperty() {
        tradeAmountProperty = new SimpleObjectProperty<>(tradeAmount);
        tradeVolumeProperty = new SimpleObjectProperty<>(getTradeVolume());
    }

    @Override
    public String toString() {
        return "TakerTrade{" +
                "tradeAmount=" + tradeAmount +
                ", tradingPeer=" + tradingPeer +
                super.toString();
    }
}
