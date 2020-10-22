package org.exoplatform.webconferencing.jitsi.rest;

import static org.exoplatform.webconferencing.Utils.getCurrentContext;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.http.HttpStatus;
import org.gatein.portal.controller.resource.ResourceRequestHandler;

import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.webconferencing.ContextInfo;
import org.exoplatform.webconferencing.IdentityStateException;
import org.exoplatform.webconferencing.UploadFileException;
import org.exoplatform.webconferencing.UserInfo;
import org.exoplatform.webconferencing.WebConferencingService;
import org.exoplatform.webconferencing.jitsi.JitsiProvider;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * The Class JitsiContextResource.
 */
@Path("/jitsi")
public class JitsiContextResource implements ResourceContainer {

  /** The Constant LOG. */
  private static final Log             LOG = ExoLogger.getLogger(JitsiContextResource.class);

  /** The Constant webconferencing. */
  private final WebConferencingService webconferencing;

  private final JitsiProvider          provider;

  /**
   * Instantiates a new jitsi context resource.
   *
   * @param webconferencing the webconferencing
   * @param repositoryService the repository service
   * @param sessionProviders the session providers
   */
  public JitsiContextResource(WebConferencingService webconferencing,
                              RepositoryService repositoryService,
                              SessionProviderService sessionProviders) {
    this.webconferencing = webconferencing;
    this.provider = (JitsiProvider) webconferencing.getProvider(JitsiProvider.TYPE);
  }

  /**
   * Content.
   *
   * @param request the request
   * @param userId the user id
   * @return the response
   */
  @GET
  @Path("/context/{userId}")
  @Produces(MediaType.APPLICATION_JSON)
  public ContextInfo context(@Context HttpServletRequest request, @PathParam("userId") String userId) {
    return getCurrentContext(userId, request.getLocale());
  }

  /**
   * Settings.
   *
   * @return the response
   */
  @GET
  @Path("/settings")
  public Response settings() {
    if (provider != null) {
      return Response.status(Status.OK).entity(provider.getSettings()).type(MediaType.APPLICATION_JSON).build();
    }
    return Response.status(Status.INTERNAL_SERVER_ERROR)
                   .entity("{\"error\":\"Jitsi Provider is not registered in webconferencing \"}")
                   .type(MediaType.APPLICATION_JSON)
                   .build();
  }

  /**
   * Upload recordings.
   *
   * @param request the request
   * @param owner the owner
   * @param isSpace the is space
   * @return the response
   * @throws Exception the exception
   */
  @POST
  @Path("/upload")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response upload(@Context HttpServletRequest request, @QueryParam("token") String token) {
    String owner = null;
    Boolean isGroup = null;
    String moderator = null;
    try {
      Claims body = Jwts.parser()
                        .setSigningKey(Keys.hmacShaKeyFor(provider.getExternalAuthSecret().getBytes()))
                        .parseClaimsJws(token)
                        .getBody();
      owner = body.get("owner", String.class);
      isGroup = body.get("isGroup", Boolean.class);
      moderator = body.get("moderator", String.class);
    } catch (Exception e) {
      LOG.error("Cannot parse JWT token for uploading recording", e.getMessage());
      return Response.status(HttpStatus.SC_BAD_REQUEST).entity("{\"error\":\"JWT token is invalid\"}").build();
    }
    if (owner == null || isGroup == null || moderator == null) {
      return Response.status(HttpStatus.SC_BAD_REQUEST)
                     .entity("{\"error\":\"JWT token should contain owner, idGroup, moderator\"}")
                     .build();
    }

    try {
      webconferencing.uploadFile(owner, isGroup, moderator, request);
      return Response.ok().build();
    } catch (RepositoryException | UploadFileException e) {
      LOG.error("Cannot upload recording for " + owner, e);
      return Response.serverError().entity("{\"error\":\"Cannot upload recording for " + owner + "\"}").build();
    }
  }

  /**
   * Content.
   *
   * @param request the request
   * @return the response
   */
  @GET
  @Path("/userinfo")
  @Produces(MediaType.APPLICATION_JSON)
  public Response userInfo(@Context HttpServletRequest request) {
    ConversationState state = ConversationState.getCurrent();
    if (state != null && !state.getIdentity().getUserId().equals(IdentityConstants.ANONIM)) {
      String userId = state.getIdentity().getUserId();
      try {
        UserInfo userInfo = webconferencing.getUserInfo(userId);
        String authToken = String.valueOf(request.getServletContext().getAttribute("token"));
        return Response.ok()
                       .entity(new UserInfoResponse(userInfo, authToken))
                       .build();
      } catch (IdentityStateException e) {
        LOG.warn("Cannot find identity with id: {}", userId);
        return Response.status(Status.INTERNAL_SERVER_ERROR)
                       .entity("{\"error\":\"Cannot find identity with id: " + userId + "\"}")
                       .type(MediaType.APPLICATION_JSON)
                       .build();
      }
    }
    return Response.status(Status.UNAUTHORIZED)
                   .entity("{\"error\":\"Current user is not authorized\"}")
                   .type(MediaType.APPLICATION_JSON)
                   .build();

  }

  /**
   * Content.
   *
   * @return the response
   */
  @GET
  @Path("/resources/version")
  public Response resourcesVersion() {
    return Response.status(Status.OK).entity(ResourceRequestHandler.VERSION).build();
  }

  /**
   * The Class Context.
   */
  public class InitContext {

    /** The username. */
    private final String      username;

    /** The user info. */
    private final UserInfo    userInfo;

    /** The context. */
    private final ContextInfo context;

    /**
     * Instantiates a new context.
     *
     * @param username the username
     * @param userInfo the user info
     * @param context the context
     */
    public InitContext(String username, UserInfo userInfo, ContextInfo context) {
      this.username = username;
      this.userInfo = userInfo;
      this.context = context;
    }

    /**
     * Gets the username.
     *
     * @return the username
     */
    public String getUsername() {
      return username;
    }

    /**
     * Gets the user info.
     *
     * @return the user info
     */
    public UserInfo getUserInfo() {
      return userInfo;
    }

    /**
     * Gets the context.
     *
     * @return the context
     */
    public ContextInfo getContext() {
      return context;
    }

  }

  /**
   * The Class UserInfoResponse.
   */
  public class UserInfoResponse {

    /** The user info. */
    private final UserInfo userInfo;

    /** The auth token. */
    private final String   authToken;

    /**
     * Instantiates a new user info response.
     *
     * @param userInfo the user info
     * @param authToken the auth token
     */
    public UserInfoResponse(UserInfo userInfo, String authToken) {
      this.userInfo = userInfo;
      this.authToken = authToken;
    }

    /**
     * Gets the user info.
     *
     * @return the user info
     */
    public UserInfo getUserInfo() {
      return userInfo;
    }

    /**
     * Gets the auth token.
     *
     * @return the auth token
     */
    public String getAuthToken() {
      return authToken;
    }
  }
}
