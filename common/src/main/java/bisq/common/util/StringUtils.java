package bisq.common.util;

public class StringUtils {

    public static String truncatePromptString(String str, int strMaxLen) {
        if(str.length() <= strMaxLen){
            return str;
        } else {
            return str.substring(0, strMaxLen - 3).concat("...");
        }

    }

}
