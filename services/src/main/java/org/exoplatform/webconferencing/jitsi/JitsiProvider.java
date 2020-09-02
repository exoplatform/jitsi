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
package org.exoplatform.webconferencing.jitsi;

import java.util.HashMap;
import java.util.Locale;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.configuration.ConfigurationException;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profile.settings.IMType;
import org.exoplatform.social.core.profile.settings.UserProfileSettingsService;
import org.exoplatform.webconferencing.CallProvider;
import org.exoplatform.webconferencing.UserInfo.IMInfo;

import antlr.Token;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Jitsi provider implementation.
 * 
 * Created by The eXo Platform SAS.
 *
 * @author <a href="mailto:pnedonosko@exoplatform.com">Peter Nedonosko</a>
 * @version $Id: MyConnectorProvider.java 00000 Mar 30, 2017 pnedonosko $
 */
public class JitsiProvider extends CallProvider {

  /** The Constant LOG. */
  protected static final Log        LOG                = ExoLogger.getLogger(JitsiProvider.class);

  /** The Constant TYPE. */
  public static final String        TYPE               = "jitsi";

  /** The Constant CONFIG_SECRET. */
  public static final String        CONFIG_SECRET      = "secret";

  /** The Constant CONFIG_SERVICE_URL. */
  public static final String        CONFIG_SERVICE_URL = "service-url";

  /** The Constant TITLE. */
  public static final String        TITLE              = "Jitsi";

  /** The Constant VERSION. */
  public static final String        VERSION            = "1.0.0";

  /** The auth tokens. */
  // TODO: should be cache with expiration
  protected HashMap<String, String> authTokens         = new HashMap<>();

  /**
   * Settings for My Call provider.
   */
  public class MySettings extends Settings {

    /**
     * Gets the url.
     *
     * @return the url
     */
    public String getUrl() {
      return JitsiProvider.this.getUrl();
    }

  }

  /**
   * IM info for user profile.
   */
  public class JitsiIMInfo extends IMInfo {

    /**
     * Instantiates a new Jitsi IM info.
     *
     * @param id the id
     */
    protected JitsiIMInfo(String id) {
      super(TYPE, id);
    }

    // You may add other specific methods here. Getters will be serialized to JSON and available on client
    // side (in Javascript provider module).
  }

  /** The secret. */
  protected final String secret;

  /** The connector web-services URL (will be used to generate Call page URLs). */
  protected final String url;

  /**
   * Instantiates a new JitsiProvider provider.
   *
   * @param profileSettings the profile settings
   * @param params the params (from configuration.xml)
   * @throws ConfigurationException the configuration exception
   */
  public JitsiProvider(UserProfileSettingsService profileSettings, InitParams params) throws ConfigurationException {
    super(params);

    String secret = this.config.get(CONFIG_SECRET);
    if (secret == null || (secret = secret.trim()).length() == 0) {
      throw new ConfigurationException(CONFIG_SECRET + " required and should be non empty.");
    }
    this.secret = secret;

    String serviceUrl = this.config.get(CONFIG_SERVICE_URL);
    if (serviceUrl == null || (serviceUrl = serviceUrl.trim()).length() == 0) {
      throw new ConfigurationException(CONFIG_SERVICE_URL + " required and should be non empty.");
    }
    this.url = serviceUrl;

    if (profileSettings != null) {
      // add plugin programmatically as it's an integral part of the provider
      profileSettings.addIMType(new IMType(TYPE, TITLE));
    }
  }

  /**
   * Instantiates a new JitsiProvider provider. This constructor can be used in environments when no
   * {@link UserProfileSettingsService} found (e.g. in test environments).
   *
   * @param params the params (from configuration.xml)
   * @throws ConfigurationException the configuration exception
   */
  public JitsiProvider(InitParams params) throws ConfigurationException {
    this(null, params);
  }

  /**
   * Adds the client.
   *
   * @param clientId the client id
   * @param userId the user id
   */
  public void addClient(String clientId, String userId) {
    IdentityManager identityManager = PortalContainer.getInstance().getComponentInstanceOfType(IdentityManager.class);
    Identity identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, userId);
    String name = identity.getProfile().getFullName();
    String email = identity.getProfile().getEmail();

    String authToken = Jwts.builder()
                           .setSubject("jitsi")
                           .claim("name", name)
                           .claim("email", email)
                           .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                           .compact();
    LOG.info("PUT AUTH TOKEN. ClientId: " + clientId + " token: " + authToken);
    authTokens.put(clientId, authToken);
  }

  /**
   * Gets the auth token.
   *
   * @param clientId the client id
   * @return the auth token
   */
  public String getAuthToken(String clientId) {
    String token = authTokens.get(clientId);
    LOG.info("Get AUTH TOKEN. ClientId: " + clientId + " token: " + token);
    return token;
  }

  /**
   * Removes the client.
   *
   * @param clientId the client id
   */
  public void removeClient(String clientId) {
    LOG.info("Get AUTH TOKEN. ClientId: " + clientId);
    authTokens.remove(clientId);
  }

  /**
   * Gets the settings.
   *
   * @return the settings
   */
  public MySettings getSettings() {
    return new MySettings();
  }

  /**
   * Gets the url.
   *
   * @return the url
   */
  public String getUrl() {
    return url;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IMInfo getIMInfo(String imId) {
    // TODO here you can validate, extend or do any other IM id preparations
    return new JitsiIMInfo(imId);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getType() {
    return TYPE;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String[] getSupportedTypes() {
    return new String[] { getType() };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getTitle() {
    return TITLE;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getDescription(Locale locale) {
    // TODO Implement i18n description of the provider
    return super.getDescription(locale);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getVersion() {
    return VERSION;
  }

}
