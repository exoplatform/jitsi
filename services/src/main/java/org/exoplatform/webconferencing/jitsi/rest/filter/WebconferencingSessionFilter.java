/*
 * 
 */
package org.exoplatform.webconferencing.jitsi.rest.filter;

import java.io.IOException;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exoplatform.common.http.HTTPStatus;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.web.AbstractFilter;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.Authenticator;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityRegistry;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.web.filter.Filter;
import org.exoplatform.webconferencing.WebConferencingService;
import org.exoplatform.webconferencing.jitsi.JitsiProvider;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.impl.crypto.JwtSignatureValidator;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

/**
 * The Class WebconferencingSessionFilter.
 */
public class WebconferencingSessionFilter extends AbstractFilter implements Filter {

  /** The Constant LOG. */
  private static final Log       LOG                  = ExoLogger.getLogger(WebconferencingSessionFilter.class);

  /** The Constant AUTH_TOKEN_ATTRIBUTE. */
  private static final String    AUTH_TOKEN_ATTRIBUTE = "X-Exoplatform-Auth";

  /** The Constant INTERNAL_AUTH. */
  private static final String    INTERNAL_AUTH        = "internal_auth";

  /** The Constant EXTERNAL_AUTH. */
  private static final String    EXTERNAL_AUTH        = "external_auth";

  /** The Constant USERNAME. */
  private static final String    USERNAME             = "username";

  /** The webconferencing. */
  private WebConferencingService webconferencing;

  /**
   * Do filter.
   *
   * @param request the request
   * @param response the response
   * @param chain the chain
   * @throws IOException Signals that an I/O exception has occurred.
   * @throws ServletException the servlet exception
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;
    if (req.getRequestURI().equals("/portal/rest/jitsi/connectorsettings")) {
      chain.doFilter(req,resp);
    } else {

      if (checkAuthToken(req)) {
        String webconfToken = getCookie(req, WebConferencingService.SESSION_TOKEN_COOKIE);
        Claims claims = null;
        if (webconfToken != null) {
          try {
            claims = parseJWT(webconfToken, getWebconferencing().getSecretKey());
          } catch (Exception e) {
            LOG.warn("Cannot parse JWT session token from cookie", e.getMessage());
          }
        }
        if (claims != null && claims.containsKey(USERNAME)) {
          try {
            String username = String.valueOf(claims.get(USERNAME));
            if (isActiveUser(username)) {
              ExoContainer container = getContainer();
              ExoContainerContext.setCurrentContainer(container);
              ConversationState state = createState(username);
              ConversationState.setCurrent(state);
              SessionProviderService sessionProviders =
                  (SessionProviderService) getContainer().getComponentInstanceOfType(SessionProviderService.class);

              SessionProvider userProvider = new SessionProvider(state);
              sessionProviders.setSessionProvider(null, userProvider);
              chain.doFilter(request, response);
              try {
                ConversationState.setCurrent(null);
              } catch (Exception e) {
                LOG.warn("An error occured while cleaning the ConversationState", e);
              }
              try {
                ExoContainerContext.setCurrentContainer(null);
              } catch (Exception e) {
                LOG.warn("An error occured while cleaning the ThreadLocal", e);
              }
            } else {
              LOG.warn("The user {} is not active", username);
              writeError(resp, HTTPStatus.FORBIDDEN, "The user is not active");
            }
          } catch (Exception e) {
            LOG.warn("Cannot set ConversationState based on provided token", e.getMessage());
            chain.doFilter(request, response);
          }
        } else {
          chain.doFilter(request, response);
        }
      } else {
        LOG.warn("The request doesn't contain valid access token for internal auth");
        writeError(resp, HTTPStatus.UNAUTHORIZED, "The request is not authorized");
      }
    }

  }

  /**
   * Writes error message to the response.
   *
   * @param response the response
   * @param status the status
   * @param message the message
   * @throws IOException Signals that an I/O exception has occurred.
   */
  private void writeError(HttpServletResponse response, int status, String message) throws IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setStatus(status);
    response.getWriter().write("{\"error\":\"" + message + "\"}");
  }

  /**
   * Destroy.
   */
  @Override
  public void destroy() {
    // TODO Auto-generated method stub
  }

  /**
   * Gets the cookie.
   *
   * @param request the request
   * @param name the name
   * @return the cookie
   */
  private String getCookie(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie cookie : request.getCookies()) {
        if (cookie.getName().equals(name)) {
          return cookie.getValue();
        }
      }
    }
    return null;
  }

  /**
   * Creates the state.
   *
   * @param userId the user id
   * @return the conversation state
   */
  private ConversationState createState(String userId) {
    Identity userIdentity = userIdentity(userId);

    if (userIdentity != null) {
      ConversationState state = new ConversationState(userIdentity);
      // Keep subject as attribute in ConversationState.
      state.setAttribute(ConversationState.SUBJECT, userIdentity.getSubject());
      return state;
    }
    LOG.warn("User identity not found " + userId + " for setting conversation state");
    return null;
  }

  /**
   * Find or create user identity.
   *
   * @param userId the user id
   * @return the identity can be null if not found and cannot be created via
   *         current authenticator
   */
  protected Identity userIdentity(String userId) {
    IdentityRegistry identityRegistry = (IdentityRegistry) getContainer().getComponentInstanceOfType(IdentityRegistry.class);
    Authenticator authenticator = (Authenticator) getContainer().getComponentInstanceOfType(Authenticator.class);
    Identity userIdentity = identityRegistry.getIdentity(userId);
    if (userIdentity == null) {
      // We create user identity by authenticator, but not register it in the
      // registry
      try {
        if (LOG.isDebugEnabled()) {
          LOG.debug("User identity not registered, trying to create it for: " + userId);
        }
        userIdentity = authenticator.createIdentity(userId);
      } catch (Exception e) {
        LOG.warn("Failed to create user identity: " + userId, e);
      }
    }
    return userIdentity;
  }

  /**
   * Checks if is active user.
   *
   * @param userId the user id
   * @return true, if is active user
   */
  protected boolean isActiveUser(String userId) {
    IdentityManager identityManager = (IdentityManager) getContainer().getComponentInstanceOfType(IdentityManager.class);
    org.exoplatform.social.core.identity.model.Identity identity =
                                                                 identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME,
                                                                                                     userId);
    return !identity.isDeleted() && identity.isEnable();
  }

  /**
   * Valid auth token.
   *
   * @param request the request
   * @return true, if successful
   */
  protected boolean checkAuthToken(HttpServletRequest request) {
    JitsiProvider provider = (JitsiProvider) getWebconferencing().getProvider(JitsiProvider.TYPE);
    if (request.getHeader(AUTH_TOKEN_ATTRIBUTE) != null) {
      String token = request.getHeader(AUTH_TOKEN_ATTRIBUTE);
      Claims claims = null;
      try {
        claims = parseJWT(token, provider.getInternalAuthSecret());
      } catch (Exception e) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Cannot parse auth token JWT with internal auth secret", e.getMessage());
        }
        // Try with external secret
        try {
          claims = parseJWT(token, provider.getExternalAuthSecret());
        } catch (Exception ex) {
          LOG.warn("Cannot parse auth token JWT", e.getMessage());
        }
      }
      if (claims != null && claims.containsKey("action")) {
        String action = String.valueOf(claims.get("action"));
        if (INTERNAL_AUTH.equals(action) || EXTERNAL_AUTH.equals(action)) {
          request.getServletContext().setAttribute("auth_type", action);
          request.getServletContext().setAttribute("token", token);
          return true;
        }
      }
      return false;
    }
    LOG.warn("The request doesn't contain auth token header");
    return false;

  }

  /**
   * Parses the JWT.
   *
   * @param token the token
   * @param secret the secret
   * @return the claims
   */
  private Claims parseJWT(String token, String secret) {
    return Jwts.parser().setSigningKey(Keys.hmacShaKeyFor(secret.getBytes())).parseClaimsJws(token).getBody();
  }

  /**
   * Gets the webconferencing.
   *
   * @return the webconferencing
   */
  private WebConferencingService getWebconferencing() {
    if (webconferencing == null) {
      webconferencing = (WebConferencingService) getContainer().getComponentInstanceOfType(WebConferencingService.class);
    }
    return webconferencing;
  }
}
