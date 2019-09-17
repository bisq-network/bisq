package common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Collection of utilities for working with files.
 * 
 * @author woodser
 */
public class FileUtils {

  /**
   * Writes string data to the given path.
   * 
   * @param path is the path to write the data to
   * @param data is the string data to write
   * @throws IOException 
   */
  public static void write(String path, String data) {
    try {
      org.apache.commons.io.FileUtils.write(new File(path), data, Charset.defaultCharset());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
