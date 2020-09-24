package com.simplaex.maven.waitfor;

import com.simplaex.bedrock.ArrayMap;
import com.simplaex.bedrock.Control;
import com.simplaex.bedrock.Pair;
import com.simplaex.bedrock.Seq;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Mojo(name = "waitfor", threadSafe = true)
public class WaitForMojo extends AbstractMojo {

  @Parameter
  Check[] checks;

  @Parameter(defaultValue = "30")
  int timeoutSeconds;

  @Parameter(defaultValue = "500")
  int checkEveryMillis;

  @Parameter(defaultValue = "false")
  boolean quiet;

  @Parameter(defaultValue = "false")
  boolean chatty;

  /**
   * Set this to "true" to bypass tests.
   */
  @Parameter( defaultValue = "false" )
  protected boolean skip;

  public Check[] getChecks() {
    return checks;
  }

  public void setChecks(final Check[] checks) {
    this.checks = checks;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public void setTimeoutSeconds(final int timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }

  private ArrayMap<Integer, Check> indexedChecks() {
    return ArrayMap.ofSeq(Seq.rangeExclusive(0, this.checks.length).zip(Seq.ofArray(this.checks)));
  }

  private int timeoutInMillis() {
    return timeoutSeconds * 1000;
  }

  private RequestConfig requestConfig() {
    final int timeoutInMillis = timeoutInMillis();
    return RequestConfig.custom()
      .setConnectionRequestTimeout(timeoutInMillis)
      .setSocketTimeout(timeoutInMillis)
      .setConnectTimeout(timeoutInMillis)
      .build();
  }

  private void info(final String message) {
    if (!quiet) {
      getLog().info("");
      getLog().info(">>> " + message);
      getLog().info("");
    }
  }

  private void warn(final String message) {
    if (!quiet) {
      getLog().warn(message);
    }
  }

  private void alwaysInfo(final String message) {
    getLog().info(message);
  }

  private void alwaysWarn(final String message) {
    getLog().warn(message);
  }

  private HttpUriRequest httpUriRequest(final Check check, final URI uri)
    throws MojoFailureException, UnsupportedEncodingException {

    switch (Optional.ofNullable(check.getMethod()).orElse(HttpMethod.GET)) {
      case HEAD:
        final HttpHead httpHead = new HttpHead(uri);
        httpHead.setConfig(requestConfig());
        return httpHead;
      case GET:
        final HttpGet httpGet = new HttpGet(uri);
        httpGet.setConfig(requestConfig());
        return httpGet;
      case POST:
        final HttpPost httpPost = new HttpPost(uri);
        httpPost.setEntity(new StringEntity(Optional.ofNullable(check.getRequestBody()).orElse("")));
        httpPost.setConfig(requestConfig());
        return httpPost;
      case PUT:
        final HttpPut httpPut = new HttpPut(uri);
        httpPut.setEntity(new StringEntity(Optional.ofNullable(check.getRequestBody()).orElse("")));
        httpPut.setConfig(requestConfig());
        return httpPut;
      default:
        throw new MojoFailureException("Unknown request method " + check.getMethod());
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.maven.plugin.AbstractMojo#execute()
   */
  public void execute() throws MojoFailureException {
    if (skip) {
      alwaysInfo( "Checks are skipped." );
      return;
    }

    if (this.checks == null || this.checks.length == 0) {
      alwaysWarn("No checks configured");
      return;
    }
    try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
      final ArrayMap<Integer, Check> checks = indexedChecks();
      final boolean[] results = new boolean[this.checks.length];
      final long startedAt = System.nanoTime();
      for (int i = 0; ; i += 1) {
        if (Seq.ofGenerator(ix -> results[ix], results.length).forAll(x -> x)) {
          alwaysInfo("All checks returned successfully.");
          break;
        } else {
          alwaysInfo("Not all checks passed. Trying again...");
        }
        final Duration elapsed = Duration.of(System.nanoTime() - startedAt, ChronoUnit.NANOS);
        if (elapsed.toMillis() > timeoutInMillis()) {
          throw new MojoFailureException("Timed out after " + elapsed.toMillis() + "ms");
        }
        if (i > 0) {
          Control.sleep(Duration.ofMillis(checkEveryMillis));
        }
        for (final Pair<Integer, Check> urlDefinition : checks) {
          final int index = urlDefinition.fst();
          final Check check = urlDefinition.snd();
          final int expectedStatusCode = check.getStatusCode() == 0 ? 200 : check.getStatusCode();
          final URI uri;
          try {
            uri = check.getUrl().toURI();
          } catch (final URISyntaxException exc) {
            throw new MojoFailureException("Invalid url " + check.getUrl() + " for url with index " + index, exc);
          }
          if (results[index]) {
            continue;
          }
          info("Checking " + uri + "...");
          final HttpUriRequest httpUriRequest = httpUriRequest(check, uri);
          for (final Header header : Optional.ofNullable(check.getHeaders()).orElse(new Header[0])) {
            httpUriRequest.setHeader(header.getName(), header.getValue());
          }
          try (final CloseableHttpResponse httpResponse = httpClient.execute(httpUriRequest)) {
            final int statusCode = httpResponse.getStatusLine().getStatusCode();
            final String response = EntityUtils.toString(httpResponse.getEntity());
            if (chatty) {
              getLog().info(uri + " responded with: " + response);
            }
            if (statusCode != expectedStatusCode) {
              info(uri + " returned " + statusCode + " instead of expected " + expectedStatusCode);
              continue;
            }
            final String expectedResponse = check.getExpectedResponseBody();
            if ((expectedResponse != null)
              && (!expectedResponse.equals(response))) {

              info(uri + " returned " + response + " instead of expected response " + expectedResponse);
              continue;
            }
            alwaysInfo(uri + " returned successfully (" + statusCode + ")");
            results[index] = true;
          } catch (final Exception exc) {
            warn(uri + " failed (" + exc.getClass().getName() + ": " + exc.getMessage() + ")");
          }
        }
      }
    } catch (final MojoFailureException exc) {
      throw exc;
    } catch (final Exception exc) {
      throw new MojoFailureException(exc.getMessage(), exc);
    }
  }
}
