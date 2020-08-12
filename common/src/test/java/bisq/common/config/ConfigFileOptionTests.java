package bisq.common.config;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class ConfigFileOptionTests {

    @Test
    public void whenOptionHasWhitespaceAroundEqualsSign_thenItGetsTrimmed() {
        String value = "name1 = arg1";
        ConfigFileOption option = ConfigFileOption.parse(value);
        assertThat(option.name, equalTo("name1"));
        assertThat(option.arg, equalTo("arg1"));
        assertThat(option.toString(), equalTo("name1=arg1"));
    }

    @Test
    public void whenOptionHasLeadingOrTrailingWhitespace_thenItGetsTrimmed() {
        String value = "  name1=arg1   ";
        ConfigFileOption option = ConfigFileOption.parse(value);
        assertThat(option.name, equalTo("name1"));
        assertThat(option.arg, equalTo("arg1"));
        assertThat(option.toString(), equalTo("name1=arg1"));
    }

    @Test
    public void whenOptionHasEscapedColons_thenTheyGetUnescaped() {
        String value = "host1=example.com\\:8080";
        ConfigFileOption option = ConfigFileOption.parse(value);
        assertThat(option.name, equalTo("host1"));
        assertThat(option.arg, equalTo("example.com:8080"));
        assertThat(option.toString(), equalTo("host1=example.com:8080"));
    }

    @Test
    public void whenOptionHasNoValue_thenItSetsEmptyValue() {
        String value = "host1=";
        ConfigFileOption option = ConfigFileOption.parse(value);
        assertThat(option.name, equalTo("host1"));
        assertThat(option.arg, equalTo(""));
        assertThat(option.toString(), equalTo("host1="));
    }
}
