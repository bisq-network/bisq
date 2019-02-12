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

package bisq.core.payment.payload;

import bisq.core.locale.Res;

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import org.bitcoinj.core.Coin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@EqualsAndHashCode(exclude = {"maxTradePeriod", "maxTradeLimit"})
@ToString
@Slf4j
public final class PaymentMethod implements PersistablePayload, Comparable {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    // time in blocks (average 10 min for one block confirmation
    private static final long DAY = TimeUnit.HOURS.toMillis(24);

    @Deprecated
    public static final String OK_PAY_ID = "OK_PAY";
    public static final String UPHOLD_ID = "UPHOLD";
    @Deprecated
    public static final String CASH_APP_ID = "CASH_APP"; // Removed due too high chargeback risk
    public static final String MONEY_BEAM_ID = "MONEY_BEAM";
    @Deprecated
    public static final String VENMO_ID = "VENMO";  // Removed due too high chargeback risk
    public static final String POPMONEY_ID = "POPMONEY";
    public static final String REVOLUT_ID = "REVOLUT";
    public static final String PERFECT_MONEY_ID = "PERFECT_MONEY";
    public static final String SEPA_ID = "SEPA";
    public static final String SEPA_INSTANT_ID = "SEPA_INSTANT";
    public static final String FASTER_PAYMENTS_ID = "FASTER_PAYMENTS";
    public static final String NATIONAL_BANK_ID = "NATIONAL_BANK";
    public static final String SAME_BANK_ID = "SAME_BANK";
    public static final String SPECIFIC_BANKS_ID = "SPECIFIC_BANKS";
    public static final String SWISH_ID = "SWISH";
    public static final String ALI_PAY_ID = "ALI_PAY";
    public static final String WECHAT_PAY_ID = "WECHAT_PAY";
    public static final String CLEAR_X_CHANGE_ID = "CLEAR_X_CHANGE";
    public static final String CHASE_QUICK_PAY_ID = "CHASE_QUICK_PAY";
    public static final String INTERAC_E_TRANSFER_ID = "INTERAC_E_TRANSFER";
    public static final String US_POSTAL_MONEY_ORDER_ID = "US_POSTAL_MONEY_ORDER";
    public static final String CASH_DEPOSIT_ID = "CASH_DEPOSIT";
    public static final String MONEY_GRAM_ID = "MONEY_GRAM";
    public static final String WESTERN_UNION_ID = "WESTERN_UNION";
    public static final String HAL_CASH_ID = "HAL_CASH";
    public static final String F2F_ID = "F2F";
    public static final String BLOCK_CHAINS_ID = "BLOCK_CHAINS";
    public static final String PROMPT_PAY_ID = "PROMPT_PAY";
    public static final String ADVANCED_CASH_ID = "ADVANCED_CASH";

    @Deprecated
    public static PaymentMethod OK_PAY;
    public static PaymentMethod UPHOLD;
    @Deprecated
    public static PaymentMethod CASH_APP; // Removed due too high chargeback risk
    public static PaymentMethod MONEY_BEAM;
    @Deprecated
    public static PaymentMethod VENMO; // Removed due too high chargeback risk
    public static PaymentMethod POPMONEY;
    public static PaymentMethod REVOLUT;
    public static PaymentMethod PERFECT_MONEY;
    public static PaymentMethod SEPA;
    public static PaymentMethod SEPA_INSTANT;
    public static PaymentMethod FASTER_PAYMENTS;
    public static PaymentMethod NATIONAL_BANK;
    public static PaymentMethod SAME_BANK;
    public static PaymentMethod SPECIFIC_BANKS;
    public static PaymentMethod SWISH;
    public static PaymentMethod ALI_PAY;
    public static PaymentMethod WECHAT_PAY;
    public static PaymentMethod CLEAR_X_CHANGE;
    public static PaymentMethod CHASE_QUICK_PAY;
    public static PaymentMethod INTERAC_E_TRANSFER;
    public static PaymentMethod US_POSTAL_MONEY_ORDER;
    public static PaymentMethod CASH_DEPOSIT;
    public static PaymentMethod MONEY_GRAM;
    public static PaymentMethod WESTERN_UNION;
    public static PaymentMethod F2F;
    public static PaymentMethod HAL_CASH;
    public static PaymentMethod BLOCK_CHAINS;
    public static PaymentMethod PROMPT_PAY;
    public static PaymentMethod ADVANCED_CASH;

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
            // we want to avoid more then 4 decimal places (0.125 / 4 = 0.03125), so we use a bit higher value to get 0.04 for first month
            Coin maxTradeLimitHighRisk = Coin.parseCoin("0.16");
            Coin maxTradeLimitMidRisk = Coin.parseCoin("0.25");
            Coin maxTradeLimitLowRisk = Coin.parseCoin("0.5");
            Coin maxTradeLimitVeryLowRisk = Coin.parseCoin("1");

            ALL_VALUES = new ArrayList<>(Arrays.asList(
                    // EUR
                    SEPA = new PaymentMethod(SEPA_ID, 6 * DAY, maxTradeLimitMidRisk),
                    SEPA_INSTANT = new PaymentMethod(SEPA_INSTANT_ID, DAY, maxTradeLimitMidRisk),
                    MONEY_BEAM = new PaymentMethod(MONEY_BEAM_ID, DAY, maxTradeLimitHighRisk),

                    // UK
                    FASTER_PAYMENTS = new PaymentMethod(FASTER_PAYMENTS_ID, DAY, maxTradeLimitMidRisk),

                    // Sweden
                    SWISH = new PaymentMethod(SWISH_ID, DAY, maxTradeLimitLowRisk),

                    // US
                    CLEAR_X_CHANGE = new PaymentMethod(CLEAR_X_CHANGE_ID, 4 * DAY, maxTradeLimitMidRisk),
                    CASH_APP = new PaymentMethod(CASH_APP_ID, DAY, maxTradeLimitHighRisk),

                    VENMO = new PaymentMethod(VENMO_ID, DAY, maxTradeLimitHighRisk),

                    POPMONEY = new PaymentMethod(POPMONEY_ID, DAY, maxTradeLimitHighRisk),
                    CHASE_QUICK_PAY = new PaymentMethod(CHASE_QUICK_PAY_ID, DAY, maxTradeLimitMidRisk),
                    US_POSTAL_MONEY_ORDER = new PaymentMethod(US_POSTAL_MONEY_ORDER_ID, 8 * DAY, maxTradeLimitMidRisk),

                    // Canada
                    INTERAC_E_TRANSFER = new PaymentMethod(INTERAC_E_TRANSFER_ID, DAY, maxTradeLimitMidRisk),

                    // Global
                    CASH_DEPOSIT = new PaymentMethod(CASH_DEPOSIT_ID, 4 * DAY, maxTradeLimitMidRisk),
                    MONEY_GRAM = new PaymentMethod(MONEY_GRAM_ID, 4 * DAY, maxTradeLimitMidRisk),
                    WESTERN_UNION = new PaymentMethod(WESTERN_UNION_ID, 4 * DAY, maxTradeLimitMidRisk),
                    NATIONAL_BANK = new PaymentMethod(NATIONAL_BANK_ID, 4 * DAY, maxTradeLimitMidRisk),
                    SAME_BANK = new PaymentMethod(SAME_BANK_ID, 2 * DAY, maxTradeLimitMidRisk),
                    SPECIFIC_BANKS = new PaymentMethod(SPECIFIC_BANKS_ID, 4 * DAY, maxTradeLimitMidRisk),
                    HAL_CASH = new PaymentMethod(HAL_CASH_ID, DAY, maxTradeLimitLowRisk),
                    F2F = new PaymentMethod(F2F_ID, 4 * DAY, maxTradeLimitLowRisk),

                    // Trans national
                    OK_PAY = new PaymentMethod(OK_PAY_ID, DAY, maxTradeLimitVeryLowRisk),
                    UPHOLD = new PaymentMethod(UPHOLD_ID, DAY, maxTradeLimitHighRisk),
                    REVOLUT = new PaymentMethod(REVOLUT_ID, DAY, maxTradeLimitHighRisk),
                    PERFECT_MONEY = new PaymentMethod(PERFECT_MONEY_ID, DAY, maxTradeLimitLowRisk),
                    ADVANCED_CASH = new PaymentMethod(ADVANCED_CASH_ID, DAY, maxTradeLimitVeryLowRisk),

                    // China
                    ALI_PAY = new PaymentMethod(ALI_PAY_ID, DAY, maxTradeLimitLowRisk),
                    WECHAT_PAY = new PaymentMethod(WECHAT_PAY_ID, DAY, maxTradeLimitLowRisk),

                    // Thailand
                    PROMPT_PAY = new PaymentMethod(PROMPT_PAY_ID, DAY, maxTradeLimitLowRisk),

                    // Altcoins
                    BLOCK_CHAINS = new PaymentMethod(BLOCK_CHAINS_ID, DAY, maxTradeLimitVeryLowRisk)
            ));

            ALL_VALUES.sort((o1, o2) -> {
                String id1 = o1.getId();
                if (id1.equals(CLEAR_X_CHANGE_ID))
                    id1 = "ZELLE";
                String id2 = o2.getId();
                if (id2.equals(CLEAR_X_CHANGE_ID))
                    id2 = "ZELLE";
                return id1.compareTo(id2);
            });
        }
        return ALL_VALUES;
    }

    public static List<PaymentMethod> getActivePaymentMethods() {
        return getAllValues().stream()
                .filter(paymentMethod -> !paymentMethod.getId().equals(PaymentMethod.VENMO_ID))
                .filter(paymentMethod -> !paymentMethod.getId().equals(PaymentMethod.CASH_APP_ID))
                .filter(paymentMethod -> !paymentMethod.getId().equals(PaymentMethod.OK_PAY_ID))
                .collect(Collectors.toList());
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
        return getAllValues().stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .orElseGet(() -> new PaymentMethod(Res.get("shared.na")));
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
