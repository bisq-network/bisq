package bisq.desktop.main.community;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.InitializableView;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.main.community.platform.Platform;
import bisq.desktop.main.community.platform.PlatformView;

import bisq.core.locale.Res;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;

@FxmlView
public class CommunityView extends InitializableView<VBox, Void> {
    @FXML
    Label joinTitle,
            joinDescription1,
            joinDescription2,
            getInTouchTitle,
            getInTouchDescription,
            askTitle,
            updatesTitle,
            chatTitle,
            getStartedTitle,
            documentationTitle;

    @FXML
    HBox getInTouchBox, askBox, updatesBox1, updatesBox2, chatBox, getStartedBox, documentationBox;

    private final ViewLoader viewLoader;

    private final Map<String, Platform> PLATFORMS = new HashMap<>() {{
        put("github",
                new Platform(
                        Res.get("community.platform.github.title"),
                        Res.get("community.platform.github.description"),
                        "https://github.com/bisq-network",
                        "icon-github"
                )
        );
        put("keybase",
                new Platform(
                        Res.get("community.platform.keybase.title"),
                        Res.get("community.platform.keybase.description"),
                        "https://keybase.io/team/bisq",
                        "icon-keybase"
                )
        );
        put("forum",
                new Platform(
                        Res.get("community.platform.forum.title"),
                        Res.get("community.platform.forum.description"),
                        "https://bisq.community/",
                        "icon-forum"
                )
        );
        put("reddit",
                new Platform(
                        Res.get("community.platform.reddit.title"),
                        Res.get("community.platform.reddit.description"),
                        "https://reddit.com/r/bisq",
                        "icon-reddit"
                )
        );
        put("twitter",
                new Platform(
                        Res.get("community.platform.twitter.title"),
                        Res.get("community.platform.twitter.description"),
                        "https://twitter.com/bisq_network/",
                        "icon-twitter"
                )
        );
        put("youtube",
                new Platform(
                        Res.get("community.platform.youtube.title"),
                        Res.get("community.platform.youtube.description"),
                        "https://youtube.com/c/bisq-network",
                        "icon-youtube"
                )
        );
        put("mastadon",
                new Platform(
                        Res.get("community.platform.mastadon.title"),
                        Res.get("community.platform.mastadon.description"),
                        "https://bitcoinhackers.org/@bisq",
                        "icon-mastodon"
                )
        );
        put("blog",
                new Platform(
                        Res.get("community.platform.blog.title"),
                        Res.get("community.platform.blog.description"),
                        "https://bisq.network/blog"
                )
        );
        put("markets",
                new Platform(
                        Res.get("community.platform.markets.title"),
                        Res.get("community.platform.markets.description"),
                        "https://bisq.network/markets"
                )
        );
        put("telegram",
                new Platform(
                        Res.get("community.platform.telegram.title"),
                        Res.get("community.platform.telegram.description"),
                        "https://t.me/bisq_p2p",
                        "icon-telegram"
                )
        );
        put("irc",
                new Platform(
                        Res.get("community.platform.irc.title"),
                        Res.get("community.platform.irc.description"),
                        "https://webchat.freenode.net/?channels=bisq",
                        "icon-globe"
                )
        );
        put("matrix",
                new Platform(
                        Res.get("community.platform.matrix.title"),
                        Res.get("community.platform.matrix.description"),
                        "https://matrix.to/#/#freenode_#bisq:matrix.org",
                        "icon-matrix"
                )
        );
        put("website",
                new Platform(
                        Res.get("community.platform.website.title"),
                        Res.get("community.platform.website.description"),
                        "https://bisq.network/"
                )
        );
        put("wiki",
                new Platform(
                        Res.get("community.platform.wiki.title"),
                        Res.get("community.platform.wiki.description"),
                        "https://bisq.wiki/"
                )
        );
    }};

    @Inject
    public CommunityView(ViewLoader viewLoader) {
        this.viewLoader = viewLoader;
    }

    @Override
    public void initialize() {
        joinTitle.setText(Res.get("community.join.title"));
        joinDescription1.setText(Res.get("community.join.description.part1"));
        joinDescription2.setText(Res.get("community.join.description.part2"));

        getInTouchTitle.setText(Res.get("community.section.getInTouch.title"));
        getInTouchDescription.setText(Res.get("community.section.getInTouch.description"));
        addPlatform(getInTouchBox, PLATFORMS.get("github"), true);
        addPlatform(getInTouchBox, PLATFORMS.get("keybase"), true);

        askTitle.setText(Res.get("community.section.ask.title"));
        addPlatform(askBox, PLATFORMS.get("forum"), false);
        addPlatform(askBox, PLATFORMS.get("reddit"), false);

        updatesTitle.setText(Res.get("community.section.updates.title"));
        addPlatform(updatesBox1, PLATFORMS.get("twitter"), false);
        addPlatform(updatesBox1, PLATFORMS.get("youtube"), false);
        addPlatform(updatesBox1, PLATFORMS.get("mastadon"), false);
        addPlatform(updatesBox2, PLATFORMS.get("blog"), false);
        addPlatform(updatesBox2, PLATFORMS.get("markets"), false);

        chatTitle.setText(Res.get("community.section.chat.title"));
        addPlatform(chatBox, PLATFORMS.get("telegram"), false);
        addPlatform(chatBox, PLATFORMS.get("irc"), false);
        addPlatform(chatBox, PLATFORMS.get("matrix"), false);

        getStartedTitle.setText(Res.get("community.section.getStarted.title"));
        addPlatform(getStartedBox, PLATFORMS.get("website"), false);

        documentationTitle.setText(Res.get("community.section.documentation.title"));
        addPlatform(documentationBox, PLATFORMS.get("wiki"), false);
    }

    private void addPlatform(HBox box, Platform platform, boolean wider) {
        PlatformView platformView = (PlatformView) viewLoader.load(PlatformView.class);
        platformView.setData(platform, wider);
        box.getChildren().add(platformView.getRoot());
    }
}
