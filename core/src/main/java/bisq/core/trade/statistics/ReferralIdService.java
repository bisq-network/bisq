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

package bisq.core.trade.statistics;

import bisq.core.user.Preferences;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.Optional;

import javax.annotation.Nullable;

public class ReferralIdService {
    private final Preferences preferences;
    private Optional<String> optionalReferralId = Optional.empty();

    @Inject
    public ReferralIdService(Preferences preferences) {
        this.preferences = preferences;
    }

    public boolean verify(String referralId) {
        return Arrays.stream(ReferralId.values()).anyMatch(e -> e.name().equals(referralId));
    }

    public Optional<String> getOptionalReferralId() {
        String referralId = preferences.getReferralId();
        if (referralId != null && !referralId.isEmpty() && verify(referralId))
            optionalReferralId = Optional.of(referralId);
        else
            optionalReferralId = Optional.empty();

        return optionalReferralId;
    }

    public void setReferralId(@Nullable String referralId) {
        if (referralId == null || referralId.isEmpty() || verify(referralId)) {
            optionalReferralId = Optional.ofNullable(referralId);
            preferences.setReferralId(referralId);
        }
    }
}
