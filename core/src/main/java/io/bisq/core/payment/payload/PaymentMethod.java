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

package io.bisq.core.payment.payload;

import io.bisq.common.locale.Res;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@EqualsAndHashCode(exclude = {"maxTradePeriod", "maxTradeLimit"})
@ToString
@Slf4j
public final class PaymentMethod implements PersistablePayload, Comparable {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    // time in blocks (average 10 min for one block confirmation
    private static final long HOUR = 3600_000;
    private static final long DAY = HOUR * 24;

    public static final String OK_PAY_ID = "OK_PAY";
    public static final String PERFECT_MONEY_ID = "PERFECT_MONEY";
    public static final String SEPA_ID = "SEPA";
    public static final String FASTER_PAYMENTS_ID = "FASTER_PAYMENTS";
    public static final String NATIONAL_BANK_ID = "NATIONAL_BANK";
    public static final String SAME_BANK_ID = "SAME_BANK";
    public static final String SPECIFIC_BANKS_ID = "SPECIFIC_BANKS";
    public static final String SWISH_ID = "SWISH";
    public static final String ALI_PAY_ID = "ALI_PAY";
    public static final String CLEAR_X_CHANGE_ID = "CLEAR_X_CHANGE";
    public static final String CHASE_QUICK_PAY_ID = "CHASE_QUICK_PAY";
    public static final String INTERAC_E_TRANSFER_ID = "INTERAC_E_TRANSFER";
    public static final String US_POSTAL_MONEY_ORDER_ID = "US_POSTAL_MONEY_ORDER";
    public static final String CASH_DEPOSIT_ID = "CASH_DEPOSIT";
    public static final String WESTERN_UNION_ID = "WESTERN_UNION";
    public static final String BLOCK_CHAINS_ID = "BLOCK_CHAINS";

    public static PaymentMethod OK_PAY;
    public static PaymentMethod PERFECT_MONEY;
    public static PaymentMethod SEPA;
    public static PaymentMethod FASTER_PAYMENTS;
    public static PaymentMethod NATIONAL_BANK;
    public static PaymentMethod SAME_BANK;
    public static PaymentMethod SPECIFIC_BANKS;
    public static PaymentMethod SWISH;
    public static PaymentMethod ALI_PAY;
    public static PaymentMethod CLEAR_X_CHANGE;
    public static PaymentMethod CHASE_QUICK_PAY;
    public static PaymentMethod INTERAC_E_TRANSFER;
    public static PaymentMethod US_POSTAL_MONEY_ORDER;
    public static PaymentMethod CASH_DEPOSIT;
    public static PaymentMethod WESTERN_UNION;
    public static PaymentMethod BLOCK_CHAINS;

    private static List<PaymentMethod> ALL_VALUES;


    public static void onAllServicesInitialized() {
        getAllValues();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Instance fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Getter
    private final String id;
    @Getter
    private final long maxTradePeriod;
    private final long maxTradeLimit;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @param id             against charge back risk. If Bank do the charge back quickly the Arbitrator and the seller can push another
     *                       double spend tx to invalidate the time locked payout tx. For the moment we set all to 0 but will have it in
     *                       place when needed.
     * @param maxTradePeriod The min. period a trader need to wait until he gets displayed the contact form for opening a dispute.
     * @param maxTradeLimit  The max. allowed trade amount in Bitcoin for that payment method (depending on charge back risk)
     */
    public PaymentMethod(String id, long maxTradePeriod, @NotNull Coin maxTradeLimit) {
        this.id = id;
        this.maxTradePeriod = maxTradePeriod;
        this.maxTradeLimit = maxTradeLimit.value;
    }

    public static List<PaymentMethod> getAllValues() {
        if (ALL_VALUES == null) {
            Coin maxTradeLimitMidRisk;
            Coin maxTradeLimitLowRisk;
            Coin maxTradeLimitVeryLowRisk;
            switch (BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode()) {
                case "BTC":
                    // av. price June 2017: 2500 EUR/BTC
                    // av. price Oct 2017: 4700 EUR/BTC
                    maxTradeLimitMidRisk = Coin.parseCoin("0.25");
                    maxTradeLimitLowRisk = Coin.parseCoin("0.5");
                    maxTradeLimitVeryLowRisk = Coin.parseCoin("1");
                    break;
                case "LTC":
                    // av. price June 2017: 40 EUR/LTC
                    maxTradeLimitMidRisk = Coin.parseCoin("25");
                    maxTradeLimitLowRisk = Coin.parseCoin("50");
                    maxTradeLimitVeryLowRisk = Coin.parseCoin("100");
                    break;
                case "DOGE":
                    // av. price June 2017: 0.002850 EUR/DOGE
                    maxTradeLimitMidRisk = Coin.parseCoin("250000");
                    maxTradeLimitLowRisk = Coin.parseCoin("500000");
                    maxTradeLimitVeryLowRisk = Coin.parseCoin("1000000");
                    break;
                case "DASH":
                    // av. price June 2017: 150 EUR/DASH
                    maxTradeLimitMidRisk = Coin.parseCoin("10");
                    maxTradeLimitLowRisk = Coin.parseCoin("20");
                    maxTradeLimitVeryLowRisk = Coin.parseCoin("40");
                    break;

                default:
                    log.error("Unsupported BaseCurrency. " + BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode());
                    throw new RuntimeException("Unsupported BaseCurrency. " + BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode());
            }

            ALL_VALUES = new ArrayList<>(Arrays.asList(
                    // EUR
                    SEPA = new PaymentMethod(SEPA_ID, 6 * DAY, maxTradeLimitMidRisk),

                    // UK
                    FASTER_PAYMENTS = new PaymentMethod(FASTER_PAYMENTS_ID, DAY, maxTradeLimitMidRisk),

                    // Sweden
                    SWISH = new PaymentMethod(SWISH_ID, DAY, maxTradeLimitLowRisk),

                    // US
                    CLEAR_X_CHANGE = new PaymentMethod(CLEAR_X_CHANGE_ID, 4 * DAY, maxTradeLimitMidRisk),
                    CHASE_QUICK_PAY = new PaymentMethod(CHASE_QUICK_PAY_ID, DAY, maxTradeLimitMidRisk),
                    US_POSTAL_MONEY_ORDER = new PaymentMethod(US_POSTAL_MONEY_ORDER_ID, 8 * DAY, maxTradeLimitMidRisk),

                    // Canada
                    INTERAC_E_TRANSFER = new PaymentMethod(INTERAC_E_TRANSFER_ID, DAY, maxTradeLimitMidRisk),

                    // Global
                    CASH_DEPOSIT = new PaymentMethod(CASH_DEPOSIT_ID, 4 * DAY, maxTradeLimitMidRisk),
                    WESTERN_UNION = new PaymentMethod(WESTERN_UNION_ID, 4 * DAY, maxTradeLimitMidRisk),
                    NATIONAL_BANK = new PaymentMethod(NATIONAL_BANK_ID, 4 * DAY, maxTradeLimitMidRisk),
                    SAME_BANK = new PaymentMethod(SAME_BANK_ID, 2 * DAY, maxTradeLimitMidRisk),
                    SPECIFIC_BANKS = new PaymentMethod(SPECIFIC_BANKS_ID, 4 * DAY, maxTradeLimitMidRisk),

                    // Trans national
                    OK_PAY = new PaymentMethod(OK_PAY_ID, DAY, maxTradeLimitVeryLowRisk),
                    PERFECT_MONEY = new PaymentMethod(PERFECT_MONEY_ID, DAY, maxTradeLimitLowRisk),

                    // China
                    ALI_PAY = new PaymentMethod(ALI_PAY_ID, DAY, maxTradeLimitLowRisk),

                    // Altcoins
                    BLOCK_CHAINS = new PaymentMethod(BLOCK_CHAINS_ID, DAY, maxTradeLimitVeryLowRisk)
            ));
        }
        return ALL_VALUES;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.PaymentMethod toProtoMessage() {
        return PB.PaymentMethod.newBuilder()
                .setId(id)
                .setMaxTradePeriod(maxTradePeriod)
                .setMaxTradeLimit(maxTradeLimit)
                .build();
    }

    public static PaymentMethod fromProto(PB.PaymentMethod proto) {
        return new PaymentMethod(proto.getId(),
                proto.getMaxTradePeriod(),
                Coin.valueOf(proto.getMaxTradeLimit()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Used for dummy entries in payment methods list (SHOW_ALL)
    public PaymentMethod(String id) {
        this(id, 0, Coin.ZERO);
    }

    public static PaymentMethod getPaymentMethodById(String id) {
        Optional<PaymentMethod> paymentMethodOptional = getAllValues().stream().filter(e -> e.getId().equals(id)).findFirst();
        if (paymentMethodOptional.isPresent())
            return paymentMethodOptional.get();
        else
            return new PaymentMethod(Res.get("shared.na"));
    }

    // Hack for SF as the smallest unit is 1 SF ;-( and price is about 3 BTC!
    public Coin getMaxTradeLimitAsCoin(String currencyCode) {
        if (currencyCode.equals("SF") || currencyCode.equals("BSQ"))
            return Coin.parseCoin("4");
        else
            return Coin.valueOf(maxTradeLimit);
    }

    @Override
    public int compareTo(@NotNull Object other) {
        if (id != null)
            return this.id.compareTo(((PaymentMethod) other).id);
        else
            return 0;
    }
}
