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

package bisq.desktop.main.account.register.refundagent;


import bisq.desktop.common.view.FxmlView;
import bisq.desktop.main.account.register.AgentRegistrationView;

import bisq.core.locale.Res;
import bisq.core.support.dispute.refund.refundagent.RefundAgent;

import bisq.common.config.Config;

import javax.inject.Named;

import javax.inject.Inject;

@FxmlView
public class RefundAgentRegistrationView extends AgentRegistrationView<RefundAgent, RefundAgentRegistrationViewModel> {

    @Inject
    public RefundAgentRegistrationView(RefundAgentRegistrationViewModel model,
                                       @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(model, useDevPrivilegeKeys);
    }

    @Override
    protected String getRole() {
        return Res.get("shared.refundAgentForSupportStaff");
    }
}
