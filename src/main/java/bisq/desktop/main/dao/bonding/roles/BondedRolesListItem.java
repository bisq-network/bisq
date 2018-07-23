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

import bisq.desktop.components.AutoTooltipButton;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.role.BondedRole;
import bisq.core.locale.Res;
import bisq.core.util.BsqFormatter;

import java.util.Date;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@Data
class BondedRolesListItem {
    @Getter
    private final BondedRole bondedRole;
    private final DaoFacade daoFacade;
    private final BsqFormatter bsqFormatter;
    private final AutoTooltipButton button;

    BondedRolesListItem(BondedRole bondedRole,
                        DaoFacade daoFacade,
                        BsqFormatter bsqFormatter) {
        this.bondedRole = bondedRole;
        this.daoFacade = daoFacade;
        this.bsqFormatter = bsqFormatter;


        button = new AutoTooltipButton();
        button.setMinWidth(70);
        button.setText(Res.get("dao.bond.table.revoke"));
        button.setVisible(true);
        button.setManaged(true);
    }

    public String getStartDate() {
        return bondedRole.getStartDate() > 0 ?
                bsqFormatter.formatDateTime(new Date(bondedRole.getStartDate())) :
                "-";
    }

    public String getRevokeDate() {
        return bondedRole.getRevokeDate() > 0 ?
                bsqFormatter.formatDateTime(new Date(bondedRole.getRevokeDate())) :
                "-";
    }

    public static void cleanup() {

    }
}
