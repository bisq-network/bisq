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

package bisq.core.util.validation;

public class RegexValidatorFactory {
    public static RegexValidator addressRegexValidator() {
        RegexValidator regexValidator = new RegexValidator();
        String portRegexPattern = "(0|[1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])";
        String onionV2RegexPattern = String.format("[a-zA-Z2-7]{16}\\.onion(?:\\:%1$s)?", portRegexPattern);
        String onionV3RegexPattern = String.format("[a-zA-Z2-7]{56}\\.onion(?:\\:%1$s)?", portRegexPattern);
        String ipv4RegexPattern = String.format("(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
                "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)" +
                "(?:\\:%1$s)?", portRegexPattern);
        String ipv6RegexPattern = "(" +
                "([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|" +          // 1:2:3:4:5:6:7:8
                "([0-9a-fA-F]{1,4}:){1,7}:|" +                         // 1::                              1:2:3:4:5:6:7::
                "([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|" +         // 1::8             1:2:3:4:5:6::8  1:2:3:4:5:6::8
                "([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|" +  // 1::7:8           1:2:3:4:5::7:8  1:2:3:4:5::8
                "([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|" +  // 1::6:7:8         1:2:3:4::6:7:8  1:2:3:4::8
                "([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|" +  // 1::5:6:7:8       1:2:3::5:6:7:8  1:2:3::8
                "([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|" +  // 1::4:5:6:7:8     1:2::4:5:6:7:8  1:2::8
                "[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|" +       // 1::3:4:5:6:7:8   1::3:4:5:6:7:8  1::8
                ":((:[0-9a-fA-F]{1,4}){1,7}|:)|" +                     // ::2:3:4:5:6:7:8  ::2:3:4:5:6:7:8 ::8       ::
                "fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|" +     // fe80::7:8%eth0   fe80::7:8%1
                "::(ffff(:0{1,4}){0,1}:){0,1}" +                       // (link-local IPv6 addresses with zone index)
                "((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}" +
                "(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|" +          // ::255.255.255.255   ::ffff:255.255.255.255  ::ffff:0:255.255.255.255
                "([0-9a-fA-F]{1,4}:){1,4}:" +                          // (IPv4-mapped IPv6 addresses and IPv4-translated addresses)
                "((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}" +
                "(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])" +           // 2001:db8:3:4::192.0.2.33  64:ff9b::192.0.2.33
                ")";                                                   // (IPv4-Embedded IPv6 Address)
        ipv6RegexPattern = String.format("(?:%1$s)|(?:\\[%1$s\\]\\:%2$s)", ipv6RegexPattern, portRegexPattern);
        String fqdnRegexPattern = String.format("(((?!-)[a-zA-Z0-9-]{1,63}(?<!-)\\.)+(?!onion)[a-zA-Z]{2,63}(?:\\:%1$s)?)", portRegexPattern);
        regexValidator.setPattern(String.format("^(?:(?:(?:%1$s)|(?:%2$s)|(?:%3$s)|(?:%4$s)|(?:%5$s)),\\s*)*(?:(?:%1$s)|(?:%2$s)|(?:%3$s)|(?:%4$s)|(?:%5$s))*$",
                onionV2RegexPattern, onionV3RegexPattern, ipv4RegexPattern, ipv6RegexPattern, fqdnRegexPattern));
        return regexValidator;
    }

    // checks if valid tor onion hostname with optional port at the end
    public static RegexValidator onionAddressRegexValidator() {
        RegexValidator regexValidator = new RegexValidator();
        String portRegexPattern = "(0|[1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])";
        String onionV2RegexPattern = String.format("[a-zA-Z2-7]{16}\\.onion(?:\\:%1$s)?", portRegexPattern);
        String onionV3RegexPattern = String.format("[a-zA-Z2-7]{56}\\.onion(?:\\:%1$s)?", portRegexPattern);
        regexValidator.setPattern(String.format("^(?:(?:(?:%1$s)|(?:%2$s)),\\s*)*(?:(?:%1$s)|(?:%2$s))*$",
                onionV2RegexPattern, onionV3RegexPattern));
        return regexValidator;
    }

    // checks if localhost address, with optional port at the end
    public static RegexValidator localhostAddressRegexValidator() {
        RegexValidator regexValidator = new RegexValidator();

        // match 0 ~ 65535
        String portRegexPattern = "(0|[1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])";

        // match 127/8 (127.0.0.0 ~ 127.255.255.255)
        String localhostIpv4RegexPattern = String.format(
                "(?:127\\.)" +
                        "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){2}" +
                        "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)" +
                        "(?:\\:%1$s)?",
                portRegexPattern);

        // match ::/64 with optional port at the end, i.e. ::1 or [::1]:8081
        String localhostIpv6RegexPattern = "(:((:[0-9a-fA-F]{1,4}){1,4}|:)|)";
        localhostIpv6RegexPattern = String.format("(?:%1$s)|(?:\\[%1$s\\]\\:%2$s)", localhostIpv6RegexPattern, portRegexPattern);

        // match *.local
        String localhostFqdnRegexPattern = String.format("(localhost(?:\\:%1$s)?)", portRegexPattern);

        regexValidator.setPattern(String.format("^(?:(?:(?:%1$s)|(?:%2$s)|(?:%3$s)),\\s*)*(?:(?:%1$s)|(?:%2$s)|(?:%3$s))*$",
                localhostIpv4RegexPattern, localhostIpv6RegexPattern, localhostFqdnRegexPattern));

        return regexValidator;
    }

    // checks if local area network address, with optional port at the end
    public static RegexValidator localnetAddressRegexValidator() {
        RegexValidator regexValidator = new RegexValidator();

        // match 0 ~ 65535
        String portRegexPattern = "(0|[1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])";

        // match 10/8 (10.0.0.0 ~ 10.255.255.255)
        String localnetIpv4RegexPatternA = String.format(
                "(?:10\\.)" +
                        "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){2}" +
                        "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)" +
                        "(?:\\:%1$s)?",
                portRegexPattern);

        // match 172.16/12 (172.16.0.0 ~ 172.31.255.255)
        String localnetIpv4RegexPatternB = String.format(
                "(?:172\\.)" +
                        "(?:(?:1[6-9]|2[0-9]|[3][0-1])\\.)" +
                        "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.)" +
                        "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)" +
                        "(?:\\:%1$s)?",
                portRegexPattern);

        // match 192.168/16 (192.168.0.0 ~ 192.168.255.255)
        String localnetIpv4RegexPatternC = String.format(
                "(?:192\\.)" +
                        "(?:168\\.)" +
                        "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.)" +
                        "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)" +
                        "(?:\\:%1$s)?",
                portRegexPattern);

        // match 169.254/15 (169.254.0.0 ~ 169.255.255.255)
        String autolocalIpv4RegexPattern = String.format(
                "(?:169\\.)" +
                        "(?:(?:254|255)\\.)" +
                        "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.)" +
                        "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)" +
                        "(?:\\:%1$s)?",
                portRegexPattern);

        // match fc00::/7  (fc00:: ~ fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff)
        String localnetIpv6RegexPattern = "(" +
                "([fF][cCdD][0-9a-fA-F]{2}:)([0-9a-fA-F]{1,4}:){6}[0-9a-fA-F]{1,4}|" +            // fd00:2:3:4:5:6:7:8
                "([fF][cCdD][0-9a-fA-F]{2}:)([0-9a-fA-F]{1,4}:){0,7}:|" +                         // fd00::                                 fd00:2:3:4:5:6:7::
                "([fF][cCdD][0-9a-fA-F]{2}:)([0-9a-fA-F]{1,4}:){0,6}:[0-9a-fA-F]{1,4}|" +         // fd00::8             fd00:2:3:4:5:6::8  fd00:2:3:4:5:6::8
                "([fF][cCdD][0-9a-fA-F]{2}:)([0-9a-fA-F]{1,4}:){0,5}(:[0-9a-fA-F]{1,4}){1,1}|" +  // fd00::7:8           fd00:2:3:4:5::7:8  fd00:2:3:4:5::8
                "([fF][cCdD][0-9a-fA-F]{2}:)([0-9a-fA-F]{1,4}:){0,4}(:[0-9a-fA-F]{1,4}){1,2}|" +  // fd00::7:8           fd00:2:3:4:5::7:8  fd00:2:3:4:5::8
                "([fF][cCdD][0-9a-fA-F]{2}:)([0-9a-fA-F]{1,4}:){0,3}(:[0-9a-fA-F]{1,4}){1,3}|" +  // fd00::6:7:8         fd00:2:3:4::6:7:8  fd00:2:3:4::8
                "([fF][cCdD][0-9a-fA-F]{2}:)([0-9a-fA-F]{1,4}:){0,2}(:[0-9a-fA-F]{1,4}){1,4}|" +  // fd00::5:6:7:8       fd00:2:3::5:6:7:8  fd00:2:3::8
                "([fF][cCdD][0-9a-fA-F]{2}:)([0-9a-fA-F]{1,4}:){0,1}(:[0-9a-fA-F]{1,4}){1,5}|" +  // fd00::4:5:6:7:8     fd00:2::4:5:6:7:8  fd00:2::8
                "([fF][cCdD][0-9a-fA-F]{2}:)(:[0-9a-fA-F]{1,4}){1,6}" +                           // fd00::3:4:5:6:7:8   fd00::3:4:5:6:7:8  fd00::8
                ")";

        // match fe80::/10 (fe80:: ~ febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff)
        String autolocalIpv6RegexPattern = "(" +
                "([fF][eE][8-9a-bA-B][0-9a-fA-F]:)([0-9a-fA-F]{1,4}:){6}[0-9a-fA-F]{1,4}|" +            // fe80:2:3:4:5:6:7:8
                "([fF][eE][8-9a-bA-B][0-9a-fA-F]:)([0-9a-fA-F]{1,4}:){0,7}:|" +                         // fe80::                                 fe80:2:3:4:5:6:7::
                "([fF][eE][8-9a-bA-B][0-9a-fA-F]:)([0-9a-fA-F]{1,4}:){0,6}:[0-9a-fA-F]{1,4}|" +         // fe80::8             fe80:2:3:4:5:6::8  fe80:2:3:4:5:6::8
                "([fF][eE][8-9a-bA-B][0-9a-fA-F]:)([0-9a-fA-F]{1,4}:){0,5}(:[0-9a-fA-F]{1,4}){1,1}|" +  // fe80::7:8           fe80:2:3:4:5::7:8  fe80:2:3:4:5::8
                "([fF][eE][8-9a-bA-B][0-9a-fA-F]:)([0-9a-fA-F]{1,4}:){0,4}(:[0-9a-fA-F]{1,4}){1,2}|" +  // fe80::7:8           fe80:2:3:4:5::7:8  fe80:2:3:4:5::8
                "([fF][eE][8-9a-bA-B][0-9a-fA-F]:)([0-9a-fA-F]{1,4}:){0,3}(:[0-9a-fA-F]{1,4}){1,3}|" +  // fe80::6:7:8         fe80:2:3:4::6:7:8  fe80:2:3:4::8
                "([fF][eE][8-9a-bA-B][0-9a-fA-F]:)([0-9a-fA-F]{1,4}:){0,2}(:[0-9a-fA-F]{1,4}){1,4}|" +  // fe80::5:6:7:8       fe80:2:3::5:6:7:8  fe80:2:3::8
                "([fF][eE][8-9a-bA-B][0-9a-fA-F]:)([0-9a-fA-F]{1,4}:){0,1}(:[0-9a-fA-F]{1,4}){1,5}|" +  // fe80::4:5:6:7:8     fe80:2::4:5:6:7:8  fe80:2::8
                "([fF][eE][8-9a-bA-B][0-9a-fA-F]:)(:[0-9a-fA-F]{1,4}){1,6}" +                           // fe80::3:4:5:6:7:8   fe80::3:4:5:6:7:8  fe80::8
                ")";

        // allow for brackets with optional port at the end
        localnetIpv6RegexPattern = String.format("(?:%1$s)|(?:\\[%1$s\\]\\:%2$s)", localnetIpv6RegexPattern, portRegexPattern);

        // allow for brackets with optional port at the end
        autolocalIpv6RegexPattern = String.format("(?:%1$s)|(?:\\[%1$s\\]\\:%2$s)", autolocalIpv6RegexPattern, portRegexPattern);

        // match *.local
        String localFqdnRegexPattern = String.format("(((?!-)[a-zA-Z0-9-]{1,63}(?<!-)\\.)+local(?:\\:%1$s)?)", portRegexPattern);

        regexValidator.setPattern(String.format("^(?:(?:(?:%1$s)|(?:%2$s)|(?:%3$s)|(?:%4$s)|(?:%5$s)|(?:%6$s)|(?:%7$s)),\\s*)*(?:(?:%1$s)|(?:%2$s)|(?:%3$s)|(?:%4$s)|(?:%5$s)|(?:%6$s)|(?:%7$s))*$",
                localnetIpv4RegexPatternA, localnetIpv4RegexPatternB, localnetIpv4RegexPatternC, autolocalIpv4RegexPattern, localnetIpv6RegexPattern, autolocalIpv6RegexPattern, localFqdnRegexPattern));
        return regexValidator;
    }
}
