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

package bisq.desktop.main.offer.offerbook;

import bisq.desktop.util.GUIUtil;

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentMethod;

import de.jensd.fx.glyphs.GlyphIcons;
import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j

public class OfferBookListItem {
    @Getter
    private final Offer offer;

    // We cache the data once created for performance reasons. AccountAgeWitnessService calls can
    // be a bit expensive.
    private WitnessAgeData witnessAgeData;

    public OfferBookListItem(Offer offer) {
        this.offer = offer;
    }

    public WitnessAgeData getWitnessAgeData(AccountAgeWitnessService accountAgeWitnessService,
                                            SignedWitnessService signedWitnessService) {
        if (witnessAgeData == null) {
            long ageInMs;
            long daysSinceSignedAsLong = -1;
            long accountAgeDaysAsLong = -1;
            long accountAgeDaysNotYetSignedAsLong = -1;
            String displayString;
            String info;
            GlyphIcons icon;

            if (CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode())) {
                // Altcoins
                displayString = Res.get("offerbook.timeSinceSigning.notSigned.noNeed");
                info = Res.get("shared.notSigned.noNeedAlts");
                icon = MaterialDesignIcon.INFORMATION_OUTLINE;
            } else if (PaymentMethod.hasChargebackRisk(offer.getPaymentMethod(), offer.getCurrencyCode())) {
                // Fiat and signed witness required
                Optional<AccountAgeWitness> optionalWitness = accountAgeWitnessService.findWitness(offer);
                AccountAgeWitnessService.SignState signState = optionalWitness.map(accountAgeWitnessService::getSignState)
                        .orElse(AccountAgeWitnessService.SignState.UNSIGNED);
                boolean isSignedAccountAgeWitness = optionalWitness.map(signedWitnessService::isSignedAccountAgeWitness)
                        .orElse(false);
                if (isSignedAccountAgeWitness || !signState.equals(AccountAgeWitnessService.SignState.UNSIGNED)) {
                    // either signed & limits lifted, or waiting for limits to be lifted
                    // Or banned
                    daysSinceSignedAsLong = TimeUnit.MILLISECONDS.toDays(optionalWitness.map(witness ->
                            accountAgeWitnessService.getWitnessSignAge(witness, new Date()))
                            .orElse(0L));
                    displayString = Res.get("offerbook.timeSinceSigning.daysSinceSigning", daysSinceSignedAsLong);
                    info = Res.get("offerbook.timeSinceSigning.info", signState.getDisplayString());
                } else {
                    // Unsigned case
                    ageInMs = optionalWitness.map(e -> accountAgeWitnessService.getAccountAge(e, new Date()))
                            .orElse(-1L);
                    accountAgeDaysNotYetSignedAsLong = ageInMs > -1 ? TimeUnit.MILLISECONDS.toDays(ageInMs) : 0;
                    displayString = Res.get("offerbook.timeSinceSigning.notSigned");
                    info = Res.get("shared.notSigned", accountAgeDaysNotYetSignedAsLong);
                }

                icon = GUIUtil.getIconForSignState(signState);
            } else {
                // Fiat, no signed witness required, we show account age
                ageInMs = accountAgeWitnessService.getAccountAge(offer);
                accountAgeDaysAsLong = ageInMs > -1 ? TimeUnit.MILLISECONDS.toDays(ageInMs) : 0;
                displayString = Res.get("offerbook.timeSinceSigning.notSigned.ageDays", accountAgeDaysAsLong);
                info = Res.get("shared.notSigned.noNeedDays", accountAgeDaysAsLong);
                icon = MaterialDesignIcon.CHECKBOX_MARKED_OUTLINE;
            }

            witnessAgeData = new WitnessAgeData(displayString, info, icon, daysSinceSignedAsLong, accountAgeDaysNotYetSignedAsLong, accountAgeDaysAsLong);
        }
        return witnessAgeData;
    }

    @Value
    public static class WitnessAgeData {
        private final String displayString;
        private final String info;
        private final GlyphIcons icon;
        private final Long daysSinceSignedAsLong;
        private final long accountAgeDaysNotYetSignedAsLong;
        private final Long accountAgeDaysAsLong;
        // Used for sorting
        private final Long type;
        // Used for sorting
        private final Long days;

        public WitnessAgeData(String displayString,
                              String info,
                              GlyphIcons icon,
                              long daysSinceSignedAsLong,
                              long accountAgeDaysNotYetSignedAsLong,
                              long accountAgeDaysAsLong) {
            this.displayString = displayString;
            this.info = info;
            this.icon = icon;
            this.daysSinceSignedAsLong = daysSinceSignedAsLong;
            this.accountAgeDaysNotYetSignedAsLong = accountAgeDaysNotYetSignedAsLong;
            this.accountAgeDaysAsLong = accountAgeDaysAsLong;

            if (daysSinceSignedAsLong > -1) {
                // First we show signed accounts sorted by days
                this.type = 3L;
                this.days = daysSinceSignedAsLong;
            } else if (accountAgeDaysNotYetSignedAsLong > -1) {
                // Next group is not yet signed accounts sorted by account age
                this.type = 2L;
                this.days = accountAgeDaysNotYetSignedAsLong;
            } else if (accountAgeDaysAsLong > -1) {
                // Next group is not signing required accounts sorted by account age
                this.type = 1L;
                this.days = accountAgeDaysAsLong;
            } else {
                // No signing and age required (altcoins)
                this.type = 0L;
                this.days = 0L;
            }
        }
    }
}

