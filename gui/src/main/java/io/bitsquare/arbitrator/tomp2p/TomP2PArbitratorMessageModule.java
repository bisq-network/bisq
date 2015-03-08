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

package io.bitsquare.arbitrator.tomp2p;

import io.bitsquare.arbitrator.ArbitratorMessageModule;
import io.bitsquare.arbitrator.ArbitratorMessageService;
import io.bitsquare.network.tomp2p.TomP2PNode;

import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import javax.inject.Inject;

import javafx.application.Platform;

import org.springframework.core.env.Environment;

public class TomP2PArbitratorMessageModule extends ArbitratorMessageModule {

    public TomP2PArbitratorMessageModule(Environment env) {
        super(env);
    }

    @Override
    protected void doConfigure() {
        bind(ArbitratorMessageService.class).toProvider(ArbitratorMessageServiceProvider.class).in(Singleton.class);
    }

    @Override
    protected void doClose(Injector injector) {
        super.doClose(injector);
    }
}

class ArbitratorMessageServiceProvider implements Provider<ArbitratorMessageService> {
    private final ArbitratorMessageService arbitratorMessageService;

    @Inject
    public ArbitratorMessageServiceProvider(TomP2PNode tomP2PNode) {
        arbitratorMessageService = new TomP2PArbitratorMessageService(tomP2PNode);
        arbitratorMessageService.setExecutor(Platform::runLater);
    }

    public ArbitratorMessageService get() {
        return arbitratorMessageService;
    }
}