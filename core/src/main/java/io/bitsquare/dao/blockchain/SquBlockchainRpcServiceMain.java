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

package io.bitsquare.dao.blockchain;

import ch.qos.logback.classic.Level;
import io.bitsquare.messages.app.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class SquBlockchainRpcServiceMain {
    private static final Logger log = LoggerFactory.getLogger(SquBlockchainRpcServiceMain.class);

    public static void main(String[] args) throws SquBlockchainException {
        Log.setup(System.getProperty("user.home") + File.separator + "BlockchainRpcServiceMain");
        Log.setLevel(Level.WARN);

        // regtest uses port 18332, mainnet 8332
        SquBlockchainRpcService blockchainRpcService = new SquBlockchainRpcService(args[0], args[1], args[2], args[3], args[4]);
        SquBlockchainManager squBlockchainManager = new SquBlockchainManager(blockchainRpcService);
        squBlockchainManager.onAllServicesInitialized();
    }
}
