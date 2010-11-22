/**
 *
 */
package net.danopia.protonet.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

public class Fetcher {
	DefaultHttpClient httpclient;
	String baseURL;

	public Fetcher(String baseURL) {
	    SchemeRegistry registry = new SchemeRegistry();
	    registry.register(new Scheme("http", new PlainSocketFactory(), 80));
	    registry.register(new Scheme("https", new FakeSocketFactory(), 443));
        HttpParams httpparams = new BasicHttpParams();
	    httpclient = new DefaultHttpClient(new ThreadSafeClientConnManager(httpparams, registry), httpparams);

	    if (!baseURL.endsWith("/")) baseURL += "/";
	    this.baseURL = baseURL;
	}

	public String doLogin(String username, String password) {
	    /*String content = doGET("public/");

	    Pattern pattern = Pattern.compile(".*pskey = \"(.+)\";.*");
	    Matcher matcher = pattern.matcher(content);
	    matcher.find();
	    String key = matcher.group(1).toString();
	    String hash = MD5.hex_hmac_md5(key, password.toLowerCase());

	    pattern = Pattern.compile(".*<input type=\"hidden\" name=\"pstoken\" value=\"(.+?)\">.*");
	    matcher = pattern.matcher(content);
	    matcher.find();
	    String token = matcher.group(1).toString();*/

	    HttpPost httpost = new HttpPost(baseURL + "users/login");

	    List <NameValuePair> nvps = new ArrayList <NameValuePair>();
	    nvps.add(new BasicNameValuePair("user[login]", username));
	    nvps.add(new BasicNameValuePair("user[password]", password));

	    try {
		    httpost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));

		    HttpResponse response = httpclient.execute(httpost);
		    HttpEntity entity = response.getEntity();

		    String content = convertStreamToString(entity.getContent());
		    entity.consumeContent();

		    return content;

		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return "";
	}

	public String doGET(String url) throws Exception {
	    HttpGet httpget = new HttpGet(baseURL + url);

	    HttpResponse response = httpclient.execute(httpget);
	    HttpEntity entity = response.getEntity();

	    String content = convertStreamToString(entity.getContent());
	    entity.consumeContent();

	    return content;
	}

	public String convertStreamToString(InputStream is) throws IOException {
		/*
		 * To convert the InputStream to String we use the
		 * Reader.read(char[] buffer) method. We iterate until the
		 * Reader return -1 which means there's no more data to
		 * read. We use the StringWriter class to produce the string.
		 */
		if (is != null) {
			Writer writer = new StringWriter();

			char[] buffer = new char[1024];
			try {
				Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
				int n;
				while ((n = reader.read(buffer)) != -1) {
					writer.write(buffer, 0, n);
				}
			} finally {
				is.close();
			}
			return writer.toString();
		} else {
			return "";
		}
	}


    // When HttpClient instance is no longer needed,
    // shut down the connection manager to ensure
    // immediate deallocation of all system resources
	public void shutdown() {
	    httpclient.getConnectionManager().shutdown();
	}
}
