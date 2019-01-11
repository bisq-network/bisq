package bisq.httpapi;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

public class RegexMatcher extends TypeSafeMatcher<String> {

    private final String regex;

    private RegexMatcher(String regex) {
        this.regex = regex;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("matches regex=`" + regex + "`");
    }

    @Override
    public boolean matchesSafely(String string) {
        return string.matches(regex);
    }

    @SuppressWarnings("WeakerAccess")
    public static RegexMatcher matchesRegex(String regex) {
        return new RegexMatcher(regex);
    }
}
