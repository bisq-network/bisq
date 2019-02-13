package bisq.desktop.main.dao.news;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.BsqAddressTextField;
import bisq.desktop.main.presentation.DaoPresentation;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.locale.Res;
import bisq.core.user.Preferences;
import bisq.core.util.BsqFormatter;

import bisq.common.app.DevEnv;
import bisq.common.util.Tuple3;

import javax.inject.Inject;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import static bisq.desktop.util.FormBuilder.addLabelBsqAddressTextField;
import static bisq.desktop.util.FormBuilder.addTitledGroupBg;

@FxmlView
public class NewsView extends ActivatableView<GridPane, Void> {

    private final Preferences preferences;
    private final BsqWalletService bsqWalletService;
    private final BsqFormatter bsqFormatter;
    private BsqAddressTextField addressTextField;

    @Inject
    private NewsView(Preferences preferences, BsqWalletService bsqWalletService,
                     BsqFormatter bsqFormatter) {
        this.preferences = preferences;
        this.bsqWalletService = bsqWalletService;
        this.bsqFormatter = bsqFormatter;
    }

    @Override
    protected void initialize() {
        int gridRow = 0;

        addTitledGroupBg(root, gridRow, 6,
                Res.get("dao.wallet.receive.dao.headline"), 0);
        FormBuilder.addMultilineLabel(root, gridRow, Res.get("dao.wallet.receive.daoInfo"), 10);

        Button daoInfoButton = FormBuilder.addButton(root, ++gridRow, Res.get("dao.wallet.receive.daoInfo.button"));
        daoInfoButton.setOnAction(e -> {
            GUIUtil.openWebPage("https://bisq.network/dao");
        });

        FormBuilder.addMultilineLabel(root, ++gridRow, Res.get("dao.wallet.receive.daoTestnetInfo"));
        Button daoContributorInfoButton = FormBuilder.addButton(root, ++gridRow, Res.get("dao.wallet.receive.daoTestnetInfo.button"));
        daoContributorInfoButton.setOnAction(e -> {
            GUIUtil.openWebPage("https://bisq.network/dao-testnet");
        });

        FormBuilder.addMultilineLabel(root, ++gridRow, Res.get("dao.wallet.receive.daoContributorInfo"));

        Button daoTestnetInfoButton = FormBuilder.addButton(root, ++gridRow, Res.get("dao.wallet.receive.daoContributorInfo.button"));
        daoTestnetInfoButton.setOnAction(e -> {
            GUIUtil.openWebPage("https://bisq.network/dao-genesis");
        });

        addTitledGroupBg(root, ++gridRow, 1,
                Res.get("dao.wallet.receive.fundYourWallet"), 20);
        Tuple3<Label, BsqAddressTextField, VBox> tuple = addLabelBsqAddressTextField(root, gridRow,
                Res.get("dao.wallet.receive.bsqAddress"),
                40);
        addressTextField = tuple.second;
        GridPane.setColumnSpan(tuple.third, 3);
    }

    @Override
    protected void activate() {
        // Hide dao new badge if user saw this page
        if (!DevEnv.isDaoActivated())
            preferences.dontShowAgain(DaoPresentation.DAO_NEWS, true);

        addressTextField.setAddress(bsqFormatter.getBsqAddressStringFromAddress(bsqWalletService.getUnusedAddress()));
    }

    @Override
    protected void deactivate() {

    }
}
