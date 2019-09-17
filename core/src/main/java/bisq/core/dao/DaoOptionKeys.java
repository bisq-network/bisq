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

package bisq.core.dao;

/**
 * Provides program argument options used in the DAO domain.
 */
public class DaoOptionKeys {
    public static final String RPC_USER = "rpcUser";
    public static final String RPC_PASSWORD = "rpcPassword";
    public static final String RPC_PORT = "rpcPort";
    public static final String RPC_BLOCK_NOTIFICATION_PORT = "rpcBlockNotificationPort";
    public static final String RPC_BLOCK_NOTIFICATION_HOST = "rpcBlockNotificationHost";
    public static final String RPC_HOST = "rpcHost";

    public static final String DUMP_BLOCKCHAIN_DATA = "dumpBlockchainData";
    public static final String FULL_DAO_NODE = "fullDaoNode";
    public static final String GENESIS_TX_ID = "genesisTxId";
    public static final String GENESIS_BLOCK_HEIGHT = "genesisBlockHeight";
    public static final String GENESIS_TOTAL_SUPPLY = "genesisTotalSupply";
    public static final String DAO_ACTIVATED = "daoActivated";
}
