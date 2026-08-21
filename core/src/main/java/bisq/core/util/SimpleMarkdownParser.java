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

package bisq.core.util;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class SimpleMarkdownParser {
    private enum MarkdownParsingState {
        TEXT,
        LINK_TEXT,
        LINK_HREF
    }

    // Simple parser without correctness validation, currently supports only links
    public static List<? extends MarkdownNode> parse(String markdown) {
        List<MarkdownNode> items = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();

        MarkdownParsingState state = MarkdownParsingState.TEXT;

        for (int i = 0; i < markdown.length(); i++) {
            char c = markdown.charAt(i);
            if (c == '[') {
                if (sb.length() > 0) {
                    items.add(new TextNode(sb.toString()));
                    sb = new StringBuilder();
                }
                state = MarkdownParsingState.LINK_TEXT;
            } else if (c == '(' && state == MarkdownParsingState.LINK_TEXT) {
                state = MarkdownParsingState.LINK_HREF;
            } else if (c == ')' && state == MarkdownParsingState.LINK_HREF) {
                state = MarkdownParsingState.TEXT;
                items.add(new HyperlinkNode(sb.toString(), sb2.toString()));
                sb = new StringBuilder();
                sb2 = new StringBuilder();
            } else if (c != ']') {
                if (state == MarkdownParsingState.LINK_HREF) {
                    sb2.append(c);
                } else {
                    sb.append(c);
                }
            }
        }
        if (sb.length() > 0) {
            items.add(new TextNode(sb.toString()));
        }
        return items;
    }

    public static class MarkdownNode {}

    @AllArgsConstructor
    public static class HyperlinkNode extends MarkdownNode {
        @Getter private final String text;
        @Getter private final String href;
    }

    @AllArgsConstructor
    public static class TextNode extends MarkdownNode {
        @Getter private final String text;
    }
}
