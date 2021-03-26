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

import java.util.Locale;

import org.json.JSONObject;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.container.configuration.ConfigurationException;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ObjectParameter;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profile.settings.IMType;
import org.exoplatform.social.core.profile.settings.UserProfileSettingsService;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.webconferencing.CallProvider;
import org.exoplatform.webconferencing.UserInfo.IMInfo;

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

  /** The constant JITSI_CONFIGURATION. */
  public static final String    JITSI_CONFIGURATION         = "jitsi-configuration";

  /** The constant JITSI_SCOPE_NAME. */
  protected static final String JITSI_SCOPE_NAME            = "webconferencing.jitsi";

  /** The Constant KEY_JITSI_SETTINGS. */
  protected static final String KEY_JITSI_SETTINGS          = "jitsi-settings";

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
  public class Settings extends org.exoplatform.webconferencing.CallProvider.Settings {

    /**
     * Instantiates a new Jitsi settings.
     */
    public Settings() {
    }

    /**
     * Gets jitsi configuration.
     *
     * @return the jitsi configuration
     */
    public Configuration getConfiguration() {
      return JitsiProvider.this.getConfiguration();
    }

    /**
     * Gets Jitsi service URL.
     *
     * @return the url
     */
    public String getUrl() {
      return JitsiProvider.this.getServiceUrl();
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

  /** The settings service. */
  protected final SettingService settingService;
  
  /** The identity manager. */
  protected final IdentityManager     identityManager;

  /** The organization service. */
  protected final OrganizationService organizationService;

  /** The document service. */
  protected final SpaceService        spaceService;

  /** The internal auth secret. */
  protected final String internalAuthSecret;

  /** The external auth secret. */
  protected final String externalAuthSecret;

  /** The connector web-services URL (will be used to generate Call page URLs). */
  protected final String serviceUrl;

  /** The jitsi configuration. */
  protected Configuration configuration;

  /**
   * Instantiates a new JitsiProvider provider.
   *
   * @param settingService the setting service
   * @param identityManager the identity manager
   * @param organizationService the organization service
   * @param spaceService the space service
   * @param profileSettings the profile settings
   * @param params the params (from configuration.xml)
   * @throws ConfigurationException the configuration exception
   */
  public JitsiProvider(SettingService settingService,
                       IdentityManager identityManager,
                       OrganizationService organizationService,
                       SpaceService spaceService,
                       UserProfileSettingsService profileSettings,
                       InitParams params)
      throws ConfigurationException {
    super(params);
    this.settingService = settingService;
    this.identityManager = identityManager;
    this.organizationService = organizationService;
    this.spaceService = spaceService;
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
    this.serviceUrl = serviceUrl;

    if (profileSettings != null) {
      // add plugin programmatically as it's an integral part of the provider
      profileSettings.addIMType(new IMType(TYPE, TITLE));
    }

    // try read Jitsi config from storage first
    Configuration configuration;
    try {
      configuration = readConfig();
    } catch (Exception e) {
      LOG.error("Error reading Jitsi configuration", e);
      configuration = null;
    }

    if (configuration == null) {
      ObjectParameter objParam = params.getObjectParam(JITSI_CONFIGURATION);
      if (objParam != null) {
        Object obj = objParam.getObject();
        if (obj != null && Configuration.class.isAssignableFrom(obj.getClass())) {
          this.configuration = Configuration.class.cast(obj);
        } else {
          LOG.warn("Predefined services configuration exists but Configuration object not found.");
          this.configuration = new Configuration();
        }
      } else {
        this.configuration = new Configuration();
      }
    } else {
      this.configuration = configuration;
    }
  }

  /**
   * Instantiates a new JitsiProvider provider. This constructor can be used in
   * environments when no {@link UserProfileSettingsService} found (e.g. in test
   * environments).
   *
   * @param settingService the setting service
   * @param identityManager the identity manager
   * @param organizationService the organization service
   * @param spaceService the space service
   * @param params the params (from configuration.xml)
   * @throws ConfigurationException the configuration exception
   */
  public JitsiProvider(SettingService settingService,
                       IdentityManager identityManager,
                       OrganizationService organizationService,
                       SpaceService spaceService,
                       InitParams params)
      throws ConfigurationException {
    this(settingService, identityManager, organizationService, spaceService, null, params);
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
   * Gets Jitsi provider settings.
   *
   * @return the settings
   */
  public Settings getSettings() {
    return new Settings();
  }

  /**
   * Gets Jitsi service URL.
   *
   * @return the url
   */
  public String getServiceUrl() {
    return serviceUrl;
  }

  /**
   * Gets the jitsi configuration.
   *
   * @return the jitsi configuration
   */
  public Configuration getConfiguration() {
    return this.configuration.copy();
  }

  /**
   * Save jitsi configuration.
   *
   * @param conf the conf
   * @throws Exception the exception
   */
  public void saveConfiguration(Configuration conf) throws Exception {
    saveConfig(conf);
    this.configuration = conf;
    logRemoteLogEnabled();
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
  public boolean isLogEnabled() {
    return configuration.isLogEnabled();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getVersion() {
    return VERSION;
  }

  /**
   * Json to Jitsi config.
   *
   * @param json the json
   * @return the Jitsi configuration
   * @throws Exception the exception
   */
  public Configuration jsonToConfig(JSONObject json) throws Exception {
    Configuration conf = new Configuration();

    boolean logEnabled = json.optBoolean("logEnabled", false);
    conf.setLogEnabled(logEnabled);

    return conf;
  }

  /**
   * Jitsi config to json.
   *
   * @param conf the jitsi conf
   * @return the JSON object
   * @throws Exception the exception
   */
  public JSONObject configToJson(Configuration conf) throws Exception {
    JSONObject json = new JSONObject();

    json.put("logEnabled", conf.isLogEnabled());

    return json;
  }

  /**
   * The Class Configuration.
   */
  public static class Configuration {

    /** The enable log. */
    protected boolean logEnabled;

    /**
     * Instantiates a new configuration.
     */
    public Configuration() {
    }

      /**
     * Instantiates a new Configuration.
     *
     * @param logEnabled the log enabled
     */
    public Configuration(boolean logEnabled) {
      this.logEnabled = logEnabled;
    }

      /**
     * Checks if is log enabled.
     *
     * @return true, if is log enabled
     */
    public boolean isLogEnabled() {
      return logEnabled;
    }

    /**
     * Sets enabled log.
     *
     * @param enableLog the new log enabled
     */
    public void setLogEnabled(boolean enableLog) {
      this.logEnabled = enableLog;
    }

    /**
     * Return a copy of the config.
     *
     * @return the Jitsi configuration
     */
    public Configuration copy() {
      Configuration copy = new Configuration();
      copy.setLogEnabled(isLogEnabled());
      return copy;
    }
  }

  /**
   * Save jitsi config.
   *
   * @param conf the conf
   * @throws Exception the exception
   */
  protected void saveConfig(Configuration conf) throws Exception {
    final String initialGlobalId = Scope.GLOBAL.getId();
    try {
      JSONObject json = configToJson(conf);
      settingService.set(Context.GLOBAL,
                         Scope.GLOBAL.id(JITSI_SCOPE_NAME),
                         KEY_JITSI_SETTINGS,
                         SettingValue.create(json.toString()));
    } finally {
      Scope.GLOBAL.id(initialGlobalId);
    }
  }

  /**
   * Read jitsi config.
   *
   * @return the Jitsi configuration
   */
  protected Configuration readConfig() {
    final String initialGlobalId = Scope.GLOBAL.getId();
    try {
      SettingValue<?> val = settingService.get(Context.GLOBAL, Scope.GLOBAL.id(JITSI_SCOPE_NAME), KEY_JITSI_SETTINGS);
      if (val != null) {
        String str = String.valueOf(val.getValue());
        if (str.startsWith("{")) {
          // Assuming it's JSON
          Configuration conf = jsonToConfig(new JSONObject(str));
          return conf;
        } else {
          LOG.warn("Cannot parse saved JitsiConfiguration: " + str);
        }
      }
      return null;
    } catch (Exception e) {
      LOG.error("Error getting JitsiConfiguration", e);
    } finally {
      Scope.GLOBAL.id(initialGlobalId);
    }
    return null;
  }

  /**
   * Log remote log enabled.
   */
  protected void logRemoteLogEnabled() {
    if (configuration != null && configuration.isLogEnabled()) {
      LOG.info("Remote diagnostic log enabled for Jitsi connector");
    } else {
      LOG.info("Remote diagnostic log disabled for Jitsi connector");
    }
  }

}
