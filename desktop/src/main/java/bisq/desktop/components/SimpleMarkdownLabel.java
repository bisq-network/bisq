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

package bisq.desktop.components;

import bisq.desktop.util.GUIUtil;

import bisq.core.util.SimpleMarkdownParser;

import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;
import java.util.stream.Collectors;

public class SimpleMarkdownLabel extends TextFlow {

    public SimpleMarkdownLabel(String markdown) {
        super();
        getStyleClass().add("markdown-label");
        if (markdown != null) {
            updateContent(markdown);
        }
    }

    public void updateContent(String markdown) {
        List<Node> items = SimpleMarkdownParser
                .parse(markdown)
                .stream()
                .map(node -> {
                    if (node instanceof SimpleMarkdownParser.HyperlinkNode) {
                        var item = ((SimpleMarkdownParser.HyperlinkNode) node);
                        Hyperlink hyperlink = new Hyperlink(item.getText());
                        hyperlink.setOnAction(e -> GUIUtil.openWebPage(item.getHref()));
                        return hyperlink;
                    } else  {
                        var item = ((SimpleMarkdownParser.TextNode) node);
                        return new Text(item.getText());
                    }
                })
                .collect(Collectors.toList());

        getChildren().setAll(items);
    }
}
