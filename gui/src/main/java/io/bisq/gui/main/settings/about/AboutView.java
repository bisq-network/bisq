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

package io.bisq.gui.main.settings.about;

import io.bisq.common.app.Version;
import io.bisq.common.locale.Res;
import io.bisq.gui.common.model.Activatable;
import io.bisq.gui.common.view.ActivatableViewAndModel;
import io.bisq.gui.common.view.FxmlView;
import io.bisq.gui.components.HyperlinkWithIcon;
import io.bisq.gui.components.TitledGroupBg;
import io.bisq.gui.util.Layout;
import javafx.geometry.HPos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import javax.inject.Inject;

import static io.bisq.gui.util.FormBuilder.*;

@FxmlView
public class AboutView extends ActivatableViewAndModel<GridPane, Activatable> {

    private int gridRow = 0;

    @Inject
    public AboutView() {
        super();
    }

    @Override
    public void initialize() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, gridRow, 4, Res.get("setting.about.aboutBisq"));
        GridPane.setColumnSpan(titledGroupBg, 2);
        Label label = addLabel(root, gridRow, Res.get("setting.about.about"), Layout.FIRST_ROW_DISTANCE);
        label.setWrapText(true);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        HyperlinkWithIcon hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, Res.get("setting.about.web"), "https://bisq.network");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, Res.get("setting.about.code"), "https://github.com/bisq-network/exchange");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, Res.get("setting.about.agpl"), "https://github.com/bisq-network/exchange/blob/master/LICENSE");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);

        titledGroupBg = addTitledGroupBg(root, ++gridRow, 3, Res.get("setting.about.support"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 2);
        label = addLabel(root, gridRow, Res.get("setting.about.def"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        label.setWrapText(true);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, Res.get("setting.about.contribute"), "https://bisq.network/contribute");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, Res.get("setting.about.donate"), "https://bisq.network/contribute/#donation");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);

        final boolean isBtc = Res.getBaseCurrencyCode().equals("BTC");
        titledGroupBg = addTitledGroupBg(root, ++gridRow, isBtc ? 3 : 2, Res.get("setting.about.providers"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 2);
        label = addLabel(root, gridRow, Res.get(isBtc ? "setting.about.apisWithFee" : "setting.about.apis"), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        label.setWrapText(true);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        addLabelTextField(root, ++gridRow, Res.get("setting.about.pricesProvided"), Res.get("setting.about.pricesProviders",
                "BitcoinAverage (https://bitcoinaverage.com)",
                "Poloniex (https://poloniex.com)",
                "Coinmarketcap (https://coinmarketcap.com)"));
        if (isBtc)
            addLabelTextField(root, ++gridRow, Res.get("setting.about.feeEstimation.label"), "21 (https://bitcoinfees.earn.com)");

        titledGroupBg = addTitledGroupBg(root, ++gridRow, 2, Res.get("setting.about.versionDetails"), Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 2);
        addLabelTextField(root, gridRow, Res.get("setting.about.version"), Version.VERSION, Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(root, ++gridRow,
                Res.get("setting.about.subsystems.label"),
                Res.get("setting.about.subsystems.val",
                        Version.P2P_NETWORK_VERSION,
                        Version.getP2PMessageVersion(),
                        Version.LOCAL_DB_VERSION,
                        Version.TRADE_PROTOCOL_VERSION));
    }

    @Override
    public void activate() {
    }

    @Override
    public void deactivate() {
    }

}

