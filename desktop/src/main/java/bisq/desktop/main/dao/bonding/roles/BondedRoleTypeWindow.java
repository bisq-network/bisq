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

package bisq.desktop.main.dao.bonding.roles;

import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.FormBuilder;

import bisq.core.dao.governance.role.BondedRoleType;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import org.bitcoinj.core.Coin;

import javafx.geometry.Insets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class BondedRoleTypeWindow extends Overlay<BondedRoleTypeWindow> {
    private final BondedRoleType bondedRoleType;
    private final BsqFormatter bsqFormatter;


    public BondedRoleTypeWindow(BondedRoleType bondedRoleType, BsqFormatter bsqFormatter) {
        this.bondedRoleType = bondedRoleType;
        this.bsqFormatter = bsqFormatter;

        width = 968;
        type = Type.Confirmation;

    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void show() {
        headLine = Res.get("dao.bond.bondedRoleType.details.header");

        createGridPane();
        addHeadLine();
        addContent();
        addCloseButton();
        applyStyles();
        display();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(35, 40, 30, 40));
        gridPane.getStyleClass().add("grid-pane");
    }

    private void addContent() {
        FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, Res.getWithCol("dao.bond.bondedRoleType.details.role"),
                bondedRoleType.getDisplayString());

        FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, Res.getWithCol("dao.bond.bondedRoleType.details.requiredBond"),
                bsqFormatter.formatCoinWithCode(Coin.valueOf(bondedRoleType.getRequiredBond())));

        FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, Res.getWithCol("dao.bond.bondedRoleType.details.unlockTime"),
                Res.get("dao.bond.bondedRoleType.details.blocks", bondedRoleType.getUnlockTime()));

        FormBuilder.addLabelHyperlinkWithIcon(gridPane, ++rowIndex, Res.getWithCol("dao.bond.bondedRoleType.details.link"),
                bondedRoleType.getLink(), bondedRoleType.getLink());

        FormBuilder.addTopLabelTextField(gridPane, ++rowIndex, Res.getWithCol("dao.bond.bondedRoleType.details.isSingleton"),
                bsqFormatter.booleanToYesNo(bondedRoleType.isAllowMultipleHolders()));
    }
}
