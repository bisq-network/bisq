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

package io.bitsquare.network.tomp2p;

import io.bitsquare.network.BootstrapState;
import io.bitsquare.network.NetworkService;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import javafx.application.Platform;

import net.tomp2p.dht.PeerDHT;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Subscriber;


/**
 * That service delivers direct messaging and DHT functionality from the TomP2P library
 * It is the translating domain specific functionality to the messaging layer.
 * The TomP2P library codebase shall not be used outside that service.
 * That way we limit the dependency of the TomP2P library only to that class (and it's sub components).
 * <p/>
 */
public class TomP2PService implements NetworkService {
    private static final Logger log = LoggerFactory.getLogger(TomP2PService.class);

    private final Subscriber<BootstrapState> subscriber;

    protected Executor executor = Platform::runLater;
    protected PeerDHT peerDHT;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TomP2PService(TomP2PNode tomP2PNode) {
        Observable<BootstrapState> bootstrapStateAsObservable = tomP2PNode.getBootstrapStateAsObservable();
        subscriber = new Subscriber<BootstrapState>() {
            @Override
            public void onCompleted() {
                executor.execute(() -> {
                    peerDHT = tomP2PNode.getPeerDHT();
                    subscriber.unsubscribe();
                    bootstrapCompleted();
                });
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onNext(BootstrapState bootstrapState) {
            }
        };
        bootstrapStateAsObservable.subscribe(subscriber);
    }

    @Override
    public void bootstrapCompleted() {

    }

    @Override
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void shutDown() {
    }

}
