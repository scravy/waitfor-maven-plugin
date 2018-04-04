package com.simplaex.maven.waitfor;

import com.simplaex.bedrock.ArrayMap;
import com.simplaex.bedrock.Control;
import com.simplaex.bedrock.Pair;
import com.simplaex.bedrock.Seq;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Mojo(name = "waitfor")
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

  public Check[] getChecks() {
    return checks;
  }

  public void setChecks(Check[] checks) {
    this.checks = checks;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public void setTimeoutSeconds(int timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.apache.maven.plugin.AbstractMojo#execute()
   */
  public void execute() throws MojoFailureException {
    if (this.checks == null || this.checks.length == 0) {
      getLog().warn("No checks configured");
      return;
    }
    try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
      final ArrayMap<Integer, Check> urls =
        ArrayMap.ofSeq(Seq.rangeExclusive(0, this.checks.length).zip(Seq.ofArray(this.checks)));
      final int timeoutInMillis = timeoutSeconds * 1000;
      final RequestConfig requestConfig = RequestConfig.custom()
        .setConnectionRequestTimeout(timeoutInMillis)
        .setSocketTimeout(timeoutInMillis)
        .setConnectTimeout(timeoutInMillis)
        .build();
      final boolean[] results = new boolean[this.checks.length];
      final long startedAt = System.nanoTime();
      for (int i = 0; ; i += 1) {
        if (Seq.ofGenerator(ix -> results[ix], results.length).forAll(x -> x)) {
          getLog().info("All checks returned successfully.");
          break;
        }
        final Duration elapsed = Duration.of(System.nanoTime() - startedAt, ChronoUnit.NANOS);
        if (elapsed.toMillis() > timeoutInMillis) {
          throw new MojoFailureException("Timed out after " + elapsed.toMillis() + "ms");
        }
        if (i > 0) {
          Control.sleep(Duration.ofMillis(checkEveryMillis));
        }
        for (final Pair<Integer, Check> urlDefinition : urls) {
          final int index = urlDefinition.fst();
          final Check url = urlDefinition.snd();
          final int expectedStatusCode = url.getStatusCode() == 0 ? 200 : url.getStatusCode();
          final URI uri;
          try {
            uri = url.getUrl().toURI();
          } catch (final URISyntaxException exc) {
            throw new MojoFailureException("Invalid url " + url.getUrl() + " for url with index " + index, exc);
          }
          if (results[index]) {
            if (!quiet) {
              getLog().info("Checking " + uri + "...");
            }
            continue;
          }
          final HttpUriRequest httpUriRequest;
          switch (Optional.ofNullable(url.getMethod()).orElse(HttpMethod.GET)) {
            case GET:
              final HttpGet httpGet = new HttpGet(uri);
              httpGet.setConfig(requestConfig);
              httpUriRequest = httpGet;
              break;
            case POST:
              final HttpPost httpPost = new HttpPost(uri);
              httpPost.setEntity(new StringEntity(url.getBody()));
              httpPost.setConfig(requestConfig);
              httpUriRequest = httpPost;
              break;
            default:
              throw new MojoFailureException("Unknown request method " + url.getMethod());
          }
          for (final Header header : Optional.ofNullable(url.getHeaders()).orElse(new Header[0])) {
            httpUriRequest.setHeader(header.getName(), header.getValue());
          }
          try (final CloseableHttpResponse httpResponse = httpClient.execute(httpUriRequest)) {
            final int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (chatty) {
              getLog().info(uri + " responded with: " + EntityUtils.toString(httpResponse.getEntity()));
            } else {
              EntityUtils.consume(httpResponse.getEntity());
            }
            if (statusCode != expectedStatusCode) {
              if (!quiet) {
                getLog().info(uri + " returned " + statusCode + " instead of expected " + expectedStatusCode);
              }
              continue;
            }
            getLog().info(uri + " returned successfully (" + statusCode + ")");
            results[index] = true;
          } catch (final Exception exc) {
            if (!quiet) {
              getLog().info(uri + " failed (" + exc.getClass().getName() + ": " + exc.getMessage() + ")");
            }
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
