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

package bisq.core.presentation;

import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.refund.RefundManager;

import javax.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import lombok.Getter;

public class SupportTicketsPresentation {
    @Getter
    private final StringProperty numOpenSupportTickets = new SimpleStringProperty();
    @Getter
    private final BooleanProperty showOpenSupportTicketsNotification = new SimpleBooleanProperty();

    @org.jetbrains.annotations.NotNull
    private final ArbitrationManager arbitrationManager;
    @org.jetbrains.annotations.NotNull
    private final MediationManager mediationManager;
    @org.jetbrains.annotations.NotNull
    private final RefundManager refundManager;

    @Inject
    public SupportTicketsPresentation(ArbitrationManager arbitrationManager,
                                      MediationManager mediationManager,
                                      RefundManager refundManager) {
        this.arbitrationManager = arbitrationManager;
        this.mediationManager = mediationManager;
        this.refundManager = refundManager;

        arbitrationManager.getNumOpenDisputes().addListener((observable, oldValue, newValue) -> onChange());
        mediationManager.getNumOpenDisputes().addListener((observable, oldValue, newValue) -> onChange());
        refundManager.getNumOpenDisputes().addListener((observable, oldValue, newValue) -> onChange());
    }

    private void onChange() {
        int supportTickets = arbitrationManager.getNumOpenDisputes().get() +
                mediationManager.getNumOpenDisputes().get() +
                refundManager.getNumOpenDisputes().get();

        numOpenSupportTickets.set(String.valueOf(supportTickets));
        showOpenSupportTicketsNotification.set(supportTickets > 0);
    }
}
