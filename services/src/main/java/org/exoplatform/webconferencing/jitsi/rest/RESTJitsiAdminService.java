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
package org.exoplatform.webconferencing.jitsi.rest;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.webconferencing.WebConferencingService;
import org.exoplatform.webconferencing.client.ErrorInfo;
import org.exoplatform.webconferencing.jitsi.JitsiProvider;
import org.exoplatform.webconferencing.jitsi.JitsiProvider.Configuration;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST service for Jitsi provider in Web Conferencing.
 *
 * Created by The eXo Platform SAS.
 *
 */
@Path("/jitsiadmin/webconferencing")
@Produces(MediaType.APPLICATION_JSON)
@Api(tags = "/jitsiadmin", value = "/jitsiadmin", description = "Administration of Jitsi provider")
public class RESTJitsiAdminService implements ResourceContainer {

  /** The Constant LOG. */
  protected static final Log             LOG = ExoLogger.getLogger(RESTJitsiAdminService.class);

  /** The web conferencing. */
  protected final WebConferencingService webConferencing;

  /** The cache control. */
  private final CacheControl             cacheControl;

  /**
   * Instantiates a new REST service for Jitsi provider in Web Conferencing.
   *
   * @param webConferencing the web conferencing
   */
  public RESTJitsiAdminService(WebConferencingService webConferencing) {
    this.webConferencing = webConferencing;
    this.cacheControl = new CacheControl();
    this.cacheControl.setNoCache(true);
    this.cacheControl.setNoStore(true);
  }

  /**
   * Post Jitsi settings.
   *
   * @param uriInfo the uri info
   * @param conf the jitsi config
   * @return the response
   */
  @POST
  @RolesAllowed("administrators")
  @Path("/settings")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update provider configuration", httpMethod = "POST", response = Configuration.class, notes = "Use this method to read a call provider configuration. This operation only avalable to Administrator user.")
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "Request fulfilled. Provider configuration object returned.", response = Configuration.class),
      @ApiResponse(code = 401, message = "Unauthorized user (conversation state not present)"),
      @ApiResponse(code = 404, message = "Jitsi provider not found"),
      @ApiResponse(code = 500, message = "Error saving Jitsi settings") })
  public Response postSettings(@Context UriInfo uriInfo, Configuration conf) {
    ConversationState convo = ConversationState.getCurrent();
    if (convo != null) {
      String currentUserName = convo.getIdentity().getUserId();
      try {
        JitsiProvider jitsi = (JitsiProvider) webConferencing.getProvider(JitsiProvider.TYPE);
        if (jitsi != null) {
          jitsi.saveConfiguration(conf);
          return Response.ok().cacheControl(cacheControl).entity(conf).build();
        } else {
          return Response.status(Status.NOT_FOUND)
                         .cacheControl(cacheControl)
                         .entity(ErrorInfo.notFoundError("Jitsi provider not found"))
                         .build();
        }
      } catch (Throwable e) {
        LOG.error("Error saving Jitsi settings by '" + currentUserName + "'", e);
        return Response.serverError()
                       .cacheControl(cacheControl)
                       .entity(ErrorInfo.serverError("Error saving Jitsi settings"))
                       .build();
      }
    } else {
      return Response.status(Status.UNAUTHORIZED)
                     .cacheControl(cacheControl)
                     .entity(ErrorInfo.accessError("Unauthorized user"))
                     .build();
    }
  }
}
