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

package io.bitsquare.msg;

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.msg.listeners.AddOfferListener;
import io.bitsquare.msg.listeners.ArbitratorListener;
import io.bitsquare.msg.listeners.BootstrapListener;
import io.bitsquare.msg.listeners.GetPeerAddressListener;
import io.bitsquare.msg.listeners.IncomingTradeMessageListener;
import io.bitsquare.msg.listeners.OfferBookListener;
import io.bitsquare.msg.listeners.OutgoingTradeMessageListener;
import io.bitsquare.network.Peer;
import io.bitsquare.trade.Offer;
import io.bitsquare.trade.protocol.trade.TradeMessage;

import java.security.PublicKey;

import java.util.Locale;

import javafx.beans.property.LongProperty;

public interface MessageFacade extends MessageBroker {

    void sendTradeMessage(Peer peer, TradeMessage tradeMessage, OutgoingTradeMessageListener listener);

    void shutDown();

    void addArbitrator(Arbitrator arbitrator);

    void addIncomingTradeMessageListener(IncomingTradeMessageListener listener);

    void removeIncomingTradeMessageListener(IncomingTradeMessageListener listener);

    void addOffer(Offer offer, AddOfferListener addOfferListener);

    void addArbitratorListener(ArbitratorListener listener);

    void getArbitrators(Locale defaultLanguageLocale);

    LongProperty invalidationTimestampProperty();

    void addOfferBookListener(OfferBookListener offerBookListener);

    void requestInvalidationTimeStampFromDHT(String fiatCode);

    void getOffers(String fiatCode);

    void removeOffer(Offer offer);

    void init(int clientPort, BootstrapListener bootstrapListener);

    void getPeerAddress(PublicKey messagePublicKey, GetPeerAddressListener getPeerAddressListener);

    void removeOfferBookListener(OfferBookListener offerBookListener);
}
