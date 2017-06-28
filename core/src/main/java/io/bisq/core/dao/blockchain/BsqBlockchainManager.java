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

package io.bisq.core.dao.blockchain;

import com.google.inject.Inject;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.dao.DaoOptionKeys;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Named;

@Slf4j
public class BsqBlockchainManager {
    private final BsqNode bsqNode;

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqBlockchainManager(BsqLiteNode bsqLiteNode,
                                BsqFullNode bsqFullNode,
                                @Named(DaoOptionKeys.RPC_USER) String rpcUser) {

        bsqNode = rpcUser != null && !rpcUser.isEmpty() ? bsqFullNode : bsqLiteNode;
    }

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        bsqNode.onAllServicesInitialized(errorMessageHandler);
    }

    public void addBsqChainStateListener(BsqChainStateListener bsqChainStateListener) {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq())
            bsqNode.addBsqChainStateListener(bsqChainStateListener);
    }

    public boolean isParseBlockchainComplete() {
        return bsqNode.isParseBlockchainComplete();
    }

    public void removeBsqChainStateListener(BsqChainStateListener bsqChainStateListener) {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq())
            bsqNode.removeBsqChainStateListener(bsqChainStateListener);
    }
}
