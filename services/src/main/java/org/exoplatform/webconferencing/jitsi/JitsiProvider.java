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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.container.configuration.ConfigurationException;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profile.settings.IMType;
import org.exoplatform.social.core.profile.settings.UserProfileSettingsService;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.webconferencing.ActiveCallProvider;
import org.exoplatform.webconferencing.CallProvider;
import org.exoplatform.webconferencing.UserInfo.IMInfo;
import org.exoplatform.commons.api.settings.SettingService;

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
  protected static final Log LOG                         = ExoLogger.getLogger(JitsiProvider.class);

  /** The Constant TYPE. */
  public static final String TYPE                        = "jitsi";

  /** The Constant CONFIG_CLIENT_SECRET. */
  public static final String CONFIG_CLIENT_SECRET        = "client-secret";

  /** The Constant CONFIG_EXTERNAL_AUTH_SECRET. */
  public static final String CONFIG_INTERNAL_AUTH_SECRET = "internal-auth-secret";

  /** The Constant CONFIG_EXTERNAL_AUTH_SECRET. */
  public static final String CONFIG_EXTERNAL_AUTH_SECRET = "external-auth-secret";

  /** The Constant CONFIG_SERVICE_URL. */
  public static final String CONFIG_SERVICE_URL          = "service-url";

  /** The Constant TITLE. */
  public static final String TITLE                       = "Jitsi";

  /** The Constant VERSION. */
  public static final String VERSION                     = "1.0.0";

  /**
   * Settings for Jitsi provider.
   */
  public class JitsiSettings extends Settings {

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

  /** The internal auth secret. */
  protected final String internalAuthSecret;

  /** The external auth secret. */
  protected final String externalAuthSecret;

  /** The connector web-services URL (will be used to generate Call page URLs). */
  protected final String url;

  private SettingService settingsService;
  private SpaceService    spaceService;
  private IdentityManager identityManager;


  /**
   * Instantiates a new JitsiProvider provider.
   *
   * @param profileSettings the profile settings
   * @param params the params (from configuration.xml)
   * @throws ConfigurationException the configuration exception
   */
  public JitsiProvider(UserProfileSettingsService profileSettings, InitParams params, SettingService settingService, IdentityManager identityManager, SpaceService spaceService) throws ConfigurationException {
    super(params);
    String internalAuthSecret = this.config.get(CONFIG_INTERNAL_AUTH_SECRET);
    if (internalAuthSecret == null || (internalAuthSecret = internalAuthSecret.trim()).length() == 0) {
      throw new ConfigurationException(CONFIG_INTERNAL_AUTH_SECRET + " required and should be non empty.");
    }
    this.internalAuthSecret = internalAuthSecret;

    String externalAuthSecret = this.config.get(CONFIG_EXTERNAL_AUTH_SECRET);
    if (externalAuthSecret == null || (externalAuthSecret = externalAuthSecret.trim()).length() == 0) {
      throw new ConfigurationException(CONFIG_EXTERNAL_AUTH_SECRET + " required and should be non empty.");
    }
    this.externalAuthSecret = externalAuthSecret;

    String serviceUrl = this.config.get(CONFIG_SERVICE_URL);
    if (serviceUrl == null || (serviceUrl = serviceUrl.trim()).length() == 0) {
      throw new ConfigurationException(CONFIG_SERVICE_URL + " required and should be non empty.");
    }
    this.url = serviceUrl;

    if (profileSettings != null) {
      // add plugin programmatically as it's an integral part of the provider
      profileSettings.addIMType(new IMType(TYPE, TITLE));
    }

    this.settingsService = settingService;
    this.identityManager = identityManager;
    this.spaceService=spaceService;
  }

  /**
   * Instantiates a new JitsiProvider provider. This constructor can be used in environments when no
   * {@link UserProfileSettingsService} found (e.g. in test environments).
   *
   * @param params the params (from configuration.xml)
   * @throws ConfigurationException the configuration exception
   */
  public JitsiProvider(InitParams params, SettingService settingService, IdentityManager identityManager, SpaceService spaceService) throws ConfigurationException {
    this(null, params, settingService, identityManager, spaceService);
  }

  /**
   * Gets the internal auth secret.
   *
   * @return the internal auth secret
   */
  public String getInternalAuthSecret() {
    return this.internalAuthSecret;
  }

  /**
   * Gets the external auth secret.
   *
   * @return the external auth secret
   */
  public String getExternalAuthSecret() {
    return this.externalAuthSecret;
  }

  /**
   * Gets the settings.
   *
   * @return the settings
   */
  public JitsiSettings getSettings() {
    return new JitsiSettings();
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

  @Override
  public List<ActiveCallProvider> getActiveProvidersForSpace(String spaceId) {
    return new ArrayList<>(List.of(new ActiveCallProvider("jitsi", TITLE, null, true, isConfiguredForIdentity(spaceId))));
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


  @Override
  public void setConfiguredForIdentity(String spaceId, boolean configured) {
    this.settingsService.set(Context.GLOBAL,
                             Scope.SPACE.id(spaceId),
                             String.valueOf(this.getType()),
                             new SettingValue<>(configured));
  }


  @Override
  public boolean isConfiguredForIdentity(String remoteId) {
    Space space = spaceService.getSpaceByPrettyName(remoteId);
    if (space!=null) {
      SettingValue settingValue = this.settingsService.get(Context.GLOBAL,
                                                           Scope.SPACE.id(space.getId()),
                                                           String.valueOf(this.getType()));
      return settingValue == null || Boolean.parseBoolean(settingValue.getValue().toString());
    } else {
      //jitsi is always configured for other than spaces
      return true;
    }

  }
}
