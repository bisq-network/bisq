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

package bisq.core.network.p2p.seed;

import bisq.network.p2p.NodeAddress;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

class DefaultSeedNodeAddresses {
    // Addresses are used if the last digit of their port match the network id:
    // - mainnet use port ends in 0
    // - testnet use port ends in 1
    // - regtest use port ends in 2
    public static final Set<NodeAddress> DEFAULT_LOCALHOST_SEED_NODE_ADDRESSES = ImmutableSet.of(
            // BTC
            // mainnet
            new NodeAddress("localhost:2000"),
            new NodeAddress("localhost:3000"),
            new NodeAddress("localhost:4000"),

            // testnet
            new NodeAddress("localhost:2001"),
            new NodeAddress("localhost:3001"),
            new NodeAddress("localhost:4001"),

            // regtest
            new NodeAddress("localhost:2002"),
            new NodeAddress("localhost:3002")
            /*    new NodeAddress("localhost:4002"),*/
    );

    // Addresses are used if their port match the network id:
    // - mainnet uses port 8000
    // - testnet uses port 8001
    // - regtest uses port 8002
    public static final Set<NodeAddress> DEFAULT_TOR_SEED_NODE_ADDRESSES = ImmutableSet.of(
            // BTC mainnet
            new NodeAddress("5quyxpxheyvzmb2d.onion:8000"), // @miker
            new NodeAddress("s67qglwhkgkyvr74.onion:8000"), // @emzy
            new NodeAddress("ef5qnzx6znifo3df.onion:8000"), // @manfredkarrer
            new NodeAddress("jhgcy2won7xnslrb.onion:8000"), // @manfredkarrer
            new NodeAddress("3f3cu2yw7u457ztq.onion:8000"), // @manfredkarrer
            new NodeAddress("723ljisnynbtdohi.onion:8000"), // @manfredkarrer
            new NodeAddress("rm7b56wbrcczpjvl.onion:8000"), // @manfredkarrer
            new NodeAddress("fl3mmribyxgrv63c.onion:8000"), // @manfredkarrer

            // local dev
            // new NodeAddress("joehwtpe7ijnz4df.onion:8000"),
            // new NodeAddress("uqxi3zrpobhtoes6.onion:8000"),

            // BTC testnet
            // new NodeAddress("vjkh4ykq7x5skdlt.onion:8001"), // local dev test
            //new NodeAddress("fjr5w4eckjghqtnu.onion:8001"), // testnet seed 1
           /* new NodeAddress("74w2sttlo4qk6go3.onion:8001"), // testnet seed 2
            new NodeAddress("jmc5ajqvtnzqaggm.onion:8001"), // testnet seed 3
            new NodeAddress("3d56s6acbi3vk52v.onion:8001"), // testnet seed 4*/

            // BTC regtest
            // For development you need to change that to your local onion addresses
            // 1. Run a seed node with prog args: --bitcoinNetwork=regtest --nodePort=8002 --myAddress=rxdkppp3vicnbgqt:8002 --appName=bisq_seed_node_rxdkppp3vicnbgqt.onion_8002
            // 2. Find your local onion address in bisq_seed_node_rxdkppp3vicnbgqt.onion_8002/regtest/tor/hiddenservice/hostname
            // 3. Shut down the seed node
            // 4. Rename the directory with your local onion address
            // 5. Edit here your found onion address (new NodeAddress("YOUR_ONION.onion:8002")
            new NodeAddress("rxdkppp3vicnbgqt.onion:8002"),
            new NodeAddress("4ie52dse64kaarxw.onion:8002"),

            // DAO TESTNET (server side regtest dedicated for DAO testing)
            new NodeAddress("fjr5w4eckjghqtnu.onion:8003"), // testnet seed 1
            new NodeAddress("74w2sttlo4qk6go3.onion:8003"), // testnet seed 2
            new NodeAddress("jmc5ajqvtnzqaggm.onion:8003"), // testnet seed 3
            new NodeAddress("3d56s6acbi3vk52v.onion:8003") //  testnet seed 4

            // explorer
            // new NodeAddress("gtif46mfxirv533z.onion:8003")
    );

    private DefaultSeedNodeAddresses() {
    }
}
