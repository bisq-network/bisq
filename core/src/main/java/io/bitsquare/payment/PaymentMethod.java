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
import io.bitsquare.common.persistance.Persistable;
import org.bitcoinj.core.Coin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// Don't use Enum as it breaks serialisation when changing entries and we want to stay flexible here
public final class PaymentMethod implements Persistable, Comparable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    // time in blocks (average 10 min for one block confirmation
    private static final long HOUR = 3600_000;
    private static final long DAY = HOUR * 24;

    public static final String OK_PAY_ID = "OK_PAY";
    public static final String PERFECT_MONEY_ID = "PERFECT_MONEY";
    public static final String SEPA_ID = "SEPA";
    public static final String NATIONAL_BANK_ID = "NATIONAL_BANK";
    public static final String SAME_BANK_ID = "SAME_BANK";
    public static final String SPECIFIC_BANKS_ID = "SPECIFIC_BANKS";
    public static final String SWISH_ID = "SWISH";
    public static final String ALI_PAY_ID = "ALI_PAY";
    public static final String CLEAR_X_CHANGE_ID = "CLEAR_X_CHANGE";
    public static final String US_POSTAL_MONEY_ORDER_ID = "US_POSTAL_MONEY_ORDER";
    public static final String CASH_DEPOSIT_ID = "CASH_DEPOSIT";
    public static final String BLOCK_CHAINS_ID = "BLOCK_CHAINS";

    public static PaymentMethod OK_PAY;
    public static PaymentMethod PERFECT_MONEY;
    public static PaymentMethod SEPA;
    public static PaymentMethod NATIONAL_BANK;
    public static PaymentMethod SAME_BANK;
    public static PaymentMethod SPECIFIC_BANKS;
    public static PaymentMethod SWISH;
    public static PaymentMethod ALI_PAY;
    public static PaymentMethod CLEAR_X_CHANGE;
    public static PaymentMethod US_POSTAL_MONEY_ORDER;
    public static PaymentMethod CASH_DEPOSIT;
    public static PaymentMethod BLOCK_CHAINS;

    public static final List<PaymentMethod> ALL_VALUES = new ArrayList<>(Arrays.asList(
            OK_PAY = new PaymentMethod(OK_PAY_ID, 0, DAY, Coin.parseCoin("1.5")), // tx instant so min. wait time 
            SEPA = new PaymentMethod(SEPA_ID, 0, 8 * DAY, Coin.parseCoin("1")), // sepa takes 1-3 business days. We use 8 days to include safety for holidays
            NATIONAL_BANK = new PaymentMethod(NATIONAL_BANK_ID, 0, 4 * DAY, Coin.parseCoin("1")),
            SAME_BANK = new PaymentMethod(SAME_BANK_ID, 0, 2 * DAY, Coin.parseCoin("1")),
            SPECIFIC_BANKS = new PaymentMethod(SPECIFIC_BANKS_ID, 0, 4 * DAY, Coin.parseCoin("1")),
            PERFECT_MONEY = new PaymentMethod(PERFECT_MONEY_ID, 0, DAY, Coin.parseCoin("1")),
            SWISH = new PaymentMethod(SWISH_ID, 0, DAY, Coin.parseCoin("1.5")),
            ALI_PAY = new PaymentMethod(ALI_PAY_ID, 0, DAY, Coin.parseCoin("1.5")),
            CLEAR_X_CHANGE = new PaymentMethod(CLEAR_X_CHANGE_ID, 0, 8 * DAY, Coin.parseCoin("0.5")),
            US_POSTAL_MONEY_ORDER = new PaymentMethod(US_POSTAL_MONEY_ORDER_ID, 0, 6 * DAY, Coin.parseCoin("0.5")),
            CASH_DEPOSIT = new PaymentMethod(CASH_DEPOSIT_ID, 0, 6 * DAY, Coin.parseCoin("0.5")),
            BLOCK_CHAINS = new PaymentMethod(BLOCK_CHAINS_ID, 0, DAY, Coin.parseCoin("2"))
    ));

    private final String id;
    private long lockTime;
    private long maxTradePeriod;
    private Coin maxTradeLimit;

    /**
     * @param id
     * @param lockTime       lock time when seller release BTC until the payout tx gets valid (bitcoin tx lockTime). Serves as protection
     *                       against charge back risk. If Bank do the charge back quickly the Arbitrator and the seller can push another
     *                       double spend tx to invalidate the time locked payout tx. For the moment we set all to 0 but will have it in
     *                       place when needed.
     * @param maxTradePeriod The min. period a trader need to wait until he gets displayed the contact form for opening a dispute.
     * @param maxTradeLimit  The max. allowed trade amount in Bitcoin for that payment method (depending on charge back risk)
     */
    public PaymentMethod(String id, long lockTime, long maxTradePeriod, Coin maxTradeLimit) {
        this.id = id;
        this.lockTime = lockTime;
        this.maxTradePeriod = maxTradePeriod;
        this.maxTradeLimit = maxTradeLimit;
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();

            // In case we update those values we want that the persisted accounts get updated as well
            PaymentMethod paymentMethod = PaymentMethod.getPaymentMethodById(id);
            this.lockTime = paymentMethod.getLockTime();
            this.maxTradePeriod = paymentMethod.getMaxTradePeriod();
            this.maxTradeLimit = paymentMethod.getMaxTradeLimit();
        } catch (Throwable t) {
            log.warn("Cannot be deserialized." + t.getMessage());
        }
    }

    public static PaymentMethod getPaymentMethodById(String name) {
        Optional<PaymentMethod> paymentMethodOptional = ALL_VALUES.stream().filter(e -> e.getId().equals(name)).findFirst();
        if (paymentMethodOptional.isPresent())
            return paymentMethodOptional.get();
        else
            return new PaymentMethod("N/A", 1, DAY, Coin.parseCoin("0"));
    }

    public String getId() {
        return id;
    }

    public long getMaxTradePeriod() {
        return maxTradePeriod;
    }

    public long getLockTime() {
        return lockTime;
    }

    public Coin getMaxTradeLimit() {
        return maxTradeLimit;
    }

    @Override
    public int compareTo(@NotNull Object other) {
        if (id != null)
            return this.id.compareTo(((PaymentMethod) other).id);
        else
            return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentMethod)) return false;

        PaymentMethod that = (PaymentMethod) o;

        if (lockTime != that.lockTime) return false;
        if (maxTradePeriod != that.maxTradePeriod) return false;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        return !(maxTradeLimit != null ? !maxTradeLimit.equals(that.maxTradeLimit) : that.maxTradeLimit != null);

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (int) (lockTime ^ (lockTime >>> 32));
        result = 31 * result + (int) (maxTradePeriod ^ (maxTradePeriod >>> 32));
        result = 31 * result + (maxTradeLimit != null ? maxTradeLimit.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "PaymentMethod{" +
                "id='" + id + '\'' +
                ", lockTime=" + lockTime +
                ", maxTradePeriod=" + maxTradePeriod +
                ", maxTradeLimitInBitcoin=" + maxTradeLimit +
                '}';
    }
}
