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

package bisq.apitest.scenario.bot.script;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.annotation.Nullable;

@Getter
@ToString
public
class BotScript {

    // Common, default is true.
    private final boolean useTestHarness;

    // Used only with test harness.  Mutually exclusive, but if both are not null,
    // the botPaymentMethodId takes precedence over countryCode.
    @Nullable
    private final String botPaymentMethodId;
    @Nullable
    private final String countryCode;

    // Used only without test harness.
    @Nullable
    @Setter
    private String paymentAccountIdForBot;
    @Nullable
    @Setter
    private String paymentAccountIdForCliScripts;

    // Common, used with or without test harness.
    private final int apiPortForCliScripts;
    private final String[] actions;
    private final long protocolStepTimeLimitInMinutes;
    private final boolean printCliScripts;
    private final boolean stayAlive;

    @SuppressWarnings("NullableProblems")
    BotScript(boolean useTestHarness,
              String botPaymentMethodId,
              String countryCode,
              String paymentAccountIdForBot,
              String paymentAccountIdForCliScripts,
              String[] actions,
              int apiPortForCliScripts,
              long protocolStepTimeLimitInMinutes,
              boolean printCliScripts,
              boolean stayAlive) {
        this.useTestHarness = useTestHarness;
        this.botPaymentMethodId = botPaymentMethodId;
        this.countryCode = countryCode != null ? countryCode.toUpperCase() : null;
        this.paymentAccountIdForBot = paymentAccountIdForBot;
        this.paymentAccountIdForCliScripts = paymentAccountIdForCliScripts;
        this.apiPortForCliScripts = apiPortForCliScripts;
        this.actions = actions;
        this.protocolStepTimeLimitInMinutes = protocolStepTimeLimitInMinutes;
        this.printCliScripts = printCliScripts;
        this.stayAlive = stayAlive;
    }
}
