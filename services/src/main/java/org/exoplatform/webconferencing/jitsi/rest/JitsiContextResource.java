/*
 * Copyright (C) 2003-2021 eXo Platform SAS.
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
package org.exoplatform.webconferencing.jitsi.rest;

import static org.exoplatform.webconferencing.Utils.getCurrentContext;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

import org.gatein.portal.controller.resource.ResourceRequestHandler;

import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.webconferencing.CallProviderConfiguration;
import org.exoplatform.webconferencing.ContextInfo;
import org.exoplatform.webconferencing.IdentityStateException;
import org.exoplatform.webconferencing.UploadFileException;
import org.exoplatform.webconferencing.UploadFileInfo;
import org.exoplatform.webconferencing.UserInfo;
import org.exoplatform.webconferencing.WebConferencingService;
import org.exoplatform.webconferencing.client.ErrorInfo;
import org.exoplatform.webconferencing.jitsi.JitsiProvider;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * The Class JitsiContextResource.
 */
@Path("/jitsi")
@Api(tags = "/jitsi", value = "/jitsi", description = "Operations on Jitsi provider")
public class JitsiContextResource implements ResourceContainer {

  /** The Constant INTERNAL_AUTH. */
  private static final String          INTERNAL_AUTH = "internal_auth";

  /** The Constant LOG. */
  private static final Log             LOG           = ExoLogger.getLogger(JitsiContextResource.class);

  /** The Constant webconferencing. */
  private final WebConferencingService webconferencing;

  /** The provider. */
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
  @ApiOperation(value = "Return user context settings", httpMethod = "GET", response = CallProviderConfiguration.class, 
    notes = "Use this method to read a call provider configuration. This operation only avalable to Administrator user.")
  @ApiResponses(value = { @ApiResponse(code = 200, message = "Request fulfilled. Provider configuration object returned.", response = CallProviderConfiguration.class),
    @ApiResponse(code = 401, message = "Unauthorized user (conversation state not present). Error code: " + ErrorInfo.CODE_ACCESS_ERROR),
    @ApiResponse(code = 404, message = "Provider not found. Error code: " + ErrorInfo.CODE_NOT_FOUND_ERROR),
    @ApiResponse(code = 500, message = "Internal server error due to data encoding or formatting result to JSON. Error code: " + ErrorInfo.CODE_SERVER_ERROR)})
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
   * @param token the token
   * @return the response
   */
  @SuppressWarnings("unchecked")
  @POST
  @Path("/upload")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response upload(@Context HttpServletRequest request, @QueryParam("token") String token) {
    String callId = null;
    String owner = null;
    String type = null;
    String moderator = null;
    List<String> participants = null;
    try {
      Claims body = Jwts.parser()
                        .setSigningKey(Keys.hmacShaKeyFor(provider.getExternalAuthSecret().getBytes()))
                        .parseClaimsJws(token)
                        .getBody();
      owner = body.get("owner", String.class);
      type = body.get("type", String.class);
      moderator = body.get("moderator", String.class);
      callId = body.get("callId", String.class);
      participants = (List<String>) body.get("participants", List.class);
    } catch (Exception e) {
      LOG.error("Cannot parse JWT token for uploading recording", e.getMessage());
      return Response.status(Status.BAD_REQUEST).entity("{\"error\":\"JWT token is invalid\"}").build();
    }
    if (callId == null || owner == null || type == null || moderator == null) {
      return Response.status(Status.BAD_REQUEST)
                     .entity("{\"error\":\"JWT token should contain owner, type, moderator\"}")
                     .build();
    }

    try {
      UploadFileInfo uploadFileInfo = new UploadFileInfo(callId, owner, type, moderator, participants);
      webconferencing.uploadFile(uploadFileInfo, request);
      return Response.ok().build();
    } catch (RepositoryException | UploadFileException e) {
      LOG.error("Cannot upload recording for " + owner, e);
      return Response.serverError().entity("{\"error\":\"Cannot upload recording for " + owner + "\"}").build();
    }
  }

  /**
   * Returns userinfo and auth token for user.
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
        return Response.ok().entity(new UserInfoResponse(userInfo, authToken)).build();
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
   * Returns Internal Auth token for invited guests.
   *
   * @param request the request
   * @return the response
   */
  @GET
  @Path("/token")
  @Produces(MediaType.APPLICATION_JSON)
  public Response token(@Context HttpServletRequest request) {
    String token = Jwts.builder()
                       .setSubject("exo-webconf")
                       .claim("action", INTERNAL_AUTH.toString().toLowerCase())
                       .setExpiration(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10)))
                       .signWith(Keys.hmacShaKeyFor(provider.getInternalAuthSecret().getBytes()))
                       .compact();
    return Response.ok().entity("{\"token\": \" " + token + "\"}").build();
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
