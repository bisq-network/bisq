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

package io.bitsquare.btc;

import com.google.inject.Inject;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeeService {
    private static final Logger log = LoggerFactory.getLogger(FeeService.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FeeService() {
    }

    int counter = 0;

    public Coin getTxFee() {
        counter += 100;
        // log.error("getTxFee " + (20_000 + counter));
        //return Coin.valueOf(20_000 + counter);
        return Coin.valueOf(20_000);
    }

    public Coin getTxFeeForWithdrawal() {
        return Coin.valueOf(20_000);
    }

    public Coin getCreateOfferFee() {
        return Coin.valueOf(50_000);
    }

    public Coin getTakeOfferFee() {
        return Coin.valueOf(100_000);
    }

}
