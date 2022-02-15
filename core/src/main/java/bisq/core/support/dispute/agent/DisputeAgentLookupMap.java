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

package bisq.core.support.dispute.agent;

import bisq.core.locale.Res;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class DisputeAgentLookupMap {

    // See also: https://bisq.wiki/Finding_your_mediator
    @Nullable
    public static String getMatrixUserName(String fullAddress) {
        if (fullAddress.matches("localhost(.*)")) {
            return fullAddress; // on regtest, agent displays as localhost
        }
        switch (fullAddress) {
            case "7hkpotiyaukuzcfy6faihjaols5r2mkysz7bm3wrhhbpbphzz3zbwyqd.onion:9999":
                return "leo816";
            case "wizbisqzd7ku25di7p2ztsajioabihlnyp5lq5av66tmu7do2dke2tid.onion:9999":
                return "wiz";
            case "apbp7ubuyezav4hy.onion:9999":
                return "bisq_knight";
            case "a56olqlmmpxrn5q34itq5g5tb5d3fg7vxekpbceq7xqvfl3cieocgsyd.onion:9999":
                return "huey735";
            case "3z5jnirlccgxzoxc6zwkcgwj66bugvqplzf6z2iyd5oxifiaorhnanqd.onion:9999":
                return "refundagent2";
            case "6c4cim7h7t3bm4bnchbf727qrhdfrfr6lhod25wjtizm2sifpkktvwad.onion:9999":
                return "pazza83";
            default:
                log.warn("No user name for dispute agent with address {} found.", fullAddress);
                return Res.get("shared.na");
        }
    }

    public static String getMatrixLinkForAgent(String onion) {
        // when a new mediator starts or an onion address changes, mediator name won't be known until
        // the table above is updated in the software.
        // as a stopgap measure, replace unknown ones with a link to the Bisq team
        String agentName = getMatrixUserName(onion).replaceAll(Res.get("shared.na"), "bisq");
        return "https://matrix.to/#/@" + agentName + ":matrix.org";
    }
}
