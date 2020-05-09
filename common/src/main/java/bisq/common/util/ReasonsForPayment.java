package bisq.common.util;

public class ReasonsForPayment {

    private static final String[] REASONS = {
            "Miss you",
            "Groceries",
            "Virtual coffee date!!!",
            "To help w/ your bills this month",
            "Thanks for everything you do",
            "Rent's due, dude",
            "Takeout > Going out",
            "Thank you, friend",
            "Pizza for din. And bfast too.",
            "You got this",
            "Dinner",
            "Treat yo self",
            "Grab a snack on me!"
    };

    public static String getReason(String id) {
        int hash = 7;
        for (char c : id.toCharArray()) {
            hash = hash * 31 + Character.getNumericValue(c);
        }

        return REASONS[hash % REASONS.length];
    }

}
