package com.simplaex.maven.waitfor;

import java.net.URL;

public class Check {

  URL url;

  int statusCode;

  HttpMethod method;

  String body;

  Header[] headers;

  public HttpMethod getMethod() {
    return method;
  }

  public void setMethod(final HttpMethod method) {
    this.method = method;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(final int statusCode) {
    this.statusCode = statusCode;
  }

  public URL getUrl() {
    return url;
  }

  public void setUrl(final URL url) {
    this.url = url;
  }

  public String getBody() {
    return body;
  }

  public void setBody(final String body) {
    this.body = body;
  }

  public Header[] getHeaders() {
    return headers;
  }

  public void setHeaders(final Header[] headers) {
    this.headers = headers;
  }
}
