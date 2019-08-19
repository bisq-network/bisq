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

/*
 * Copyright © 2019 Oneiro NA, Inc.
 */

package bisq.asset.coins;

import bisq.asset.Coin;
import bisq.asset.RegexAddressValidator;


public class Ndau extends Coin {

    public Ndau() {
        // note: ndau addresses contain an internal checksum which was deemed too complicated to include here.
        // this regex performs superficial validation, but there is a large space of addresses marked valid
        // by this regex which are not in fact valid ndau addresses. For actual ndau address validation,
        // use the Address class in github.com/oneiro-ndev/ndauj (java) or github.com/oneiro-ndev/ndaumath/pkg/address (go).
        super("ndau", "XND", new RegexAddressValidator("nd[anexbm][abcdefghijkmnpqrstuvwxyz23456789]{45}"));
    }
}
