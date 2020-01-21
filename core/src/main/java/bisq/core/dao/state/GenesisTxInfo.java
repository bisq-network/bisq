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

import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


/**
 * Encapsulate the genesis txId and height.
 * As we don't persist those data we don't want to have it in the DaoState directly and moved it to a separate class.
 * Using a static final field in DaoState would not work well as we want to support that the data can be overwritten by
 * program arguments for development testing and therefore it is set in the constructor via Guice.
 */
@SuppressWarnings("SpellCheckingInspection")
@Slf4j
public class GenesisTxInfo {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static final String MAINNET_GENESIS_TX_ID = "4b5417ec5ab6112bedf539c3b4f5a806ed539542d8b717e1c4470aa3180edce5";
    private static final int MAINNET_GENESIS_BLOCK_HEIGHT = 571747; // 2019-04-15
    private static final Coin MAINNET_GENESIS_TOTAL_SUPPLY = Coin.parseCoin("3.65748");

    private static final String TESTNET_GENESIS_TX_ID = "f35b62930b16a680ba6bc8ba8fecc4f1db65c5635b5a4b4b0445544649acf4f6";
    private static final int TESTNET_GENESIS_BLOCK_HEIGHT = 1564395; // 2019-06-21
    private static final Coin TESTNET_GENESIS_TOTAL_SUPPLY = Coin.parseCoin("2.5"); // 2.5M BSQ / 2.50000000 BTC

    private static final String DAO_TESTNET_GENESIS_TX_ID = "cb316a186b9e88d1b8e1ce8dc79cc6a2080cc7bbc6df94f2be325d8253417af1";
    private static final int DAO_TESTNET_GENESIS_BLOCK_HEIGHT = 104; // 2019-02-19
    private static final Coin DAO_TESTNET_GENESIS_TOTAL_SUPPLY = Coin.parseCoin("2.5"); // 2.5M BSQ / 2.50000000 BTC

    private static final String DAO_BETANET_GENESIS_TX_ID = "0bd66d8ff26476b55dfaf2a5db0c659a5d8635566488244df25606db63a08bd9";
    private static final int DAO_BETANET_GENESIS_BLOCK_HEIGHT = 567405; // 2019-03-16
    private static final Coin DAO_BETANET_GENESIS_TOTAL_SUPPLY = Coin.parseCoin("0.49998644"); // 499 986.44 BSQ / 0.49998644 BTC

    private static final String DAO_REGTEST_GENESIS_TX_ID = "d594ad0c5de53e261b5784e5eb2acec8b807c45b74450401f488d36b8acf2e14";
    private static final int DAO_REGTEST_GENESIS_BLOCK_HEIGHT = 104; // 2019-03-26
    private static final Coin DAO_REGTEST_GENESIS_TOTAL_SUPPLY = Coin.parseCoin("2.5"); // 2.5M BSQ / 2.50000000 BTC

    private static final String REGTEST_GENESIS_TX_ID = "30af0050040befd8af25068cc697e418e09c2d8ebd8d411d2240591b9ec203cf";
    private static final int REGTEST_GENESIS_BLOCK_HEIGHT = 111;
    private static final Coin REGTEST_GENESIS_TOTAL_SUPPLY = Coin.parseCoin("2.5"); // 2.5M BSQ / 2.50000000 BTC


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We cannot use a static final as we want to set the txId also via program argument for development and then it
    // is getting passed via Guice in the constructor.
    @Getter
    private final String genesisTxId;
    @Getter
    private final int genesisBlockHeight;
    @Getter
    private final long genesisTotalSupply;

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
    public GenesisTxInfo(@Named(Config.GENESIS_TX_ID) String genesisTxId,
                         @Named(Config.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight,
                         @Named(Config.GENESIS_TOTAL_SUPPLY) long genesisTotalSupply) {
        BaseCurrencyNetwork baseCurrencyNetwork = Config.baseCurrencyNetwork();
        boolean isMainnet = baseCurrencyNetwork.isMainnet();
        boolean isTestnet = baseCurrencyNetwork.isTestnet();
        boolean isDaoTestNet = baseCurrencyNetwork.isDaoTestNet();
        boolean isDaoBetaNet = baseCurrencyNetwork.isDaoBetaNet();
        boolean isDaoRegTest = baseCurrencyNetwork.isDaoRegTest();
        boolean isRegtest = baseCurrencyNetwork.isRegtest();
        if (!genesisTxId.isEmpty()) {
            this.genesisTxId = genesisTxId;
        } else if (isMainnet) {
            this.genesisTxId = MAINNET_GENESIS_TX_ID;
        } else if (isTestnet) {
            this.genesisTxId = TESTNET_GENESIS_TX_ID;
        } else if (isDaoTestNet) {
            this.genesisTxId = DAO_TESTNET_GENESIS_TX_ID;
        } else if (isDaoBetaNet) {
            this.genesisTxId = DAO_BETANET_GENESIS_TX_ID;
        } else if (isDaoRegTest) {
            this.genesisTxId = DAO_REGTEST_GENESIS_TX_ID;
        } else if (isRegtest) {
            this.genesisTxId = REGTEST_GENESIS_TX_ID;
        } else {
            this.genesisTxId = "genesisTxId is undefined";
        }

        if (genesisBlockHeight > -1) {
            this.genesisBlockHeight = genesisBlockHeight;
        } else if (isMainnet) {
            this.genesisBlockHeight = MAINNET_GENESIS_BLOCK_HEIGHT;
        } else if (isTestnet) {
            this.genesisBlockHeight = TESTNET_GENESIS_BLOCK_HEIGHT;
        } else if (isDaoTestNet) {
            this.genesisBlockHeight = DAO_TESTNET_GENESIS_BLOCK_HEIGHT;
        } else if (isDaoBetaNet) {
            this.genesisBlockHeight = DAO_BETANET_GENESIS_BLOCK_HEIGHT;
        } else if (isDaoRegTest) {
            this.genesisBlockHeight = DAO_REGTEST_GENESIS_BLOCK_HEIGHT;
        } else if (isRegtest) {
            this.genesisBlockHeight = REGTEST_GENESIS_BLOCK_HEIGHT;
        } else {
            this.genesisBlockHeight = 0;
        }

        if (genesisTotalSupply > -1) {
            this.genesisTotalSupply = genesisTotalSupply;
        } else if (isMainnet) {
            this.genesisTotalSupply = MAINNET_GENESIS_TOTAL_SUPPLY.value;
        } else if (isTestnet) {
            this.genesisTotalSupply = TESTNET_GENESIS_TOTAL_SUPPLY.value;
        } else if (isDaoTestNet) {
            this.genesisTotalSupply = DAO_TESTNET_GENESIS_TOTAL_SUPPLY.value;
        } else if (isDaoBetaNet) {
            this.genesisTotalSupply = DAO_BETANET_GENESIS_TOTAL_SUPPLY.value;
        } else if (isDaoRegTest) {
            this.genesisTotalSupply = DAO_REGTEST_GENESIS_TOTAL_SUPPLY.value;
        } else if (isRegtest) {
            this.genesisTotalSupply = REGTEST_GENESIS_TOTAL_SUPPLY.value;
        } else {
            this.genesisTotalSupply = 0;
        }
    }
}
