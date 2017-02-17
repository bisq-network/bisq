/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.settings.about;

import io.bitsquare.app.Version;
import io.bitsquare.gui.common.model.Activatable;
import io.bitsquare.gui.common.view.ActivatableViewAndModel;
import io.bitsquare.gui.common.view.FxmlView;
import io.bitsquare.gui.components.HyperlinkWithIcon;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.util.Layout;
import javafx.geometry.HPos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import javax.inject.Inject;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class AboutView extends ActivatableViewAndModel<GridPane, Activatable> {

    private int gridRow = 0;


    @Inject
    public AboutView() {
        super();
    }

    public void initialize() {
        TitledGroupBg titledGroupBg = addTitledGroupBg(root, gridRow, 4, "About Bitsquare");
        GridPane.setColumnSpan(titledGroupBg, 2);
        Label label = addLabel(root, gridRow, "Bitsquare is an open source project and a decentralized network of users who want to " +
                "exchange Bitcoin with national currencies or alternative crypto currencies in a privacy protecting way. " +
                "Learn more about Bitsquare on our project web page.", Layout.FIRST_ROW_DISTANCE);
        label.setWrapText(true);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        HyperlinkWithIcon hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, "Bitsquare web page", "https://bitsquare.io");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, "Source code", "https://github.com/bitsquare/bitsquare");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, "AGPL License", "https://github.com/bitsquare/bitsquare/blob/master/LICENSE");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);

        titledGroupBg = addTitledGroupBg(root, ++gridRow, 3, "Support Bitsquare", Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 2);
        label = addLabel(root, gridRow, "Bitsquare is not a company but a community project and open for participation. " +
                "If you want to participate or support Bitsquare please follow the links below.", Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        label.setWrapText(true);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, "Contribute", "https://bitsquare.io/contribute");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, "Donate", "https://bitsquare.io/contribute/#donation");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);

        titledGroupBg = addTitledGroupBg(root, ++gridRow, 3, "Data providers", Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 2);
        label = addLabel(root, gridRow, "Bitsquare uses 3rd party APIs for Fiat and Altcoin market prices as well as for mining fee estimation.", Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        label.setWrapText(true);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        addLabelTextField(root, ++gridRow, "Market prices provided by: ", "BitcoinAverage (https://bitcoinaverage.com), Poloniex (https://poloniex.com) and Coinmarketcap (https://coinmarketcap.com)");
        addLabelTextField(root, ++gridRow, "Mining fee estimation provided by: 21 (https://bitcoinfees.21.co)");

        titledGroupBg = addTitledGroupBg(root, ++gridRow, 2, "Version details", Layout.GROUP_DISTANCE);
        GridPane.setColumnSpan(titledGroupBg, 2);
        addLabelTextField(root, gridRow, "Application version:", Version.VERSION, Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(root, ++gridRow, "Versions of subsystems:",
                "Network version: " + Version.P2P_NETWORK_VERSION +
                        "; P2P message version: " + Version.getP2PMessageVersion() +
                        "; Local DB version: " + Version.LOCAL_DB_VERSION +
                        "; Trade protocol version: " + Version.TRADE_PROTOCOL_VERSION);
    }

    @Override
    public void activate() {
    }

    @Override
    public void deactivate() {
    }

}

