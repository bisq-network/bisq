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

package io.bitsquare.crypto;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;

import com.google.common.base.Charsets;

import org.spongycastle.util.encoders.Base64;

public class SignatureService {

    public String signMessage(ECKey key, String message) {
        byte[] data = Utils.formatMessageForSigning(message);
        Sha256Hash hash = Sha256Hash.hashTwice(data);
        return signMessage(key, hash);
    }

    public ECKey.ECDSASignature signBytes(ECKey key, byte[] data) {
        return key.sign(Sha256Hash.hashTwice(data), null);
    }

    public String signMessage(ECKey key, byte[] data) {
        Sha256Hash hash = Sha256Hash.hashTwice(data);
        return signMessage(key, hash);
    }

    public String signMessage(ECKey key, Sha256Hash hash) {
        ECKey.ECDSASignature sig = key.sign(hash, null);
        // Now we have to work backwards to figure out the recId needed to recover the signature.
        int recId = -1;
        for (int i = 0; i < 4; i++) {
            ECKey k = ECKey.recoverFromSignature(i, sig, hash, key.isCompressed());
            if (k != null && k.getPubKeyPoint().equals(key.getPubKeyPoint())) {
                recId = i;
                break;
            }
        }
        if (recId == -1)
            throw new RuntimeException("Could not construct a recoverable key. This should never happen.");
        int headerByte = recId + 27 + (key.isCompressed() ? 4 : 0);
        byte[] sigData = new byte[65];  // 1 header + 32 bytes for R + 32 bytes for S
        sigData[0] = (byte) headerByte;
        System.arraycopy(Utils.bigIntegerToBytes(sig.r, 32), 0, sigData, 1, 32);
        System.arraycopy(Utils.bigIntegerToBytes(sig.s, 32), 0, sigData, 33, 32);
        return new String(Base64.encode(sigData), Charsets.UTF_8);
    }


    public byte[] digestMessageWithSignature(ECKey key, String message) {
        String signedMessage = signMessage(key, message);
        return Utils.sha256hash160(message.concat(signedMessage).getBytes(Charsets.UTF_8));
    }

    public boolean verify(byte[] signaturePubKey, byte[] data, ECKey.ECDSASignature sig) {
        return ECKey.fromPublicOnly(signaturePubKey).verify(Sha256Hash.hashTwice(data), sig);
    }
}
