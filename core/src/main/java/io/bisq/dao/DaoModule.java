/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.dao;

import com.google.inject.Singleton;
import io.bisq.app.AppModule;
import io.bisq.dao.blockchain.BsqBlockchainManager;
import io.bisq.dao.blockchain.BsqBlockchainRpcService;
import io.bisq.dao.blockchain.BsqBlockchainService;
import io.bisq.dao.compensation.CompensationRequestManager;
import io.bisq.dao.vote.VotingDefaultValues;
import io.bisq.dao.vote.VotingManager;
import io.bisq.dao.vote.VotingService;
import io.bisq.network_messages.dao.blockchain.RpcOptionKeys;
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
        bind(BsqBlockchainManager.class).in(Singleton.class);
        bind(BsqBlockchainService.class).to(BsqBlockchainRpcService.class).in(Singleton.class);
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

