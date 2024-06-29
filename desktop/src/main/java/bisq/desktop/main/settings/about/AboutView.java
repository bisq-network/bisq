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

package bisq.desktop.main.settings.about;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.util.Layout;

import bisq.core.locale.Res;

import bisq.common.app.Version;

import javax.inject.Inject;

import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import javafx.geometry.HPos;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addHyperlinkWithIcon;
import static bisq.desktop.util.FormBuilder.addLabel;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class AboutView extends ActivatableView<GridPane, Void> {

    private int gridRow = 0;

    @Inject
    public AboutView() {
        super();
    }

    @Override
    public void initialize() {
        addTitledGroupBg(root, gridRow, 4, Res.get("setting.about.aboutBisq"));

        Label label = addLabel(root, gridRow, Res.get("setting.about.about"), Layout.TWICE_FIRST_ROW_DISTANCE);
        label.setWrapText(true);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        HyperlinkWithIcon hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, Res.get("setting.about.web"), "https://bisq.network");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, Res.get("setting.about.code"), "https://bisq.network/source/bisq");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, Res.get("setting.about.agpl"), "https://bisq.network/source/bisq/blob/master/LICENSE");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);

        addTitledGroupBg(root, ++gridRow, 2, Res.get("setting.about.support"), Layout.GROUP_DISTANCE);

        label = addLabel(root, gridRow, Res.get("setting.about.def"), Layout.TWICE_FIRST_ROW_AND_GROUP_DISTANCE);
        label.setWrapText(true);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, Res.get("setting.about.contribute"), "https://bisq.network/contribute");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);

        boolean isBtc = Res.getBaseCurrencyCode().equals("BTC");
        addTitledGroupBg(root, ++gridRow, isBtc ? 3 : 2, Res.get("setting.about.providers"), Layout.GROUP_DISTANCE);

        label = addLabel(root, gridRow, Res.get(isBtc ? "setting.about.apisWithFee" : "setting.about.apis"), Layout.TWICE_FIRST_ROW_AND_GROUP_DISTANCE);
        label.setWrapText(true);
        GridPane.setHalignment(label, HPos.LEFT);
        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.pricesProvided"),
                "Bisq Price Index (https://bisq.wiki/Bisq_Price_Index)");
        if (isBtc)
            addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.feeEstimation.label"), "mempool.space (https://mempool.space)");

        addTitledGroupBg(root, ++gridRow, 2, Res.get("setting.about.versionDetails"), Layout.GROUP_DISTANCE);
        addCompactTopLabelTextField(root, gridRow, Res.get("setting.about.version"), Version.VERSION, Layout.TWICE_FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(root, ++gridRow,
                Res.get("setting.about.subsystems.label"),
                Res.get("setting.about.subsystems.val",
                        Version.P2P_NETWORK_VERSION,
                        Version.getP2PMessageVersion(),
                        Version.LOCAL_DB_VERSION,
                        Version.getTradeProtocolVersion()));

        addTitledGroupBg(root, ++gridRow, 18, Res.get("setting.about.shortcuts"), Layout.GROUP_DISTANCE);

        // basics
        addCompactTopLabelTextField(root, gridRow, Res.get("setting.about.shortcuts.menuNav"),
                Res.get("setting.about.shortcuts.menuNav.value"),
                Layout.TWICE_FIRST_ROW_AND_GROUP_DISTANCE);

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.close"),
                Res.get("setting.about.shortcuts.close.value", "q", "w"));

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.closePopup"),
                Res.get("setting.about.shortcuts.closePopup.value"));

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.chatSendMsg"),
                Res.get("setting.about.shortcuts.chatSendMsg.value"));

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.openDispute"),
                Res.get("setting.about.shortcuts.openDispute.value",
                        Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "o")));

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.walletDetails"),
                Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "j"));

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.openEmergencyBtcWalletTool"),
                Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "e"));

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.openEmergencyBsqWalletTool"),
                Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "b"));

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.showTorLogs"),
                Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "t"));

        // special
        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.removeStuckTrade"),
                Res.get("setting.about.shortcuts.removeStuckTrade.value",
                        Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "y")));

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.manualPayoutTxWindow"),
                Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "g"));

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.reRepublishAllGovernanceData"),
                Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "h"));

        // for arbitrators
        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.registerArbitrator"),
                Res.get("setting.about.shortcuts.registerArbitrator.value",
                        Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "n")));

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.registerMediator"),
                Res.get("setting.about.shortcuts.registerMediator.value",
                        Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "d")));

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.openSignPaymentAccountsWindow"),
                Res.get("setting.about.shortcuts.openSignPaymentAccountsWindow.value",
                        Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "s")));

        // only for maintainers
        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.sendAlertMsg"),
                Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "m"));

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.sendFilter"),
                Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "f"));

        addCompactTopLabelTextField(root, ++gridRow, Res.get("setting.about.shortcuts.sendPrivateNotification"),
                Res.get("setting.about.shortcuts.sendPrivateNotification.value",
                        Res.get("setting.about.shortcuts.ctrlOrAltOrCmd", "r")));

        // Not added:
        // allTradesWithReferralId, allOffersWithReferralId -> ReferralId is not used yet
        // revert tx -> not tested well, high risk
        // debug window -> not maintained, only for devs working on trade protocol relevant
    }

    @Override
    public void activate() {
    }

    @Override
    public void deactivate() {
    }

}

