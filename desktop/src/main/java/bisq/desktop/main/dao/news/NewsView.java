package bisq.desktop.main.dao.news;

import bisq.desktop.common.view.ActivatableView;
import bisq.desktop.common.view.FxmlView;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.Layout;

import bisq.core.locale.Res;

import bisq.common.config.Config;
import bisq.common.config.ConfigFileEditor;

import javax.inject.Inject;

import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;

import static bisq.desktop.util.FormBuilder.*;

@FxmlView
public class NewsView extends ActivatableView<HBox, Void> {

    private final ConfigFileEditor configFileEditor;
    private ToggleButton daoActivatedToggleButton;

    @Inject
    private NewsView(Config config) {
        configFileEditor = new ConfigFileEditor(config.configFile);
    }

    @Override
    protected void initialize() {
        root.setSpacing(20);

        AnchorPane bisqDAOPane = createBisqDAOContent();
        HBox.setHgrow(bisqDAOPane, Priority.SOMETIMES);
        Separator separator = new Separator();
        separator.setOrientation(Orientation.VERTICAL);
        HBox.setHgrow(separator, Priority.NEVER);
        GridPane bisqDAOOnTestnetPane = createBisqDAOOnTestnetContent();
        HBox.setHgrow(bisqDAOOnTestnetPane, Priority.SOMETIMES);
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        root.getChildren().addAll(bisqDAOPane, separator, bisqDAOOnTestnetPane, spacer);
    }

    private GridPane createBisqDAOOnTestnetContent() {
        GridPane gridPane = new GridPane();
        gridPane.setMaxWidth(370);

        int rowIndex = 0;

        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, rowIndex, 14, Res.get("dao.news.daoInfo.title"));
        titledGroupBg.getStyleClass().addAll("last", "dao-news-titled-group");
        Label daoTestnetDescription = addMultilineLabel(gridPane, ++rowIndex, Res.get("dao.news.daoInfo.description"), 0, 370);
        GridPane.setMargin(daoTestnetDescription, new Insets(Layout.FLOATING_LABEL_DISTANCE, 0, 8, 0));
        daoTestnetDescription.getStyleClass().add("dao-news-content");

        rowIndex = addInfoSectionEnableDao(gridPane, rowIndex, Res.get("dao.news.daoInfo.firstSection.title"),
                Res.get("dao.news.daoInfo.firstSection.content"));
        rowIndex = addInfoSection(gridPane, rowIndex, Res.get("dao.news.DAOOnTestnet.secondSection.title"),
                Res.get("dao.news.DAOOnTestnet.secondSection.content"),
                "https://docs.bisq.network/getting-started-dao.html#acquire-some-bsq");
        rowIndex = addInfoSection(gridPane, rowIndex, Res.get("dao.news.DAOOnTestnet.thirdSection.title"),
                Res.get("dao.news.DAOOnTestnet.thirdSection.content"),
                "https://docs.bisq.network/getting-started-dao.html#participate-in-a-voting-cycle");
        rowIndex = addInfoSection(gridPane, rowIndex, Res.get("dao.news.DAOOnTestnet.fourthSection.title"),
                Res.get("dao.news.DAOOnTestnet.fourthSection.content"),
                "https://docs.bisq.network/getting-started-dao.html#explore-a-bsq-block-explorer");

        Hyperlink hyperlink = addHyperlinkWithIcon(gridPane, ++rowIndex, Res.get("dao.news.DAOOnTestnet.readMoreLink"),
                "https://bisq.network/docs/dao");
        hyperlink.getStyleClass().add("dao-news-link");

        return gridPane;
    }

    private int addInfoSection(GridPane gridPane, int rowIndex, String title, String content, String linkURL) {
        Label titleLabel = addLabel(gridPane, ++rowIndex, title);
        GridPane.setMargin(titleLabel, new Insets(6, 0, 0, 0));

        titleLabel.getStyleClass().add("dao-news-section-header");
        Label contentLabel = addMultilineLabel(gridPane, ++rowIndex, content, -Layout.FLOATING_LABEL_DISTANCE, 370);
        contentLabel.getStyleClass().add("dao-news-section-content");

        Hyperlink link = addHyperlinkWithIcon(gridPane, ++rowIndex, "Read More", linkURL);
        link.getStyleClass().add("dao-news-section-link");
        GridPane.setMargin(link, new Insets(0, 0, 29, 0));

        return rowIndex;
    }

    private int addInfoSectionEnableDao(GridPane gridPane, int rowIndex, String title, String content) {
        Label titleLabel = addLabel(gridPane, ++rowIndex, title);
        GridPane.setMargin(titleLabel, new Insets(6, 0, 0, 0));

        titleLabel.getStyleClass().add("dao-news-section-header");
        Label contentLabel = addMultilineLabel(gridPane, ++rowIndex, content, -Layout.FLOATING_LABEL_DISTANCE, 370);
        contentLabel.getStyleClass().add("dao-news-section-content");

        daoActivatedToggleButton = addSlideToggleButton(gridPane, ++rowIndex, Res.get("setting.preferences.dao.activated"));

        return rowIndex;
    }

    private AnchorPane createBisqDAOContent() {
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.setMinWidth(373);

        GridPane bisqDAOPane = new GridPane();
        AnchorPane.setTopAnchor(bisqDAOPane, 0d);
        bisqDAOPane.setVgap(5);
        bisqDAOPane.setMaxWidth(373);

        int rowIndex = 0;
        TitledGroupBg theBisqDaoTitledGroup = addTitledGroupBg(bisqDAOPane, rowIndex, 3, Res.get("dao.news.bisqDAO.title"));
        theBisqDaoTitledGroup.getStyleClass().addAll("last", "dao-news-titled-group");
        Label daoTeaserContent = addMultilineLabel(bisqDAOPane, ++rowIndex, Res.get("dao.news.bisqDAO.description"));
        daoTeaserContent.getStyleClass().add("dao-news-teaser");
        Hyperlink hyperlink = addHyperlinkWithIcon(bisqDAOPane, ++rowIndex, Res.get("dao.news.bisqDAO.readMoreLink"), "https://bisq.network/docs/dao");
        hyperlink.getStyleClass().add("dao-news-link");

        anchorPane.getChildren().add(bisqDAOPane);

        return anchorPane;
    }

    @Override
    protected void activate() {
        daoActivatedToggleButton.setSelected(false);
        daoActivatedToggleButton.setOnAction(e -> {
            configFileEditor.setOption("daoActivated", Boolean.toString(daoActivatedToggleButton.isSelected()));
            new Popup().information(Res.get("setting.preferences.dao.activated.popup")).useShutDownButton().show();
        });
    }

    @Override
    protected void deactivate() {
        daoActivatedToggleButton.setOnAction(null);
    }
}
