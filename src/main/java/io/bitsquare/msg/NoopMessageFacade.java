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
import io.bitsquare.user.User;

import com.google.common.util.concurrent.FutureCallback;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import javafx.application.Platform;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;

import net.tomp2p.dht.PeerDHT;

public class NoopMessageFacade implements MessageFacade {

    private final List<IncomingTradeMessageListener> incomingTradeMessageListeners = new ArrayList<>();
    private final List<OfferBookListener> offerBookListeners = new ArrayList<>();
    private final LongProperty invalidationTimestamp = new SimpleLongProperty(0);
    private final P2PNode p2pNode;
    private final User user;

    @Inject
    public NoopMessageFacade(User user, P2PNode p2pNode) {
        this.user = user;
        this.p2pNode = p2pNode;
    }

    @Override
    public void sendTradeMessage(Peer peer, TradeMessage tradeMessage, OutgoingTradeMessageListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutDown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addArbitrator(Arbitrator arbitrator) {
        //throw new UnsupportedOperationException();
    }

    @Override
    public void addIncomingTradeMessageListener(IncomingTradeMessageListener listener) {
        incomingTradeMessageListeners.add(listener);
    }

    @Override
    public void removeIncomingTradeMessageListener(IncomingTradeMessageListener listener) {
        incomingTradeMessageListeners.remove(listener);
    }

    @Override
    public void addOffer(Offer offer, AddOfferListener addOfferListener) {
        //throw new UnsupportedOperationException();
    }

    @Override
    public void addArbitratorListener(ArbitratorListener listener) {
        throw new UnsupportedOperationException();

    }

    @Override
    public void getArbitrators(Locale defaultLanguageLocale) {
        throw new UnsupportedOperationException();

    }

    @Override
    public LongProperty invalidationTimestampProperty() {
        return invalidationTimestamp;
    }

    @Override
    public void addOfferBookListener(OfferBookListener offerBookListener) {
        offerBookListeners.add(offerBookListener);
    }

    @Override
    public void removeOfferBookListener(OfferBookListener offerBookListener) {
        offerBookListeners.remove(offerBookListener);
    }

    @Override
    public void requestInvalidationTimeStampFromDHT(String fiatCode) {
        //throw new UnsupportedOperationException();

    }

    @Override
    public void getOffers(String fiatCode) {
        //throw new UnsupportedOperationException();

    }

    @Override
    public void removeOffer(Offer offer) {
        //throw new UnsupportedOperationException();

    }

    @Override
    public void init(int clientPort, BootstrapListener bootstrapListener) {
        //System.out.println("DummyMessageFacade.init");
        //throw new UnsupportedOperationException();

        p2pNode.setMessageBroker(this);
        p2pNode.setKeyPair(user.getMessageKeyPair());

        p2pNode.start(clientPort, new FutureCallback<PeerDHT>() {
            @Override
            public void onSuccess(PeerDHT result) {
                Platform.runLater(bootstrapListener::onCompleted);
            }

            @Override
            public void onFailure(Throwable t) {
                Platform.runLater(() -> bootstrapListener.onFailed(t));
            }
        });
    }

    @Override
    public void getPeerAddress(PublicKey messagePublicKey, GetPeerAddressListener getPeerAddressListener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void handleMessage(Object message, Peer sender) {
        throw new UnsupportedOperationException();
    }
}
