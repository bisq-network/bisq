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

package io.bisq.core.trade;

import io.bisq.common.persistence.Persistable;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OpenOffer;
import io.bisq.generated.protobuffer.PB;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

public interface Tradable extends Persistable {
    Offer getOffer();

    Date getDate();

    String getId();

    String getShortId();


}
