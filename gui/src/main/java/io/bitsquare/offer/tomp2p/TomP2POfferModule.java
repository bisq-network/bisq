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

package io.bitsquare.offer.tomp2p;

import io.bitsquare.msg.tomp2p.TomP2PNode;
import io.bitsquare.offer.OfferModule;
import io.bitsquare.offer.RemoteOfferBook;

import com.google.inject.Provider;

import javax.inject.Inject;

import javafx.application.Platform;

import org.springframework.core.env.Environment;

public class TomP2POfferModule extends OfferModule {

    public TomP2POfferModule(Environment env) {
        super(env);
    }

    @Override
    protected void configure() {
        bind(RemoteOfferBook.class).toProvider(RemoteOfferBookProvider.class).asEagerSingleton();
    }
}

class RemoteOfferBookProvider implements Provider<RemoteOfferBook> {
    private final TomP2POfferBook remoteOfferBook;

    @Inject
    public RemoteOfferBookProvider(TomP2PNode p2pNode) {
        remoteOfferBook = new TomP2POfferBook(p2pNode);
        remoteOfferBook.setExecutor(Platform::runLater);
    }

    public RemoteOfferBook get() {
        return remoteOfferBook;
    }
}