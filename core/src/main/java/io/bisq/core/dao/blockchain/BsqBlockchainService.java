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

package io.bisq.core.dao.blockchain;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.domain.RawTransaction;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.handlers.ResultHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
abstract public class BsqBlockchainService {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqBlockchainService() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Non blocking methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract void setup(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler);

    abstract void requestChainHeadHeight(Consumer<Integer> resultHandler, Consumer<Throwable> errorHandler);

    abstract void parseBlocks(int startBlockHeight,
                              int chainHeadHeight,
                              int genesisBlockHeight,
                              String genesisTxId,
                              TxOutputMap txOutputMap,
                              ResultHandler resultHandler,
                              Consumer<Throwable> errorHandler);

    abstract void addBlockHandler(Consumer<BsqBlock> onNewBlockHandler);

    abstract void parseBlock(BsqBlock block,
                             int genesisBlockHeight,
                             String genesisTxId,
                             TxOutputMap txOutputMap,
                             ResultHandler resultHandler,
                             Consumer<Throwable> errorHandler);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Blocking methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    abstract int requestChainHeadHeight() throws BitcoindException, CommunicationException;

    @VisibleForTesting
    abstract com.neemre.btcdcli4j.core.domain.Block requestBlock(int i) throws BitcoindException, CommunicationException;

    @VisibleForTesting
    abstract Tx requestTransaction(String txId, int blockHeight) throws BsqBlockchainException;

    @VisibleForTesting
    abstract RawTransaction requestRawTransaction(String txId) throws BitcoindException, CommunicationException;
}
