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

package io.bitsquare.dao;

import com.google.inject.Singleton;
import io.bitsquare.app.AppModule;
import io.bitsquare.dao.blockchain.RpcOptionKeys;
import io.bitsquare.dao.blockchain.SquBlockchainManager;
import io.bitsquare.dao.blockchain.SquBlockchainRpcService;
import io.bitsquare.dao.blockchain.SquBlockchainService;
import io.bitsquare.dao.compensation.CompensationRequestManager;
import io.bitsquare.dao.vote.VotingDefaultValues;
import io.bitsquare.dao.vote.VotingManager;
import io.bitsquare.dao.vote.VotingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import static com.google.inject.name.Names.named;

public class DaoModule extends AppModule {
    private static final Logger log = LoggerFactory.getLogger(DaoModule.class);

    public DaoModule(Environment env) {
        super(env);
    }

    @Override
    protected void configure() {
        bind(DaoManager.class).in(Singleton.class);
        bind(SquBlockchainManager.class).in(Singleton.class);
        bind(SquBlockchainService.class).to(SquBlockchainRpcService.class).in(Singleton.class);
        bind(DaoPeriodService.class).in(Singleton.class);
        bind(VotingService.class).in(Singleton.class);
        
        bind(CompensationRequestManager.class).in(Singleton.class);
        bind(VotingManager.class).in(Singleton.class);
        bind(DaoService.class).in(Singleton.class);
        bind(VotingDefaultValues.class).in(Singleton.class);

        bindConstant().annotatedWith(named(RpcOptionKeys.RPC_USER)).to(env.getRequiredProperty(RpcOptionKeys.RPC_USER));
        bindConstant().annotatedWith(named(RpcOptionKeys.RPC_PASSWORD)).to(env.getRequiredProperty(RpcOptionKeys.RPC_PASSWORD));
        bindConstant().annotatedWith(named(RpcOptionKeys.RPC_PORT)).to(env.getRequiredProperty(RpcOptionKeys.RPC_PORT));
        bindConstant().annotatedWith(named(RpcOptionKeys.RPC_BLOCK_PORT)).to(env.getRequiredProperty(RpcOptionKeys.RPC_BLOCK_PORT));
        bindConstant().annotatedWith(named(RpcOptionKeys.RPC_WALLET_PORT)).to(env.getRequiredProperty(RpcOptionKeys.RPC_WALLET_PORT));
    }
}

