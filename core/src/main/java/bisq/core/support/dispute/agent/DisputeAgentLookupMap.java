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
    public static String getKeyBaseUserName(String fullAddress) {
        switch (fullAddress) {
            case "sjlho4zwp3gecspf.onion:9999":
                return "leo816";
            case "wizbisqzd7ku25di7p2ztsajioabihlnyp5lq5av66tmu7do2dke2tid.onion:9999":
                return "wiz";
            case "apbp7ubuyezav4hy.onion:9999":
                return "bisq_knight";
            case "a56olqlmmpxrn5q34itq5g5tb5d3fg7vxekpbceq7xqvfl3cieocgsyd.onion:9999":
                return "huey735";
            case "3z5jnirlccgxzoxc6zwkcgwj66bugvqplzf6z2iyd5oxifiaorhnanqd.onion:9999":
                return "refundagent2";
            default:
                log.warn("No user name for dispute agent with address {} found.", fullAddress);
                return Res.get("shared.na");
        }
    }
}
