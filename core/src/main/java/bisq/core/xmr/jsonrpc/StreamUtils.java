package bisq.core.xmr.jsonrpc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

/**
 * Collection of utilities for working with streams.
 * 
 * @author woodser
 */
public class StreamUtils {

  /**
   * Converts an input stream to a byte array.
   * 
   * @param is is the input stream
   * @return byte[] are the contents of the input stream as a byte array
   * @throws IOException 
   */
  public static byte[] streamToBytes(InputStream is) throws IOException {
    byte[] bytes = IOUtils.toByteArray(is);
    is.close();
    return bytes;
  }
  
  /**
   * Converts a byte array to an input stream.
   * 
   * @param bytes is the byte[] to convert to an input stream
   * @return InputStream is the input stream initialized from the byte array
   */
  public static InputStream bytesToStream(byte[] bytes) {
    return new ByteArrayInputStream(bytes);
  }
  
  /**
   * Converts an input stream to a string.
   * 
   * @param is is the input stream to convert to a string
   * @return String is the input stream converted to a string
   * @throws IOException 
   */
  public static String streamToString(InputStream is) throws IOException {
    return new String(streamToBytes(is));
  }
}
