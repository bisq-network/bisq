/*
 * Copyright 2013 Google Inc.
 * Copyright 2015 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.bisq.gui.util.validation.params.btc;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.net.discovery.HttpDiscovery;

import java.net.URI;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the main production network on which people trade goods and services.
 *
 * We cannot use MainNetParams because that would be one of the other base currencies,
 * so we cloned the MainNetParams to BtcMainNetParamsForValidation
 *
 */
public class BtcMainNetParamsForValidation extends AbstractBitcoinNetParams {
    public static final int MAINNET_MAJORITY_WINDOW = 1000;
    public static final int MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED = 950;
    public static final int MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 750;

    public BtcMainNetParamsForValidation() {
        super();
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        maxTarget = Utils.decodeCompactBits(0x1d00ffffL);
        dumpedPrivateKeyHeader = 128;
        addressHeader = 0;
        p2shHeader = 5;
        acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
        port = 8333;
        packetMagic = 0xf9beb4d9L;
        bip32HeaderPub = 0x0488B21E; //The 4 byte header that serializes in base58 to "xpub".
        bip32HeaderPriv = 0x0488ADE4; //The 4 byte header that serializes in base58 to "xprv"

        majorityEnforceBlockUpgrade = MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = MAINNET_MAJORITY_WINDOW;

        genesisBlock.setDifficultyTarget(0x1d00ffffL);
        genesisBlock.setTime(1231006505L);
        genesisBlock.setNonce(2083236893);
        id = ID_MAINNET;
        subsidyDecreaseBlockCount = 210000;
        spendableCoinbaseDepth = 100;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"),
                genesisHash);

        // This contains (at a minimum) the blocks which are not BIP30 compliant. BIP30 changed how duplicate
        // transactions are handled. Duplicated transactions could occur in the case where a coinbase had the same
        // extraNonce and the same outputs but appeared at different heights, and greatly complicated re-org handling.
        // Having these here simplifies block connection logic considerably.
        checkpoints.put(91722, Sha256Hash.wrap("00000000000271a2dc26e7667f8419f2e15416dc6955e5a6c6cdf3f2574dd08e"));
        checkpoints.put(91812, Sha256Hash.wrap("00000000000af0aed4792b1acee3d966af36cf5def14935db8de83d6f9306f2f"));
        checkpoints.put(91842, Sha256Hash.wrap("00000000000a4d0a398161ffc163c503763b1f4360639393e0e4c8e300e0caec"));
        checkpoints.put(91880, Sha256Hash.wrap("00000000000743f190a18c5577a3c2d2a1f610ae9601ac046a38084ccb7cd721"));
        checkpoints.put(200000, Sha256Hash.wrap("000000000000034a7dedef4a161fa058a2d67a173a90155f3a2fe6fc132e0ebf"));

        dnsSeeds = new String[]{
                "seed.bitcoin.sipa.be",         // Pieter Wuille
                "dnsseed.bluematt.me",          // Matt Corallo
                "dnsseed.bitcoin.dashjr.org",   // Luke Dashjr
                "seed.bitcoinstats.com",        // Chris Decker
                "seed.bitnodes.io",             // Addy Yeow
                "bitseed.xf2.org",              // Jeff Garzik
                "seed.bitcoin.jonasschnelli.ch" // Jonas Schnelli
        };
        httpSeeds = new HttpDiscovery.Details[]{
                // Andreas Schildbach
                new HttpDiscovery.Details(
                        ECKey.fromPublicOnly(Utils.HEX.decode("0238746c59d46d5408bf8b1d0af5740fe1a6e1703fcb56b2953f0b965c740d256f")),
                        URI.create("http://httpseed.bitcoin.schildbach.de/peers")
                )
        };

        // note: These are in big-endian format, which is what the SeedPeers code expects.
        // These values should be kept up-to-date as much as possible.
        //
        // these values were created using this tool:
        //  https://github.com/dan-da/names2ips
        //
        // with this exact command:
        //  $ ./names2ips.php --hostnames=seed.bitcoin.sipa.be,dnsseed.bluematt.me,dnsseed.bitcoin.dashjr.org,seed.bitcoinstats.com,seed.bitnodes.io --format=code --ipformat=hex --endian=big

        // Updated Nov. 4th 2017
        addrSeeds = new int[]{
            // -- seed.bitcoin.sipa.be --
            0x254187d,  0x1af0735d, 0x20b72088, 0x30a1c321, 0x3515cb9f, 0x4539448a, 0x459caed5,
            0x4b0559d1, 0x5c88c9c1, 0x726fc523, 0x753b448a, 0x75b25c50, 0x7efc63b3, 0x8e79e849,
            0x914ff334, 0x93d60bc6, 0x9e1df618, 0xa7069f4b, 0xafbd5827, 0xb5a3ce62, 0xc635d054,
            0xc6fa4260, 0xc73aea63, 0xc7437558, 0xd78bbfd5,
// -- dnsseed.bluematt.me --
            0x1a536adb, 0x20ecc692, 0x21117c4c, 0x4060d85b, 0x4a9caed5, 0x513caca3, 0x5a636358,
            0x675ad655, 0x75c0d4ad, 0xa5e3e8ad, 0xa6531525, 0xac08ab4c, 0xb2d7af41, 0xb4008bb9,
            0xb5064945, 0xc9fad02f, 0xcb59a28b, 0xd54ba834, 0xe04d0055, 0xed5d5848, 0xfb0a825e,
// -- dnsseed.bitcoin.dashjr.org --
            0x858ba8c,  0xc201bb9,  0xdded4ad,  0x21fe312d, 0x330fc023, 0x4230b75f, 0x45667a7b,
            0x47370159, 0x64384e57, 0x67b1b14d, 0x6e2a0f33, 0x810c6e3b, 0x9f3a652e, 0xa0def550,
            0xab2d2d18, 0xb06a3850, 0xb7df01a9, 0xc8a8825e, 0xd3ace75c, 0xd5a0aa43, 0xdc0d599f,
            0xe1f00d47, 0xf1519f51, 0xfd83bd56,
// -- seed.bitcoinstats.com --
            0x2e1ff68,  0x424bd6b,  0x7571e53,  0x14d33174, 0x18aac780, 0x1c5cd812, 0x40614032,
            0x40de1c4d, 0x688fd812, 0x73c6ffad, 0x7dc6ffad, 0x7ebd2149, 0x8c08d153, 0x90b5a6d3,
            0x986fd462, 0xae1c82aa, 0xbeffdc4a, 0xc0a1764b, 0xc0c0b1d3, 0xdff8c768, 0xe0e6a6d3,
            0xe23b0f34, 0xe35f372f, 0xe9ff4748, 0xeed8b943,
// -- seed.bitnodes.io --
            0x4d3921f,  0xf480548,  0x1356b1d1, 0x1d25ddd8, 0x1d556056, 0x233d2567, 0x3058a2b2,
            0x3ee58c9e, 0x45ea0747, 0x4abd2088, 0x4d0d97d8, 0x5246b2b3, 0x5a0ae25b, 0x5a0ea9d9,
            0x5a55f450, 0x64610545, 0x6ed40d3e, 0x7a48cb4a, 0x98c5b35d, 0xaeb473bc, 0xdcefff86,
            0xe170d951, 0xe76280b2, 0xfa1f645e, 0xfb96466d,
        };
    }

    private static BtcMainNetParamsForValidation instance;

    public static synchronized BtcMainNetParamsForValidation get() {
        if (instance == null) {
            instance = new BtcMainNetParamsForValidation();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_MAINNET;
    }
}
