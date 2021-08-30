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

package bisq.desktop.main.overlays.windows;

import bisq.desktop.main.overlays.Overlay;
import bisq.desktop.util.DisplayUtils;
import bisq.desktop.util.Layout;

import bisq.core.locale.CountryUtil;
import bisq.core.locale.Res;
import bisq.core.payment.payload.SwiftAccountPayload;
import bisq.core.trade.Trade;

import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;

import javafx.geometry.Insets;

import static bisq.common.util.Utilities.cleanString;
import static bisq.desktop.util.FormBuilder.*;
import static bisq.core.payment.payload.SwiftAccountPayload.*;

public class SwiftPaymentDetails extends Overlay<SwiftPaymentDetails> {
    private SwiftAccountPayload payload;
    private Trade trade;

    public SwiftPaymentDetails(SwiftAccountPayload swiftAccountPayload, Trade trade) {
        this.payload = swiftAccountPayload;
        this.trade = trade;
    }

    public void show() {
        rowIndex = -1;
        width = 918;
        createGridPane();
        addContent();
        display();
    }

    @Override
    protected void cleanup() {
    }

    @Override
    protected void createGridPane() {
        super.createGridPane();
        gridPane.setPadding(new Insets(35, 40, 30, 40));
        gridPane.getStyleClass().add("grid-pane");
    }

    private void addContent() {
        int rows = 20;
        addTitledGroupBg(gridPane, ++rowIndex, rows, Res.get("payment.swift.headline"));

        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("portfolio.pending.step2_buyer.amountToTransfer"),
                DisplayUtils.formatVolumeWithCode(trade.getTradeVolume()), Layout.TWICE_FIRST_ROW_DISTANCE);
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get(SWIFT_CODE + BANKPOSTFIX), payload.getBankSwiftCode());
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get(SNAME + BANKPOSTFIX), payload.getBankName());
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get(BRANCH + BANKPOSTFIX), payload.getBankBranch());
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get(ADDRESS + BANKPOSTFIX), cleanString(payload.getBankAddress()));
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get(COUNTRY + BANKPOSTFIX), CountryUtil.getNameAndCode(payload.getBankCountryCode()));

        if (payload.usesIntermediaryBank()) {
            addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get(SWIFT_CODE + INTERMEDIARYPOSTFIX), payload.getIntermediarySwiftCode(), Layout.GROUP_DISTANCE_WITHOUT_SEPARATOR);
            addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get(SNAME + INTERMEDIARYPOSTFIX), payload.getIntermediaryName());
            addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get(BRANCH + INTERMEDIARYPOSTFIX), payload.getIntermediaryBranch());
            addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get(ADDRESS + INTERMEDIARYPOSTFIX), cleanString(payload.getIntermediaryAddress()));
            addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get(COUNTRY + INTERMEDIARYPOSTFIX), CountryUtil.getNameAndCode(payload.getIntermediaryCountryCode()));
        }

        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("payment.account.owner"), payload.getBeneficiaryName(), Layout.GROUP_DISTANCE_WITHOUT_SEPARATOR);
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get(SWIFT_ACCOUNT), payload.getBeneficiaryAccountNr());
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get(ADDRESS + BENEFICIARYPOSTFIX), cleanString(payload.getBeneficiaryAddress()));
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get(PHONE + BENEFICIARYPOSTFIX), payload.getBeneficiaryPhone());
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("payment.account.city"), payload.getBeneficiaryCity());
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("payment.country"), CountryUtil.getNameAndCode(payload.getBankCountryCode()));
        addConfirmationLabelLabel(gridPane, ++rowIndex, Res.get("payment.shared.extraInfo"), cleanString(payload.getSpecialInstructions()));

        Button closeButton = addButton(gridPane, ++rowIndex, Res.get("shared.close"));
        closeButton.setMaxWidth(Region.USE_COMPUTED_SIZE);
        GridPane.setColumnIndex(closeButton, 2);
        closeButton.setOnAction(e -> {
            closeHandlerOptional.ifPresent(Runnable::run);
            hide();
        });
    }
}
