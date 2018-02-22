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

import org.bitcoinj.core.Utils;

public class ACHParams extends AbstractBitcoinNetParams {

    public ACHParams() {
        super();
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        maxTarget = Utils.decodeCompactBits(0x1d00ffffL);
        dumpedPrivateKeyHeader = 128;

        // Address format is different to BTC, rest is the same
        addressHeader = 23; //BTG 38;
        p2shHeader = 34; //BTG 23;

        acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
        port = 7337; //BTC and BTG 8333
        packetMagic = 0x1461de3cL; //BTG 0xe1476d44L, BTC 0xf9beb4d9L;
        bip32HeaderPub = 0x02651F71; //BTG and BTC 0x0488B21E; //The 4 byte header that serializes in base58 to "xpub".
        bip32HeaderPriv = 0x02355E56; //BTG and BTC 0x0488ADE4; //The 4 byte header that serializes in base58 to "xprv"

        id = ID_MAINNET;
    }

    private static ACHParams instance;

    public static synchronized ACHParams get() {
        if (instance == null) {
            instance = new ACHParams();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_MAINNET;
    }
}
