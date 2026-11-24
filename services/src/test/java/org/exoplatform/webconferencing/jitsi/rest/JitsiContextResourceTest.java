/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.webconferencing.jitsi.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserHandler;
import org.exoplatform.services.organization.UserStatus;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.services.security.IdentityConstants;
import org.exoplatform.webconferencing.ContextInfo;
import org.exoplatform.webconferencing.Utils;
import org.exoplatform.webconferencing.WebConferencingService;
import org.exoplatform.webconferencing.client.ErrorInfo;

import jakarta.servlet.http.HttpServletRequest;

class JitsiContextResourceTest {

  private UserHandler          userHandler;

  private JitsiContextResource resource;

  @BeforeEach
  void setUp() {
    WebConferencingService webconferencing = mock(WebConferencingService.class);
    OrganizationService organization = mock(OrganizationService.class);
    userHandler = mock(UserHandler.class);
    when(organization.getUserHandler()).thenReturn(userHandler);
    resource = new JitsiContextResource(webconferencing, null, null, organization);
    ConversationState.setCurrent(null);
  }

  @AfterEach
  void tearDown() {
    ConversationState.setCurrent(null);
  }

  @Test
  void aSignedInUserGetsTheirOwnContextOnly() {
    signIn("john");
    assertTrue(resource.isContextAllowed("john"));
    assertFalse(resource.isContextAllowed("mary"));
    assertFalse(resource.isContextAllowed("guest-ann-lee-1"));
  }

  @Test
  void anotherUsersContextIsRefusedWithForbiddenAndNotStored() {
    signIn("john");
    HttpServletRequest request = mock(HttpServletRequest.class);
    try (MockedStatic<Utils> utils = mockStatic(Utils.class)) {
      Response response = resource.context(request, "mary");
      assertEquals(Status.FORBIDDEN.getStatusCode(), response.getStatus());
      CacheControl cacheControl = (CacheControl) response.getMetadata().getFirst("Cache-Control");
      assertTrue(cacheControl.isNoStore());
      assertTrue(cacheControl.isNoCache());
      assertTrue(cacheControl.isPrivate());
      ErrorInfo error = (ErrorInfo) response.getEntity();
      assertEquals(ErrorInfo.CODE_ACCESS_ERROR, error.getCode());
      assertEquals("Access denied", error.getMessage());
      utils.verifyNoInteractions();
    }
  }

  @Test
  void theOwnContextIsReturnedAndNotStored() {
    signIn("john");
    HttpServletRequest request = mock(HttpServletRequest.class);
    ContextInfo context = mock(ContextInfo.class);
    try (MockedStatic<Utils> utils = mockStatic(Utils.class)) {
      utils.when(() -> Utils.getCurrentContext(eq("john"), any())).thenReturn(context);
      Response response = resource.context(request, "john");
      assertEquals(Status.OK.getStatusCode(), response.getStatus());
      assertEquals(context, response.getEntity());
      CacheControl cacheControl = (CacheControl) response.getMetadata().getFirst("Cache-Control");
      assertTrue(cacheControl.isNoStore());
      assertTrue(cacheControl.isNoCache());
      assertTrue(cacheControl.isPrivate());
    }
  }

  @Test
  void aRequestWithoutSignedInUserGetsAGuestContextOnly() throws Exception {
    when(userHandler.findUserByName("guest-john", UserStatus.ANY)).thenReturn(mock(User.class));
    assertTrue(resource.isContextAllowed("guest-ann-lee-1"));
    assertFalse(resource.isContextAllowed("john"));
    assertFalse(resource.isContextAllowed("guest-john"));
  }

  @Test
  void anAnonymousStateIsTreatedAsNoSignedInUser() {
    signIn(IdentityConstants.ANONIM);
    assertTrue(resource.isContextAllowed("guest-ann-lee-1"));
    assertFalse(resource.isContextAllowed("john"));
  }

  @Test
  void aGuestContextIsRefusedWhenUsersCannotBeRead() throws Exception {
    when(userHandler.findUserByName("guest-ann-lee-1", UserStatus.ANY)).thenThrow(new IllegalStateException("unavailable"));
    assertFalse(resource.isContextAllowed("guest-ann-lee-1"));
  }

  private void signIn(String userId) {
    ConversationState.setCurrent(new ConversationState(new Identity(userId)));
  }
}
