package monero.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import common.utils.JsonUtils;
import monero.rpc.MoneroRpcConnection;

/**
 * Collection of utilties bridged from Monero Core C++ to Java.
 */
public class MoneroCppUtils {
  
  static {
    System.loadLibrary("monero-java");
  }

  public static byte[] mapToBinary(Map<String, Object> map) {
    return jsonToBinaryJni(JsonUtils.serialize(map));
  }
  
  public static Map<String, Object> binaryToMap(byte[] bin) {
    return JsonUtils.deserialize(binaryToJsonJni(bin), new TypeReference<Map<String, Object>>(){});
  }
  
  @SuppressWarnings("unchecked")
  public static Map<String, Object> binaryBlocksToMap(byte[] binBlocks) {
    
    // convert binary blocks to json then to map
    Map<String, Object> map = JsonUtils.deserialize(MoneroRpcConnection.MAPPER, binaryBlocksToJsonJni(binBlocks), new TypeReference<Map<String, Object>>(){});
    
    // parse blocks to maps
    List<Map<String, Object>> blockMaps = new ArrayList<Map<String, Object>>();
    for (String blockStr : (List<String>) map.get("blocks")) {
      blockMaps.add(JsonUtils.deserialize(MoneroRpcConnection.MAPPER, blockStr, new TypeReference<Map<String, Object>>(){}));
    }
    map.put("blocks", blockMaps); // overwrite block strings
    
    // parse txs to maps, one array of txs per block
    List<List<Map<String, Object>>> allTxs = new ArrayList<List<Map<String, Object>>>();
    List<Object> rpcAllTxs = (List<Object>) map.get("txs");
    for (Object rpcTxs : rpcAllTxs) {
      if ("".equals(rpcTxs)) {
        allTxs.add(new ArrayList<Map<String, Object>>());
      } else {
        List<Map<String, Object>> txs = new ArrayList<Map<String, Object>>();
        allTxs.add(txs);
        for (String rpcTx : (List<String>) rpcTxs) {
          txs.add(JsonUtils.deserialize(MoneroRpcConnection.MAPPER, rpcTx.replaceFirst(",", "{") + "}", new TypeReference<Map<String, Object>>(){})); // modify tx string to proper json and parse // TODO: more efficient way than this json manipulation?
        }
      }
    }
    map.put("txs", allTxs); // overwrite tx strings

    // return map containing blocks and txs as maps
    return map;
  }
  
  public static void initLogging(String path, int level, boolean console) {
    initLoggingJni(path, console);
    setLogLevelJni(level);
  }
  
  public static void setLogLevel(int level) {
    setLogLevelJni(level);
  }
  
  // ------------------------------- NATIVE METHODS ---------------------------
  
  private native static byte[] jsonToBinaryJni(String json);
  
  private native static String binaryToJsonJni(byte[] bin);
  
  private native static String binaryBlocksToJsonJni(byte[] binBlocks);
  
  private native static void initLoggingJni(String path, boolean console);

  private native static void setLogLevelJni(int level);
}
