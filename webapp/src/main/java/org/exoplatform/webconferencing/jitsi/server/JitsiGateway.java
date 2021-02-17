/*
 * Copyright (C) 2003-2017 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.webconferencing.jitsi.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;

import org.exoplatform.common.http.HTTPMethods;
import org.exoplatform.container.web.AbstractHttpServlet;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.webconferencing.WebConferencingService;
import org.exoplatform.webconferencing.jitsi.JitsiProvider;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * The Class JitsiCallGateway.
 */
public class JitsiGateway extends AbstractHttpServlet {

  /**
   * The Enum Action.
   */
  private enum Action {

    /** The external auth. */
    EXTERNAL_AUTH,
    /** The internal auth. */
    INTERNAL_AUTH
  }

  /** The Constant serialVersionUID. */
  private static final long   serialVersionUID           = -6075521943684342671L;

  /** The Constant LOG. */
  protected static final Log  LOG                        = ExoLogger.getLogger(JitsiGateway.class);

  /** The Constant EXTERNAL_AUTH_TOKEN_HEADER. */
  private final static String EXTERNAL_AUTH_TOKEN_HEADER = "X-Exoplatform-External-Auth";

  /** The Constant INTERNAL_AUTH_TOKEN_HEADER. */
  private final static String AUTH_TOKEN_HEADER          = "X-Exoplatform-Auth";

  /** The Constant TRANSFER_ENCODING_HEADER. */
  private final static String TRANSFER_ENCODING_HEADER   = "Transfer-Encoding";

  /** The Constant APPLICATION_JSON. */
  private static final String APPLICATION_JSON           = "application/json";

  /** The Constant UTF_8. */
  private static final String UTF_8                      = "utf-8";

  /**
   * {@inheritDoc}
   */
  @Override
  protected void doGet(final HttpServletRequest httpRequest, final HttpServletResponse httpResponse) throws ServletException, IOException {
    WebConferencingService webconferencing =
                                           (WebConferencingService) getContainer().getComponentInstanceOfType(WebConferencingService.class);
    final JitsiProvider jitsiProvider = (JitsiProvider) webconferencing.getProvider(JitsiProvider.TYPE);
    final AsyncContext ctx = httpRequest.startAsync();
    ctx.start(new Runnable() {
      public void run() {
        HttpServletRequest req = (HttpServletRequest) ctx.getRequest();
        HttpServletResponse resp = (HttpServletResponse) ctx.getResponse();
        String uri = req.getRequestURI() + (req.getQueryString() != null ? "?" + req.getQueryString() : "");
        uri = uri.substring(uri.indexOf("/jitsi") + 6);
        if (req.getRequestURI().startsWith("/jitsi/portal/")) {
          String requestUrl = new StringBuilder(getPlatformUrl(req)).append(uri).toString();
          forward(requestUrl, Action.INTERNAL_AUTH, jitsiProvider.getInternalAuthSecret(), req, resp);
        } else if (req.getRequestURI().startsWith("/jitsi/resources")) {
          // TODO: separate the Gateway from local resources, make two .wars
          handleResourceRequest(uri, httpRequest, httpResponse);
        } else {
          String requestUrl = new StringBuilder(jitsiProvider.getServiceUrl()).append(uri).toString();
          forward(requestUrl, Action.EXTERNAL_AUTH, jitsiProvider.getExternalAuthSecret(), req, resp);
        }
        ctx.complete();
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doGet(req, resp);
  }

  /**
   * Forward.
   *
   * @param requestUrl the request url
   * @param action the action
   * @param secret the secret
   * @param req the req
   * @param resp the resp
   */
  private void forward(String requestUrl, Action action, String secret, HttpServletRequest req, HttpServletResponse resp) {
    HttpRequestBase request = null;
    if (req.getMethod().equals(HTTPMethods.GET)) {
      request = new HttpGet(requestUrl);
    } else {
      HttpPost post = new HttpPost(requestUrl);
      try {
        post.setEntity(new InputStreamEntity(req.getInputStream(), ContentType.create(req.getContentType())));
      } catch (IOException e) {
        LOG.warn("Cannot set entity for post request  {} : {} ", requestUrl, e.getMessage());
      }
      request = post;
    }

    String authHeader;
    // TODO: Use servlet forwarding for accessing internal resources. Solve the issue with filters (not invoked when forwarding)
    if (action == Action.INTERNAL_AUTH) {
      // Pass cookies for internal auth
      String cookies = getCookiesAsString(req);
      if (cookies != null) {
        request.setHeader("Cookie", cookies);
      }
      authHeader = AUTH_TOKEN_HEADER;
    } else {
      authHeader = EXTERNAL_AUTH_TOKEN_HEADER;
    }
    String token = Jwts.builder()
                       .setSubject("exo-webconf")
                       .claim("action", action.toString().toLowerCase())
                       .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                       .compact();

    request.setHeader(authHeader, token);
    // XXX: working with invalid SSL
    SSLContextBuilder builder = new SSLContextBuilder();
    try {
      builder.loadTrustMaterial(null, new TrustStrategy() {
        @Override
        public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
          return true;
        }
      });
    } catch (KeyStoreException | NoSuchAlgorithmException e) {
      LOG.warn("Cannot load trust material for SSL", e.getMessage());
    }
    SSLConnectionSocketFactory sslSF = null;
    try {
      sslSF = new SSLConnectionSocketFactory(builder.build(), SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
    } catch (KeyManagementException | NoSuchAlgorithmException e) {
      LOG.warn("Cannot create SSLConnectionSockerFactory: {}", e.getMessage());
    }

    try (CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslSF).build();
        CloseableHttpResponse response = httpClient.execute(request)) {
      for (Header header : response.getAllHeaders()) {
        if (!header.getName().equals(TRANSFER_ENCODING_HEADER)) {
          resp.setHeader(header.getName(), header.getValue());
          if (header.getName().equals("Content-Type")) {
            resp.setContentType(header.getValue());
          }
        }
      }
      resp.setStatus(response.getStatusLine().getStatusCode());

      HttpEntity entity = response.getEntity();
      if (entity != null) {
        if (resp.getContentType() != null && resp.getContentType().startsWith(APPLICATION_JSON)) {
          resp.setCharacterEncoding(UTF_8);
          resp.getWriter().write(EntityUtils.toString(entity, UTF_8));
        } else {
          resp.getWriter().write(EntityUtils.toString(entity));
        }
      }
    } catch (IOException e) {
      LOG.warn("Error occured while requesting remote resource [{}]", requestUrl, e.getMessage());
      try {
        if (req.getRequestURI().startsWith("/jitsi/meet/")) {
          resp.sendRedirect("/jitsi/resources/pages/error.html");
        } else {
          resp.sendError(HttpStatus.SC_FORBIDDEN, "Cannot connect to " + requestUrl);
        }
      } catch (IOException ex) {
        LOG.error("Cannot write response", ex.getMessage());
      }
    }
  }

  /**
   * Handle resource request.
   *
   * @param uri the uri
   * @param httpRequest the http request
   * @param httpResponse the http response
   */
  private void handleResourceRequest(String uri, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    try {
      uri = uri.substring(uri.indexOf("/resources") + 10);
      InputStream is = httpRequest.getServletContext().getResourceAsStream(uri);
      if (is != null) {
        httpResponse.setContentLength(is.available());
        stream(is, httpResponse.getOutputStream());
      } else {
        httpResponse.sendError(HttpStatus.SC_NOT_FOUND, "Resource " + uri + " is not found");
      }
    } catch (Exception e) {
      LOG.warn("Cannot load local resource {}, {}", uri, e.getMessage());
      try {
        httpResponse.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error while loading " + uri + " resource");
      } catch (IOException ex) {
        LOG.warn("Cannot send error response: {}", e.getMessage());
      }
    }
  }

  /**
   * Gets the cookies as string.
   *
   * @param req the req
   * @return the cookies as string
   */
  private String getCookiesAsString(HttpServletRequest req) {
    Cookie[] cookies = req.getCookies();
    if (cookies != null) {
      StringBuilder builder = new StringBuilder();
      for (Cookie cookie : cookies) {
        builder.append(cookie.getName()).append("=").append(cookie.getValue()).append(";");
      }
      return builder.toString();
    }
    return null;
  }

  /**
   * Gets the platform url.
   *
   * @param req the req
   * @return the platform url
   */
  private String getPlatformUrl(HttpServletRequest req) {
    return new StringBuilder(req.getScheme()).append("://")
                                             .append(req.getServerName())
                                             .append(("http".equals(req.getScheme()) && req.getServerPort() == 80
                                                 || "https".equals(req.getScheme()) && req.getServerPort() == 443
                                                                                                                  ? ""
                                                                                                                  : ":"
                                                                                                                      + req.getServerPort()))
                                             .toString();
  }

  /**
   * Writes inputstream to outputstream using NIO.
   *
   * @param input the input
   * @param output the output
   * @return the long
   * @throws IOException Signals that an I/O exception has occurred.
   */
  private long stream(InputStream input, OutputStream output) throws IOException {
    try (ReadableByteChannel inputChannel = Channels.newChannel(input);
        WritableByteChannel outputChannel = Channels.newChannel(output);) {
      ByteBuffer buffer = ByteBuffer.allocateDirect(10240);
      long size = 0;

      while (inputChannel.read(buffer) != -1) {
        buffer.flip();
        size += outputChannel.write(buffer);
        buffer.clear();
      }

      return size;
    }
  }

}
