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

package bisq.desktop.main.account.content.arbitratorselection;

import bisq.desktop.util.BSFormatter;

import bisq.core.arbitration.Arbitrator;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.Date;

public class ArbitratorListItem {
    public final Arbitrator arbitrator;
    private final BSFormatter formatter;
    private final BooleanProperty isSelected = new SimpleBooleanProperty();

    public ArbitratorListItem(Arbitrator arbitrator, BSFormatter formatter) {
        this.arbitrator = arbitrator;
        this.formatter = formatter;
    }

    public String getAddressString() {
        return arbitrator != null ? arbitrator.getNodeAddress().getFullAddress() : "";
    }

    public String getLanguageCodes() {
        return arbitrator != null && arbitrator.getLanguageCodes() != null ?
                formatter.languageCodesToString(arbitrator.getLanguageCodes()) : "";
    }

    public String getRegistrationDate() {
        return arbitrator != null ? formatter.formatDate(new Date(arbitrator.getRegistrationDate())) : "";
    }

    public boolean getIsSelected() {
        return isSelected.get();
    }

    public BooleanProperty isSelectedProperty() {
        return isSelected;
    }

    public void setIsSelected(boolean isSelected) {
        this.isSelected.set(isSelected);
    }
}
