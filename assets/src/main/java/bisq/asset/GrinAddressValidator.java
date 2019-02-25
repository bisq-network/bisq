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

package bisq.asset;

/**
 * We only support the grinbox format as it is currently the only tool which offers a validation options of sender.
 * Beside that is the IP:port format very insecure with MITM attacks.
 *
 * Here is the information from a conversation with the Grinbox developer regarding the Grinbox address format.
 *
 A Grinbox address is of the format: grinbox://<key>@domain.com:port where everything besides <key> is optional.
 If no domain is specified, the default relay grinbox.io will be used.

 The <key> is a base58check encoded value (like in Bitcoin). For Grin mainnet, the first 2 bytes will be [1, 11] and
 the following 33 bytes should be a valid secp256k1 compressed public key.

 Some examples of valid addresses are:

 gVvRNiuopubvxPrs1BzJdQjVdFAxmkLzMqiVJzUZ7ubznhdtNTGB
 gVvUcSafSTD3YTSqgNf9ojEYWkz3zMZNfsjdpdb9en5mxc6gmja6
 gVvk7rLBg3r3qoWYL3VsREnBbooT7nynxx5HtDvUWCJUaNCnddvY
 grinbox://gVtWzX5NTLCBkyNV19QVdnLXue13heAVRD36sfkGD6xpqy7k7e4a
 gVw9TWimGFXRjoDXWhWxeNQbu84ZpLkvnenkKvA5aJeDo31eM5tC@somerelay.com
 grinbox://gVwjSsYW5vvHpK4AunJ5piKhhQTV6V3Jb818Uqs6PdC3SsB36AsA@somerelay.com:1220

 Some examples of invalid addresses are:

 gVuBJDKcWkhueMfBLAbFwV4ax55YXPeinWXdRME1Zi3eiC6sFNye (invalid checksum)
 geWGCMQjxZMHG3EtTaRbR7rH9rE4DsmLfpm1iiZEa7HFKjjkgpf2 (wrong version bytes)
 gVvddC2jYAfxTxnikcbTEQKLjhJZpqpBg39tXkwAKnD2Pys2mWiK (invalid public key)

 We only add the basic validation without checksum, version byte and pubkey validation as that would require much more
 effort. Any Grin developer is welcome to add that though!

 */
public class GrinAddressValidator implements AddressValidator {
    // A Grin Wallet URL (address is not the correct term) can be in the form IP:port or a grinbox format.
    // The grinbox has the format grinbox://<key>@domain.com:port where everything beside the key is optional.


    // Regex for IP validation borrowed from https://stackoverflow.com/questions/53497/regular-expression-that-matches-valid-ipv6-addresses
    private static final String PORT = "((6553[0-5])|(655[0-2][0-9])|(65[0-4][0-9]{2})|(6[0-4][0-9]{3})|([1-5][0-9]{4})|([0-5]{0,5})|([0-9]{1,4}))$";
    private static final String DOMAIN = "[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]\\.[a-zA-Z]{2,}$";
    private static final String KEY = "[a-km-zA-HJ-NP-Z1-9]{52}$";

    public GrinAddressValidator() {
    }

    @Override
    public AddressValidationResult validate(String address) {
        if (address == null || address.length() == 0)
            return AddressValidationResult.invalidAddress("Address may not be empty (only Grinbox format is supported)");

        // We only support grinbox address
        String key;
        String domain = null;
        String port = null;
        address = address.replace("grinbox://", "");
        if (address.contains("@")) {
            String[] keyAndDomain = address.split("@");
            key = keyAndDomain[0];
            if (keyAndDomain.length > 1) {
                domain = keyAndDomain[1];
                if (domain.contains(":")) {
                    String[] domainAndPort = domain.split(":");
                    domain = domainAndPort[0];
                    if (domainAndPort.length > 1)
                        port = domainAndPort[1];
                }
            }
        } else {
            key = address;
        }

        if (!key.matches("^" + KEY))
            return AddressValidationResult.invalidAddress("Invalid key (only Grinbox format is supported)");

        if (domain != null && !domain.matches("^" + DOMAIN))
            return AddressValidationResult.invalidAddress("Invalid domain (only Grinbox format is supported)");

        if (port != null && !port.matches("^" + PORT))
            return AddressValidationResult.invalidAddress("Invalid port (only Grinbox format is supported)");

        return AddressValidationResult.validAddress();

    }
}
