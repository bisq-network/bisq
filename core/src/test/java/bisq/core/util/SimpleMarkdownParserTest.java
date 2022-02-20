package bisq.core.util;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SimpleMarkdownParserTest {

    @Test
    public void testParse() {
        String text = "Take a look at the trade process" +
                " [here](https://docs.bisq.network/getting-started.html#4-send-payment)." +
                " \n\nIf you have any problems you can try to contact the trade peer in the trade chat.";

        List<? extends SimpleMarkdownParser.MarkdownNode> result = SimpleMarkdownParser.parse(text);

        assertEquals(3, result.size());

        SimpleMarkdownParser.TextNode item0 = (SimpleMarkdownParser.TextNode) result.get(0);
        assertEquals("Take a look at the trade process ", item0.getText());

        SimpleMarkdownParser.HyperlinkNode item1 = (SimpleMarkdownParser.HyperlinkNode) result.get(1);
        assertEquals(item1.getText(), "here");
        assertEquals(item1.getHref(), "https://docs.bisq.network/getting-started.html#4-send-payment");

        SimpleMarkdownParser.TextNode item2 = (SimpleMarkdownParser.TextNode) result.get(2);
        assertEquals(". \n\nIf you have any problems you can try to contact the trade peer in the trade chat.", item2.getText());
    }

    @Test
    public void testParseWithBrackets() {
        String text = "Take a look (here) for more";

        List<? extends SimpleMarkdownParser.MarkdownNode> result = SimpleMarkdownParser.parse(text);

        assertEquals(1, result.size());

        SimpleMarkdownParser.TextNode item0 = (SimpleMarkdownParser.TextNode) result.get(0);
        assertEquals("Take a look (here) for more", item0.getText());
    }
}
