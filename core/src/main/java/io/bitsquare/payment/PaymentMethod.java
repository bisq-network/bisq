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

package io.bitsquare.payment;

import io.bitsquare.app.Version;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Don't use Enum as it breaks serialisation when changing entries and we want to stay flexible here
public class PaymentMethod implements Serializable, Comparable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    // time in blocks (average 10 min for one block confirmation
    private static final int HOUR = 6;
    private static final int DAY = HOUR * 24; // 144

    public static final String OK_PAY_ID = "OK_PAY";
    public static final String PERFECT_MONEY_ID = "PERFECT_MONEY";
    public static final String SEPA_ID = "SEPA";
    public static final String SWISH_ID = "SWISH";
    public static final String ALI_PAY_ID = "ALI_PAY";
    /* public static final String FED_WIRE="FED_WIRE";*/
   /* public static final String TRANSFER_WISE="TRANSFER_WISE";*/
   /* public static final String US_POSTAL_MONEY_ORDER="US_POSTAL_MONEY_ORDER";*/
    public static final String BLOCK_CHAINS_ID = "BLOCK_CHAINS";

    public static PaymentMethod OK_PAY;
    public static PaymentMethod PERFECT_MONEY;
    public static PaymentMethod SEPA;
    public static PaymentMethod SWISH;
    public static PaymentMethod ALI_PAY;
    /* public static PaymentMethod FED_WIRE;*/
   /* public static PaymentMethod TRANSFER_WISE;*/
   /* public static PaymentMethod US_POSTAL_MONEY_ORDER;*/
    public static PaymentMethod BLOCK_CHAINS;

    public static final List<PaymentMethod> ALL_VALUES = new ArrayList<>(Arrays.asList(
            OK_PAY = new PaymentMethod(OK_PAY_ID, 0, DAY), // tx instant so min. wait time 
            PERFECT_MONEY = new PaymentMethod(PERFECT_MONEY_ID, 0, DAY),
            SEPA = new PaymentMethod(SEPA_ID, 0, 7 * DAY), // sepa takes 1-3 business days. We use 7 days to include safety for holidays
            SWISH = new PaymentMethod(SWISH_ID, 0, DAY),
            ALI_PAY = new PaymentMethod(ALI_PAY_ID, 0, DAY),
           /* FED_WIRE = new PaymentMethod(FED_WIRE_ID, 0, DAY),*/
           /* TRANSFER_WISE = new PaymentMethod(TRANSFER_WISE_ID, 0, DAY),*/
           /* US_POSTAL_MONEY_ORDER = new PaymentMethod(US_POSTAL_MONEY_ORDER_ID, 0, DAY),*/
            BLOCK_CHAINS = new PaymentMethod(BLOCK_CHAINS_ID, 0, DAY)
    ));


    private final String id;

    private final long lockTime;

    private final int maxTradePeriod;

    /**
     * @param id
     * @param lockTime       lock time when seller release BTC until the payout tx gets valid (bitcoin tx lockTime). Serves as protection
     *                       against charge back risk. If Bank do the charge back quickly the Arbitrator and the seller can push another
     *                       double spend tx to invalidate the time locked payout tx. For the moment we set all to 0 but will have it in
     *                       place when needed.
     * @param maxTradePeriod The min. period a trader need to wait until he gets displayed the contact form for opening a dispute.
     */
    public PaymentMethod(String id, long lockTime, int maxTradePeriod) {
        this.id = id;
        this.lockTime = lockTime;
        this.maxTradePeriod = maxTradePeriod;
    }

    public static PaymentMethod getPaymentMethodByName(String name) {
        return ALL_VALUES.stream().filter(e -> e.getId().equals(name)).findFirst().get();
    }

    public String getId() {
        return id;
    }

    public int getMaxTradePeriod() {
        return maxTradePeriod;
    }

    public long getLockTime() {
        return lockTime;
    }

    @Override
    public int compareTo(@NotNull Object other) {
        return this.id.compareTo(((PaymentMethod) other).id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentMethod)) return false;

        PaymentMethod that = (PaymentMethod) o;

        if (getLockTime() != that.getLockTime()) return false;
        if (getMaxTradePeriod() != that.getMaxTradePeriod()) return false;
        return !(getId() != null ? !getId().equals(that.getId()) : that.getId() != null);

    }

    @Override
    public int hashCode() {
        int result = getId() != null ? getId().hashCode() : 0;
        result = 31 * result + (int) (getLockTime() ^ (getLockTime() >>> 32));
        result = 31 * result + getMaxTradePeriod();
        return result;
    }

    @Override
    public String toString() {
        return "PaymentMethod{" +
                "name='" + id + '\'' +
                ", lockTime=" + lockTime +
                ", waitPeriodForOpenDispute=" + maxTradePeriod +
                '}';
    }
}
