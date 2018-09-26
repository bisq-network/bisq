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

package bisq.core.dao.state;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.BaseCurrencyNetwork;
import bisq.core.dao.DaoOptionKeys;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;


/**
 * Encapsulate the genesis txId and height.
 * As we don't persist those data we don't want to have it in the BsqState directly and moved it to a separate class.
 * Using a static final field in BsqState would not work well as we want to support that the data can be overwritten by
 * program arguments for development testing and therefore it is set in the constructor via Guice.
 */
@Slf4j
public class GenesisTxInfo {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static final Coin GENESIS_TOTAL_SUPPLY = Coin.parseCoin("2.5");

    private static final String MAINNET_GENESIS_TX_ID = "81855816eca165f17f0668898faa8724a105196e90ffc4993f4cac980176674e";
    private static final int MAINNET_GENESIS_BLOCK_HEIGHT = 524717; // 2018-05-27

    private static final String TESTNET_GENESIS_TX_ID = "7085539068b4fc27dfc6c39b0feae2adc7fe20f925e79ca0ba064725fe6c9991";
    private static final int TESTNET_GENESIS_BLOCK_HEIGHT = 1414332; // 2018-09-25


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We cannot use a static final as we want to set the txId also via program argument for development and then it
    // is getting passed via Guice in the constructor.
    @Getter
    private final String genesisTxId;
    @Getter
    private final int genesisBlockHeight;

    // mainnet
    // this tx has a lot of outputs
    // https://blockchain.info/de/tx/ee921650ab3f978881b8fe291e0c025e0da2b7dc684003d7a03d9649dfee2e15
    // BLOCK_HEIGHT 411779
    // 411812 has 693 recursions
    // block 376078 has 2843 recursions and caused once a StackOverflowError, a second run worked. Took 1,2 sec.


    // BTC MAIN NET
    // new: --genesisBlockHeight=524717 --genesisTxId=81855816eca165f17f0668898faa8724a105196e90ffc4993f4cac980176674e
    //  private static final String DEFAULT_GENESIS_TX_ID = "e5c8313c4144d219b5f6b2dacf1d36f2d43a9039bb2fcd1bd57f8352a9c9809a";
    // private static final int DEFAULT_GENESIS_BLOCK_HEIGHT = 477865; // 2017-07-28


    // private static final String DEFAULT_GENESIS_TX_ID = "--";
    // private static final int DEFAULT_GENESIS_BLOCK_HEIGHT = 499000; // recursive test 137298, 499000 dec 2017

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public GenesisTxInfo(@Nullable @Named(DaoOptionKeys.GENESIS_TX_ID) String genesisTxId,
                         @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        BaseCurrencyNetwork baseCurrencyNetwork = BisqEnvironment.getBaseCurrencyNetwork();
        boolean isMainnet = baseCurrencyNetwork.isMainnet();
        boolean isTestnet = baseCurrencyNetwork.isTestnet();
        if (genesisTxId != null && !genesisTxId.isEmpty()) {
            this.genesisTxId = genesisTxId;
        } else if (isMainnet) {
            this.genesisTxId = MAINNET_GENESIS_TX_ID;
        } else if (isTestnet) {
            this.genesisTxId = TESTNET_GENESIS_TX_ID;
        } else {
            this.genesisTxId = "genesisTxId is undefined";
        }

        if (genesisBlockHeight != 0) {
            this.genesisBlockHeight = genesisBlockHeight;
        } else if (isMainnet) {
            this.genesisBlockHeight = MAINNET_GENESIS_BLOCK_HEIGHT;
        } else if (isTestnet) {
            this.genesisBlockHeight = TESTNET_GENESIS_BLOCK_HEIGHT;
        } else {
            this.genesisBlockHeight = 0;
        }
    }
}
