package bisq.core.xmr.jsonrpc;

import java.math.BigInteger;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Maintains a connection and sends requests to a Monero RPC API.
 */
public class MoneroRpcConnection {

  // logger
  private static final Logger LOGGER = LoggerFactory.getLogger(MoneroRpcConnection.class);

  // custom mapper to deserialize integers to BigIntegers
  public static ObjectMapper MAPPER;
  static {
    MAPPER = new ObjectMapper();
    MAPPER.setSerializationInclusion(Include.NON_NULL);
    MAPPER.configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);
  }

  // instance variables
  private String uri;
  private HttpClient client;
  private String username;
  private String password;
  
  public MoneroRpcConnection(URI uri) {
    this(uri, null, null);
  }
  
  public MoneroRpcConnection(String uri) {
    this(uri, null, null);
  }
  
  public MoneroRpcConnection(String uri, String username, String password) {
    this((URI) (uri == null ? null : MoneroUtils.parseUri(uri)), username, password);
  }
  
  public MoneroRpcConnection(URI uri, String username, String password) {
    if (uri == null) throw new MoneroException("Must provide URI of RPC endpoint");
    this.uri = uri.toString();
    this.username = username;
    this.password = password;
    if (username != null || password != null) {
      CredentialsProvider creds = new BasicCredentialsProvider();
      creds.setCredentials(new AuthScope(uri.getHost(), uri.getPort()), new UsernamePasswordCredentials(username, password));
      this.client = HttpClients.custom().setDefaultCredentialsProvider(creds).build();
    } else {
      this.client = HttpClients.createDefault();
    }
  }
  
  public String getUri() {
    return uri;
  }
  
  public String getUsername() {
    return username;
  }
  
  public String getPassword() {
    return password;
  }
  
  /**
   * Sends a request to the RPC API.
   * 
   * @param method specifies the method to request
   * @return the RPC API response as a map
   */
  public Map<String, Object> sendJsonRequest(String method) {
    return sendJsonRequest(method, (Map<String, Object>) null);
  }
  
  /**
   * Sends a request to the RPC API.
   * 
   * @param method specifies the method to request
   * @param params specifies input parameters (Map<String, Object>, List<Object>, String, etc)
   * @return the RPC API response as a map
   */
  public Map<String, Object> sendJsonRequest(String method, Object params) {
    try {

      // build request body
      Map<String, Object> body = new HashMap<String, Object>();
      body.put("jsonrpc", "2.0");
      body.put("id", "0");
      body.put("method", method);
      if (params != null) body.put("params", params);
      LOGGER.debug("Sending json request with method '" + method + "' and body: " + JsonUtils.serialize(body));

      // send http request and validate response
      HttpPost post = new HttpPost(uri.toString() + "/json_rpc");
      HttpEntity entity = new StringEntity(JsonUtils.serialize(body));
      post.setEntity(entity);
      HttpResponse resp = client.execute(post);
      validateHttpResponse(resp);

      // deserialize response
      Map<String, Object> respMap = JsonUtils.toMap(MAPPER, StreamUtils.streamToString(resp.getEntity().getContent()));
      LOGGER.debug("Received response to method '" + method + "': " + JsonUtils.serialize(respMap));
      EntityUtils.consume(resp.getEntity());

      // check RPC response for errors
      validateRpcResponse(respMap, method, params);
      return respMap;
    } catch (HttpException e1) {
      throw e1;
    } catch (MoneroRpcException e2) {
      throw e2;
    } catch (Exception e3) {
      //e3.printStackTrace();
      throw new MoneroException(e3);
    }
  }
  
  /**
   * Sends a RPC request to the given path and with the given paramters.
   * 
   * E.g. "/get_transactions" with params
   * 
   * @param path is the url path of the request to invoke
   * @return the request's deserialized response
   */
  public Map<String, Object>sendPathRequest(String path) {
    return sendPathRequest(path, null);
  }
  
  /**
   * Sends a RPC request to the given path and with the given paramters.
   * 
   * E.g. "/get_transactions" with params
   * 
   * @param path is the url path of the request to invoke
   * @param params are request parameters sent in the body
   * @return the request's deserialized response
   */
  public Map<String, Object> sendPathRequest(String path, Map<String, Object> params) {
    //System.out.println("sendPathRequest(" + path + ", " + JsonUtils.serialize(params) + ")");
    
    try {
      
      // build request
      HttpPost post = new HttpPost(uri.toString() + "/" + path);
      if (params != null) {
        HttpEntity entity = new StringEntity(JsonUtils.serialize(params));
        post.setEntity(entity);
      }
      LOGGER.debug("Sending path request with path '" + path + "' and params: " + JsonUtils.serialize(params));
      
      // send request and validate response
      HttpResponse resp = client.execute(post);
      validateHttpResponse(resp);
      
      // deserialize response
      Map<String, Object> respMap = JsonUtils.toMap(MAPPER, StreamUtils.streamToString(resp.getEntity().getContent()));
      LOGGER.debug("Received response to path '" + path + "': " + JsonUtils.serialize(respMap));
      EntityUtils.consume(resp.getEntity());

      // check RPC response for errors
      validateRpcResponse(respMap, path, params);
      return respMap;
    } catch (HttpException e1) {
      throw e1;
    } catch (MoneroRpcException e2) {
      throw e2;
    } catch (Exception e3) {
      e3.printStackTrace();
      throw new MoneroException(e3);
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((password == null) ? 0 : password.hashCode());
    result = prime * result + ((uri == null) ? 0 : uri.hashCode());
    result = prime * result + ((username == null) ? 0 : username.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    MoneroRpcConnection other = (MoneroRpcConnection) obj;
    if (password == null) {
      if (other.password != null) return false;
    } else if (!password.equals(other.password)) return false;
    if (uri == null) {
      if (other.uri != null) return false;
    } else if (!uri.equals(other.uri)) return false;
    if (username == null) {
      if (other.username != null) return false;
    } else if (!username.equals(other.username)) return false;
    return true;
  }
  
  // ------------------------------ STATIC UTILITIES --------------------------

  private static void validateHttpResponse(HttpResponse resp) {
    int code = resp.getStatusLine().getStatusCode();
    if (code < 200 || code > 299) {
      String content = null;
      try {
        content = StreamUtils.streamToString(resp.getEntity().getContent());
      } catch (Exception e) {
        // could not get content
      }
      throw new HttpException(code, code + " " + resp.getStatusLine().getReasonPhrase() + (content == null || content.isEmpty() ? "" : (": " + content)));
    }
  }

  @SuppressWarnings("unchecked")
  private static void validateRpcResponse(Map<String, Object> respMap, String method, Object params) {
    Map<String, Object> error = (Map<String, Object>) respMap.get("error");
    if (error == null) return;
    String msg = (String) error.get("message");
    int code = ((BigInteger) error.get("code")).intValue();
    throw new MoneroRpcException(msg, code, method, params);
  }
}
