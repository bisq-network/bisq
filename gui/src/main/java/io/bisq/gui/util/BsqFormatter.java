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

package io.bisq.gui.util;

import io.bisq.core.btc.wallet.WalletUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.utils.MonetaryFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class BsqFormatter extends BSFormatter {
    private static final Logger log = LoggerFactory.getLogger(BsqFormatter.class);
    private static final boolean useBsqAddressFormat = false;

    @Inject
    private BsqFormatter() {
        super();
        coinFormat = new MonetaryFormat().shift(5).code(5, "BSQ").minDecimals(3);
    }

    /**
     * Returns the base-58 encoded String representation of this
     * object, including version and checksum bytes.
     */
    public String getBsqAddressStringFromAddress(Address address) {
        if (useBsqAddressFormat) {
            byte[] bytes = address.getHash160();
            int version = address.getVersion();
            // A stringified buffer is:
            //   1 byte version + data bytes + 4 bytes check code (a truncated hash)
            byte[] addressBytes = new byte[1 + bytes.length + 4];
            addressBytes[0] = (byte) version;
            System.arraycopy(bytes, 0, addressBytes, 1, bytes.length);
            byte[] checksum = Sha256Hash.hashTwice(addressBytes, 0, bytes.length + 1);
            System.arraycopy(checksum, 0, addressBytes, bytes.length + 1, 4);
            // return "BSQ" + Base58Bsq.encode(addressBytes);
            return Base58Bsq.encode(addressBytes);
        } else {
            return address.toString();
        }
    }


    public Address getAddressFromBsqAddress(String encoded) {
        if (useBsqAddressFormat) {
            try {
                //encoded = encoded.substring(3, encoded.length());
                byte[] versionAndDataBytes = Base58Bsq.decodeChecked(encoded);
                byte[] bytes = new byte[versionAndDataBytes.length - 1];
                System.arraycopy(versionAndDataBytes, 1, bytes, 0, versionAndDataBytes.length - 1);
                return new Address(WalletUtils.getParameters(), bytes);
            } catch (AddressFormatException e) {
                log.error(e.toString());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } else {
            try {
                return new Address(WalletUtils.getParameters(), encoded);
            } catch (AddressFormatException e) {
                log.error(e.toString());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }
}
