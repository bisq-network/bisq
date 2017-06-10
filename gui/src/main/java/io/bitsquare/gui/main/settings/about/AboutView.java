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
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.util.Layout;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;

import javax.inject.Inject;

import static io.bitsquare.gui.util.FormBuilder.*;

@FxmlView
public class AboutView extends ActivatableViewAndModel<GridPane, Activatable> {
    @FXML
    GridPane root;
    @FXML
    Insets rootPadding;

    private int gridRow = 0;


    @Inject
    public AboutView() {
        super();
    }

    public void initialize() {
        root.setHgap(MainView.scale(5));
        root.setVgap(MainView.scale(5));
        AnchorPane.setTopAnchor(root, MainView.scale(0));
        AnchorPane.setRightAnchor(root, MainView.scale(0));
        AnchorPane.setBottomAnchor(root, MainView.scale(0));
        AnchorPane.setLeftAnchor(root, MainView.scale(0));
        rootPadding = new Insets(MainView.scale(30), MainView.scale(25), MainView.scale(10), MainView.scale(25));

        TitledGroupBg titledGroupBg = addTitledGroupBg(root, gridRow, 4, "About Bitsquare");
        GridPane.setColumnSpan(titledGroupBg, 2);
        Label label = addLabel(root, gridRow, "Bitsquare is an open source project and a decentralized network of users who want to " +
                "exchange Bitcoin with national currencies or alternative crypto currencies in a privacy protecting way. " +
                "Learn more about Bitsquare on our project web page.", MainView.scale(Layout.FIRST_ROW_DISTANCE));
        label.setWrapText(true);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        HyperlinkWithIcon hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, "Bitsquare web page", "https://bitsquare.io");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, "Source code", "https://github.com/bitsquare/bitsquare");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, "AGPL License", "https://github.com/bitsquare/bitsquare/blob/master/LICENSE");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);

        titledGroupBg = addTitledGroupBg(root, ++gridRow, 3, "Support Bitsquare", MainView.scale(Layout.GROUP_DISTANCE));
        GridPane.setColumnSpan(titledGroupBg, 2);
        label = addLabel(root, gridRow, "Bitsquare is not a company but a community project and open for participation. " +
                "If you want to participate or support Bitsquare please follow the links below.", MainView.scale(Layout.FIRST_ROW_AND_GROUP_DISTANCE));
        label.setWrapText(true);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, "Contribute", "https://bitsquare.io/contribute");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);
        hyperlinkWithIcon = addHyperlinkWithIcon(root, ++gridRow, "Donate", "https://bitsquare.io/contribute/#donation");
        GridPane.setColumnSpan(hyperlinkWithIcon, 2);

        titledGroupBg = addTitledGroupBg(root, ++gridRow, 3, "Market price API providers", MainView.scale(Layout.GROUP_DISTANCE));
        GridPane.setColumnSpan(titledGroupBg, 2);
        label = addLabel(root, gridRow, "Bitsquare uses market price feed providers for displaying the current exchange rate.", MainView.scale(Layout.FIRST_ROW_AND_GROUP_DISTANCE));
        label.setWrapText(true);
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);
        addLabelHyperlinkWithIcon(root, ++gridRow, "Market price API provider for fiat: ", "BitcoinAverage", "https://bitcoinaverage.com");
        label = addLabel(root, ++gridRow, "Market price API providers for altcoins: Poloniex (http://poloniex.com) / Coinmarketcap (https://coinmarketcap.com) as fallback");
        GridPane.setColumnSpan(label, 2);
        GridPane.setHalignment(label, HPos.LEFT);

        titledGroupBg = addTitledGroupBg(root, ++gridRow, 2, "Version details", MainView.scale(Layout.GROUP_DISTANCE));
        GridPane.setColumnSpan(titledGroupBg, 2);
        addLabelTextField(root, gridRow, "Application version:", Version.VERSION, MainView.scale(Layout.FIRST_ROW_AND_GROUP_DISTANCE));
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

