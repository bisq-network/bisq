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

import bisq.core.arbitration.DisputeManager;

import javax.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import lombok.Getter;

public class DisputePresentation {
    @Getter
    private final StringProperty numOpenDisputes = new SimpleStringProperty();
    @Getter
    private final BooleanProperty showOpenDisputesNotification = new SimpleBooleanProperty();

    @Inject
    public DisputePresentation(DisputeManager disputeManager) {
        disputeManager.getNumOpenDisputes().addListener((observable, oldValue, newValue) -> {
            int openDisputes = (int) newValue;
            if (openDisputes > 0)
                numOpenDisputes.set(String.valueOf(openDisputes));
            if (openDisputes > 9)
                numOpenDisputes.set("★");

            showOpenDisputesNotification.set(openDisputes > 0);
        });
    }
}
