/*
 * Copyright 2011-2013 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.sina.cloudstorage.http;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map.Entry;

import com.sina.cloudstorage.ClientConfiguration;
import com.sina.cloudstorage.Request;
import com.sina.cloudstorage.util.HttpUtils;


/** Responsible for creating Apache HttpClient 4 request objects. */
class HttpRequestFactory {

    private static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * Creates an HttpClient method object based on the specified request and
     * populates any parameters, headers, etc. from the original request.
     *
     * @param request
     *            The request to convert to an HttpClient method object.
     * @param previousEntity
     *            The optional, previous HTTP entity to reuse in the new
     *            request.
     * @param context
     *            The execution context of the HTTP method to be executed
     *
     * @return The converted HttpClient method object with any parameters,
     *         headers, etc. from the original request set.
     * @throws IOException 
     */
    HttpURLConnection createHttpRequest(Request<?> request, ClientConfiguration clientConfiguration, 
    		ExecutionContext context, URI redirectedURI) throws IOException {
        URI endpoint = request.getEndpoint();
        
        /*
         * HttpClient cannot handle url in pattern of "http://host//path", so we
         * have to escape the double-slash between endpoint and resource-path
         * into "/%2F"
         */
    	String uri = HttpUtils.appendUri(endpoint.toString(), request.getResourcePath(), true);
        String encodedParams = HttpUtils.encodeParameters(request);

        /*
         * For all non-POST requests, and any POST requests that already have a
         * payload, we put the encoded params directly in the URI, otherwise,
         * we'll put them in the POST request's payload.
         */
        boolean requestHasNoPayload = request.getContent() != null;
        boolean requestIsPost = request.getHttpMethod() == HttpMethodName.POST;
        boolean putParamsInUri = !requestIsPost || requestHasNoPayload;
        if (encodedParams != null && putParamsInUri) {
            uri += "?" + encodedParams;
        }

     // Create connection
        HttpURLConnection connection = null;
        URL url = null;
		if (redirectedURI != null)
			url = new URL(redirectedURI.toString());
		else
			url = new URL(uri);
		connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod(request.getHttpMethod().toString());
		connection.setUseCaches(false);
		connection.setDoInput(true);
		connection.setDoOutput(true);
		
		configureHeaders(connection, request, context, clientConfiguration);
		
		// Send request
		if (request.getContent() != null){
			DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
			byte[] buffer = new byte[1024*8];
			int len = 0;
			while((len = request.getContent().read(buffer)) > 0){
				wr.write(buffer, 0, len);
			}
			wr.flush();
			wr.close();
		}
		
        return connection;
    }

    /** Configures the headers in the specified HttpURLConnection connection. */
    private void configureHeaders(HttpURLConnection connection, Request<?> request, ExecutionContext context, 
    		ClientConfiguration clientConfiguration) {
        URI endpoint = request.getEndpoint();
        String hostHeader = endpoint.getHost();
        if (HttpUtils.isUsingNonDefaultPort(endpoint)) {
            hostHeader += ":" + endpoint.getPort();
        }
        connection.setRequestProperty("Host", hostHeader);
        // Copy over any other headers already in our request
        for (Entry<String, String> entry : request.getHeaders().entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue().toString());
        }

        /* Set content type and encoding */
        if (connection.getRequestProperty("Content-Type") == null || 
        		connection.getRequestProperty("Content-Type").length() == 0) {
        	connection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded; " +
                    "charset=" + DEFAULT_ENCODING.toLowerCase());
        }

        // Override the user agent string specified in the client params if the context requires it
        if (context != null && context.getContextUserAgent() != null) {
        	connection.setRequestProperty("User-Agent", createUserAgentString(clientConfiguration, context.getContextUserAgent()));
        }
        
    }

    /** Appends the given user-agent string to the client's existing one and returns it. */
    private String createUserAgentString(ClientConfiguration clientConfiguration, String contextUserAgent) {
        if (clientConfiguration.getUserAgent().contains(contextUserAgent)) {
            return clientConfiguration.getUserAgent();
        } else {
            return clientConfiguration.getUserAgent() + " " + contextUserAgent;
        }
    }
}
