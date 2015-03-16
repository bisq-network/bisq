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

package io.bitsquare.trade.tomp2p;

import io.bitsquare.network.tomp2p.TomP2PNode;
import io.bitsquare.trade.TradeMessageModule;
import io.bitsquare.trade.TradeMessageService;

import com.google.inject.Provider;
import com.google.inject.Singleton;

import javax.inject.Inject;

import javafx.application.Platform;

import org.springframework.core.env.Environment;

public class TomP2PTradeMessageModule extends TradeMessageModule {

    public TomP2PTradeMessageModule(Environment env) {
        super(env);
    }

    @Override
    protected void doConfigure() {
        bind(TradeMessageService.class).toProvider(TomP2PTradeMessageServiceProvider.class).in(Singleton.class);
    }
}

class TomP2PTradeMessageServiceProvider implements Provider<TradeMessageService> {
    private final TradeMessageService tradeMessageService;

    @Inject
    public TomP2PTradeMessageServiceProvider(TomP2PNode tomP2PNode) {
        tradeMessageService = new TomP2PTradeMessageService(tomP2PNode);
        tradeMessageService.setExecutor(Platform::runLater);
    }

    public TradeMessageService get() {
        return tradeMessageService;
    }
}