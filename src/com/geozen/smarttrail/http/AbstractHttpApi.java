/**
 * Adapted from code from foursquare.
 *  
 * GeoZen LLC
 * Copyright 2011. All rights reserved.
 */
package com.geozen.smarttrail.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import com.geozen.smarttrail.error.SmartTrailException;
import com.geozen.smarttrail.error.CredentialsException;
import com.geozen.smarttrail.util.AppLog;

abstract public class AbstractHttpApi implements HttpApi {


	private static final String DEFAULT_CLIENT_VERSION = "com.geozen";
	private static final String CLIENT_VERSION_HEADER = "User-Agent";
	private static final int TIMEOUT = 10;
	static final int HTTP_PORT = 80;
	static final int SSL_PORT = 443;

	private final DefaultHttpClient mHttpClient;
	private final String mClientVersion;

	public AbstractHttpApi(DefaultHttpClient httpClient, String clientVersion) {
		mHttpClient = httpClient;
		if (clientVersion != null) {
			mClientVersion = clientVersion;
		} else {
			mClientVersion = DEFAULT_CLIENT_VERSION;
		}
	}

	public String executeRequest(HttpRequestBase httpRequest)
			throws CredentialsException, IOException, SmartTrailException {
		AppLog.d("doHttpRequest: " + httpRequest.getURI());

		HttpResponse response = executeHttpRequest(httpRequest);
		AppLog.d("executed HttpRequest for: "
					+ httpRequest.getURI().toString());

		int statusCode = response.getStatusLine().getStatusCode();
		switch (statusCode) {
		case 200:

			return EntityUtils.toString(response.getEntity());

		case 400:
			AppLog.d("HTTP Code: 400");
			throw new SmartTrailException(response.getStatusLine().toString(),
					EntityUtils.toString(response.getEntity()));

		case 401:
			response.getEntity().consumeContent();
			AppLog.d("HTTP Code: 401");
			throw new CredentialsException(response.getStatusLine().toString());

		case 404:
			response.getEntity().consumeContent();
			AppLog.d("HTTP Code: 404");
			throw new SmartTrailException(response.getStatusLine().toString(),
					httpRequest.getURI().toString());

		case 500:
			response.getEntity().consumeContent();
			AppLog.d("HTTP Code: 500");
			throw new SmartTrailException(response.getStatusLine().toString(),
					httpRequest.getURI().toString());

		default:
			AppLog.d("Default case for status code reached: "
						+ response.getStatusLine().toString());
			response.getEntity().consumeContent();
			throw new SmartTrailException("Error connecting to GeoZen: "
					+ statusCode + ". Try again later.");
		}
	}

	public String doHttpPost(String url, NameValuePair... nameValuePairs)
			throws CredentialsException, SmartTrailException, IOException {
		AppLog.d("doHttpPost: " + url);
		HttpPost httpPost = createHttpPost(url, nameValuePairs);

		HttpResponse response = executeHttpRequest(httpPost);
		AppLog.d("executed HttpRequest for: "
					+ httpPost.getURI().toString());

		switch (response.getStatusLine().getStatusCode()) {
		case 200:

			return EntityUtils.toString(response.getEntity());

		case 401:
			response.getEntity().consumeContent();
			throw new CredentialsException(response.getStatusLine().toString());

		case 404:
			response.getEntity().consumeContent();
			throw new SmartTrailException(response.getStatusLine().toString());

		default:
			response.getEntity().consumeContent();
			throw new SmartTrailException(response.getStatusLine().toString());
		}
	}

	/**
	 * execute() an httpRequest catching exceptions and returning null instead.
	 * 
	 * @param httpRequest
	 * @return
	 * @throws IOException
	 */
	public HttpResponse executeHttpRequest(HttpRequestBase httpRequest)
			throws IOException {
		AppLog.d("executing HttpRequest for: "
					+ httpRequest.getURI().toString());
		try {
			mHttpClient.getConnectionManager().closeExpiredConnections();
			return mHttpClient.execute(httpRequest);
		} catch (IOException e) {
			httpRequest.abort();
			throw e;
		}
	}

	public HttpGet createHttpGet(String url, NameValuePair... nameValuePairs) {
		AppLog.d("creating HttpGet for: " + url);
		String query = URLEncodedUtils.format(stripNulls(nameValuePairs),
				HTTP.UTF_8);
		HttpGet httpGet = new HttpGet(url + "?" + query);
		httpGet.addHeader(CLIENT_VERSION_HEADER, mClientVersion);
		AppLog.d("Created: " + httpGet.getURI());
		return httpGet;
	}

	public HttpPost createHttpPost(String url, NameValuePair... nameValuePairs) {
		AppLog.d("creating HttpPost for: " + url);
		HttpPost httpPost = new HttpPost(url);
		httpPost.addHeader(CLIENT_VERSION_HEADER, mClientVersion);
		try {
			httpPost.setEntity(new UrlEncodedFormEntity(
					stripNulls(nameValuePairs), HTTP.UTF_8));
		} catch (UnsupportedEncodingException e1) {
			throw new IllegalArgumentException(
					"Unable to encode http parameters.");
		}
		AppLog.d("Created: " + httpPost);
		return httpPost;
	}

	public HttpPost createHttpJsonPost(String url, String data) {
		AppLog.d("creating HttpPost for: " + url);
		HttpPost httpPost = new HttpPost(url);

		httpPost.setHeader("Accept", "application/json");
		httpPost.setHeader("Content-type", "application/json");
		httpPost.addHeader(CLIENT_VERSION_HEADER, mClientVersion);
		try {
			httpPost.setEntity(new StringEntity(data));
		} catch (UnsupportedEncodingException e1) {
			throw new IllegalArgumentException(
					"Unable to encode http parameters.");
		}
		AppLog.d("Created: " + httpPost);
		return httpPost;
	}

	public HttpDelete createHttpDelete(String url,
			NameValuePair... nameValuePairs) {
		AppLog.d("creating HttpDelete for: " + url);
		String query = URLEncodedUtils.format(stripNulls(nameValuePairs),
				HTTP.UTF_8);
		HttpDelete httpDelete = new HttpDelete(url + "?" + query);
		httpDelete.addHeader(CLIENT_VERSION_HEADER, mClientVersion);

		AppLog.d("Created: " + httpDelete);
		return httpDelete;
	}

	private List<NameValuePair> stripNulls(NameValuePair... nameValuePairs) {
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		for (int i = 0; i < nameValuePairs.length; i++) {
			NameValuePair param = nameValuePairs[i];
			if (param.getValue() != null) {
				AppLog.d("Param: " + param);
				params.add(param);
			}
		}
		return params;
	}

	/**
	 * Create a thread-safe client. This client does not do redirecting, to
	 * allow us to capture correct "error" codes.
	 * 
	 * @return HttpClient
	 */
	public static final DefaultHttpClient createHttpClient() {
		// Sets up the http part of the service.
		final SchemeRegistry schemeRegistry = new SchemeRegistry();

		// Register the "http" protocol scheme, it is required
		// by the default operator to look up socket factories.
		schemeRegistry.register(new Scheme("http", PlainSocketFactory
				.getSocketFactory(), HTTP_PORT));
		schemeRegistry.register(new Scheme("https", SSLSocketFactory
				.getSocketFactory(), SSL_PORT));

		// Set some client http client parameter defaults.
		final HttpParams httpParams = createHttpParams();
		HttpClientParams.setRedirecting(httpParams, false);

		final ClientConnectionManager ccm = new ThreadSafeClientConnManager(
				httpParams, schemeRegistry);
		return new DefaultHttpClient(ccm, httpParams);
	}

	/**
	 * Create the default HTTP protocol parameters.
	 */
	private static final HttpParams createHttpParams() {
		final HttpParams params = new BasicHttpParams();

		// Turn off stale checking. Our connections break all the time anyway,
		// and it's not worth it to pay the penalty of checking every time.
		HttpConnectionParams.setStaleCheckingEnabled(params, false);

		HttpConnectionParams.setConnectionTimeout(params, TIMEOUT * 1000);
		HttpConnectionParams.setSoTimeout(params, TIMEOUT * 1000);
		HttpConnectionParams.setSocketBufferSize(params, 8192);

		return params;
	}

}
