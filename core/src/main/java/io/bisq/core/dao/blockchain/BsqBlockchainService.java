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

import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.domain.Block;
import com.neemre.btcdcli4j.core.domain.RawTransaction;
import io.bisq.core.dao.blockchain.exceptions.BsqBlockchainException;
import io.bisq.core.dao.blockchain.vo.Tx;

import java.util.function.Consumer;

// Access calls to blockchain data provider
public interface BsqBlockchainService {

    void setup() throws BsqBlockchainException;

    void registerBlockHandler(Consumer<Block> blockHandler);

    int requestChainHeadHeight() throws BitcoindException, CommunicationException;

    Block requestBlock(int i) throws BitcoindException, CommunicationException;

    Tx requestTransaction(String txId, int blockHeight) throws BsqBlockchainException;

    RawTransaction requestRawTransaction(String txId) throws BitcoindException, CommunicationException;
}
