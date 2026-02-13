package uk.gov.justice.laa.dstew.access.utils.builders;

import org.springframework.http.HttpHeaders;

public class HttpHeadersBuilder {
  private HttpHeaders httpHeaders = new HttpHeaders();

  public HttpHeadersBuilder withServiceName(String serviceName) {
    this.httpHeaders.add("x-ServiceName", serviceName);
    return this;
  }

  public HttpHeaders build() {
    return httpHeaders;
  }
}
