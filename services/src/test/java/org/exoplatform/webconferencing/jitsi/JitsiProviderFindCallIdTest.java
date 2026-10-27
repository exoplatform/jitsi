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
package org.exoplatform.webconferencing.jitsi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import org.exoplatform.webconferencing.CallProvider;

/**
 * The call id the server reads out of a Jitsi meeting link must be the one
 * the call page uses as its room name, which Jibri names the recording after
 * and the upload token carries.
 */
class JitsiProviderFindCallIdTest {

  /**
   * The link an agenda event stores: the invite id it appends is not part of
   * the call id.
   */
  @Test
  void stripsTheInviteIdTheAgendaAppends() {
    assertEquals("g_42-0b6e1f0c9d3a4e2f8a1b2c3d4e5f6a7b",
                 JitsiProvider.extractCallId("https://tribe.example.com/jitsi/meet/g_42-0b6e1f0c9d3a4e2f8a1b2c3d4e5f6a7b?inviteId=abc123"));
  }

  /**
   * A link with no query string answers its last segment, underscores kept.
   */
  @Test
  void readsALinkWithoutQuery() {
    assertEquals("g_sales_team", JitsiProvider.extractCallId("https://tribe.example.com/jitsi/meet/g_sales_team"));
  }

  /**
   * A fragment and a further path segment end the call id too.
   */
  @Test
  void stopsAtAFragmentOrAFurtherSegment() {
    assertEquals("p_john-mary", JitsiProvider.extractCallId("https://host/jitsi/meet/p_john-mary#config"));
    assertEquals("p_john-mary", JitsiProvider.extractCallId("https://host/jitsi/meet/p_john-mary/"));
  }

  /**
   * A link of another provider, a link with no id and no link are not Jitsi
   * meetings.
   */
  @Test
  void answersNullForAnythingElse() {
    assertNull(JitsiProvider.extractCallId("https://teams.microsoft.com/l/meetup-join/19%3ameeting"));
    assertNull(JitsiProvider.extractCallId("https://host/jitsi/meet/"));
    assertNull(JitsiProvider.extractCallId("https://host/jitsi/meet/?inviteId=abc"));
    assertNull(JitsiProvider.extractCallId(null));
  }

  /**
   * The provider answers the lookup the core asks it through the SPI.
   */
  @Test
  void theProviderAnswersThroughTheSpi() {
    CallProvider provider = mock(JitsiProvider.class, CALLS_REAL_METHODS);
    assertEquals("g_space", provider.findCallId("https://host/jitsi/meet/g_space?inviteId=x"));
    assertNull(provider.findCallId("https://zoom.us/j/123"));
  }

}
