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

package bisq.desktop.main.community;

import bisq.desktop.common.view.FxmlView;
import bisq.desktop.common.view.InitializableView;
import bisq.desktop.common.view.ViewLoader;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.main.community.platform.Platform;
import bisq.desktop.main.community.platform.PlatformView;

import bisq.core.locale.Res;

import javax.inject.Inject;

import javafx.fxml.FXML;

import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@FxmlView
public class CommunityView extends InitializableView<ScrollPane, Void> {

    @FXML
    VBox content;

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
        addLabel(content, Res.get("community.join.title"), "community-heading-1");
        addLabel(content, Res.get("community.join.description.part1"));
        addLabel(content, Res.get("community.join.description.part2"));

        addSectionLabel(content, Res.get("community.section.getInTouch.title"));
        addLabel(content, Res.get("community.section.getInTouch.description"));
        addPlatformsBox(content, Arrays.asList("github", "keybase"), true);

        addSectionLabel(content, Res.get("community.section.ask.title"));
        addPlatformsBox(content, Arrays.asList("forum", "reddit"));

        addSectionLabel(content, Res.get("community.section.updates.title"));
        addPlatformsBox(content, Arrays.asList("twitter", "youtube", "mastadon"));
        addPlatformsBox(content, Arrays.asList("blog", "markets"));

        addSectionLabel(content, Res.get("community.section.chat.title"));
        addPlatformsBox(content, Arrays.asList("telegram", "irc", "matrix"));

        addSectionLabel(content, Res.get("community.section.getStarted.title"));
        addPlatformsBox(content, List.of("website"));

        addSectionLabel(content, Res.get("community.section.documentation.title"));
        addPlatformsBox(content, List.of("wiki"));
    }

    private void addPlatformsBox(VBox content, List<String> slugs, boolean wider) {
        HBox platformsBox = new HBox();
        platformsBox.setSpacing(20);
        slugs.forEach(slug -> addPlatform(platformsBox, PLATFORMS.get(slug), wider));
        content.getChildren().add(platformsBox);
    }

    private void addPlatformsBox(VBox content, List<String> slugs) {
        addPlatformsBox(content, slugs, false);
    }

    private void addPlatform(HBox box, Platform platform, boolean wider) {
        PlatformView platformView = (PlatformView) viewLoader.load(PlatformView.class);
        platformView.setData(platform, wider);
        box.getChildren().add(platformView.getRoot());
    }

    private void addLabel(Pane pane, String message, String styleClass) {
        AutoTooltipLabel label = new AutoTooltipLabel(message);
        if (styleClass != null) {
            label.getStyleClass().add(styleClass);
        }
        pane.getChildren().add(label);
    }

    private void addLabel(Pane pane, String message) {
        addLabel(pane, message, null);
    }

    private void addSectionLabel(Pane pane, String message) {
        addLabel(pane, message, "community-heading-2");
    }
}
