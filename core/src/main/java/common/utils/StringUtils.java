package common.utils;

/**
 * Collection of string utilities.
 */
public class StringUtils {
  
  /**
   * Returns the given number of tabs as a string.
   * 
   * @param numTabs is the number of tabs
   * @return String is the number of tabs as a string
   */
  public static String getTabs(int numTabs) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < numTabs; i++) sb.append("\t");
    return sb.toString();
  }
}
