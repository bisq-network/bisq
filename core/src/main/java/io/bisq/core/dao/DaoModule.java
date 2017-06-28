/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao;

import com.google.inject.Singleton;
import io.bisq.common.app.AppModule;
import io.bisq.core.dao.blockchain.BsqBlockchainManager;
import io.bisq.core.dao.blockchain.BsqFullNode;
import io.bisq.core.dao.blockchain.BsqLiteNode;
import io.bisq.core.dao.blockchain.json.JsonChainStateExporter;
import io.bisq.core.dao.blockchain.parse.*;
import io.bisq.core.dao.compensation.CompensationRequestManager;
import io.bisq.core.dao.compensation.CompensationRequestModel;
import io.bisq.core.dao.vote.VotingDefaultValues;
import io.bisq.core.dao.vote.VotingManager;
import io.bisq.core.dao.vote.VotingService;
import org.springframework.core.env.Environment;

import static com.google.inject.name.Names.named;

public class DaoModule extends AppModule {

    public DaoModule(Environment environment) {
        super(environment);
    }

    @Override
    protected void configure() {
        bind(DaoManager.class).in(Singleton.class);

        bind(BsqBlockchainManager.class).in(Singleton.class);
        bind(BsqLiteNode.class).in(Singleton.class);
        bind(BsqFullNode.class).in(Singleton.class);
        bind(BsqChainState.class).in(Singleton.class);
        bind(BsqFullNodeExecutor.class).in(Singleton.class);
        bind(BsqLiteNodeExecutor.class).in(Singleton.class);
        bind(BsqParser.class).in(Singleton.class);
        bind(RpcService.class).in(Singleton.class);

        bind(OpReturnVerification.class).in(Singleton.class);
        bind(CompensationRequestVerification.class).in(Singleton.class);
        bind(VotingVerification.class).in(Singleton.class);
        bind(IssuanceVerification.class).in(Singleton.class);

        bind(JsonChainStateExporter.class).in(Singleton.class);
        bind(DaoPeriodService.class).in(Singleton.class);
        bind(VotingService.class).in(Singleton.class);

        bind(CompensationRequestManager.class).in(Singleton.class);
        bind(CompensationRequestModel.class).in(Singleton.class);
        bind(VotingManager.class).in(Singleton.class);
        bind(DaoService.class).in(Singleton.class);
        bind(VotingDefaultValues.class).in(Singleton.class);

        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_USER)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_USER));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_PASSWORD)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_PASSWORD));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_PORT)).to(environment.getRequiredProperty(DaoOptionKeys.RPC_PORT));
        bindConstant().annotatedWith(named(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT))
                .to(environment.getRequiredProperty(DaoOptionKeys.RPC_BLOCK_NOTIFICATION_PORT));
        bindConstant().annotatedWith(named(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA))
                .to(environment.getRequiredProperty(DaoOptionKeys.DUMP_BLOCKCHAIN_DATA));
        bindConstant().annotatedWith(named(DaoOptionKeys.FULL_DAO_NODE))
                .to(environment.getRequiredProperty(DaoOptionKeys.FULL_DAO_NODE));
    }
}

