/**
 * Jitsi provider module for Web Conferencing. This script will be used to add a
 * provider to Web Conferencing module and then handle calls for portal
 * user/groups.
 */
(function($, webConferencing, callButton) {
  "use strict";
  var globalWebConferencing = typeof eXo != "undefined" && eXo && eXo.webConferencing ? eXo.webConferencing : null;
  // Use webConferencing from global eXo namespace (for non AMD uses).
  // This can be actual when running the script outside the portal page - e.g.
  // on a custom call page.
  if (!webConferencing && globalWebConferencing) {
    webConferencing = globalWebConferencing;
  }

  if (webConferencing) {

    // Start with default logger, later in configure() we'll get it for the
    // provider.
    // We know it's jitsi here.
    var log = webConferencing.getLog("jitsi");
    // log.trace("> Loading at " + location.origin + location.pathname);

    /**
     * An object that implements Web Conferencing SPI contract for a call
     * provider.
     */

    function JitsiProvider() {

      const GUEST_TYPE = "guest";
      const GUEST_EXPIRATION_MS = 1000 * 60 * 4; // 4 hrs
      
      var self = this;
      var settings;

      /**
       * MUST return a call type name. If several types supported, this one is
       * assumed as major one and it will be used for referring this connector
       * in getProvider() and similar methods. This type also should listed in
       * getSupportedTypes(). Call type is the same as used in user profile.
       */
      this.getType = function() {
        if (settings) {
          return settings.type;
        }
      };

      /**
       * MUST return all call types supported by a connector.
       */
      this.getSupportedTypes = function() {
        if (settings) {
          return settings.supportedTypes;
        }
      };

      /**
       * MUST return human-readable title of a connector.
       */
      this.getTitle = function() {
        if (settings) {
          return settings.title;
        }
      };
      
      /**
       * Request a status of Jitsi Call App
       */
      var getCallAppStatus = function() {
        return $.get({
          type: "GET",
          url: "/jitsi",
        });
      };

      /**
       * Returns call members
       */
      var getCallMembers = function(currentUser, target) {
        var callMembers = [];
        if (target.group) {
          // If target is a group: go through its members (this will
          // work for both space and chat room)
          for (var uname in target.members) {
            if (target.members.hasOwnProperty(uname)) {
              var u = target.members[uname];
              callMembers.push(u);
            }
          }
        } else {
          // Otherwise it's 1:1 call
          callMembers.push(currentUser);
          callMembers.push(target);
        }
        return callMembers;
      };

      /**
       * Creates callId for given context and target
       */
      var getCallId = function(context, target) {
        var callId;
        if (target.group) {
          callId = "g_" + (target.type == "chat_room" ? context.roomName : target.id);
        } else {
          // Sort call members to have always the same ID for two
          // parts independently on who started the call
          var callMembersAsc = getCallMembers(context.currentUser, target).map(function(member) {
            return member.id;
          }).slice();
          callMembersAsc.sort();
          callId = "p_" + callMembersAsc.join("-");
        }
        // Transliterate callId
        return window.slugify(callId);
      };

      /**
       * Returns call url
       */
      var getCallUrl = function(callId) {
        return window.location.protocol + "//" + window.location.host + "/jitsi/meet/" + callId;
      };

      /**
       * Creates a new call, returns promise with call object when resolved.
       */
      var createCall = function(callId, currentUser, target) {
        var participatntsIds = getCallMembers(currentUser, target).map(function(member) {
          return member.id;
        }).join(";");
        // OK, this call not found - start a new one,
        var callInfo = {
          // for group calls an owner is a group entity
          // (space or room), otherwise it's 1:1 and who
          // started is an owner
          owner: target.group ? target.id : currentUser.id,
          // ownerType can be 'user' for 1:1 calls, 'space'
          // for group call in space, 'chat_room' for group
          // call in Chat room
          ownerType: target.type, // use target type
          provider: self.getType(),
          // tagret's title is a group or user full name
          title: target.title,
          participants: participatntsIds
          // string build from array separated by ';'
        };
        return webConferencing.addCall(callId, callInfo);
      };

      var callWindowName = function(callId) {
        // Window name should be without spaces according Mozilla!
        return self.getType() + "-" + callId;
      };
      
      /**
       * Read a call from the backend storage by an ID. 
       * Additionally check to keep the backend with calls clean of phantom guests - 
       * those who was in the call in the past and stuck for unknown reasons and doesn't let the call to stop.
       */
      var getCall = function(callId, currentUserId) {
        const callProcess = $.Deferred();
        webConferencing.getCall(callId).then(call => {
          // TODO check does call contain only guests and stop it (guests should be removed) if it's older of 3hrs
          const now = Date.now();
          if (now - call.lastDate.time > GUEST_EXPIRATION_MS 
              && call.participants.filter(p => p.state === "joined" && p.type !== GUEST_TYPE).length === 0 
              && call.participants.filter(p => p.state !== "leaved" && p.type === GUEST_TYPE).length > 0) {
            log.debug("Call assumed as expired for guests: " + callId + ", now: " + now + ", date: " + JSON.stringify(call.lastDate));
            webConferencing.updateCall(callId, "stopped").then(call => {
              log.info("Call forsed to stopped (it contains only guests): " + callId + " by " + currentUserId);
              callProcess.resolve(call); // resolve with a freshly stopped call (parties should all leaved and no guests)
            }).catch(err => {
              log.warn("Failed force call to stop (it contains only guests): " + callId, err);
              // Indeed we assume this not as an error and lt it to run.
              callProcess.resolve(call);
            });
          } else {
            callProcess.resolve(call);
          }
        }).catch(err => {
          callProcess.reject(err);
        });
        return callProcess.promise();
      };

      /**
       * Start a call in given context. 
       */
      var startCall = function(context, target) {
        const callProcess = $.Deferred();
        const callId = getCallId(context, target);
        // Open the call window before async requests to avoid browser blocker, then we'll update it with a right URL
        const callWindow = webConferencing.showCallWindow("", callWindowName(callId));
        getCallAppStatus().then(res => {
          if (res.status === "active") {
            getCall(callId, context.currentUser.id).then(call => {
              if (target.type === "chat_room") {
                // Chat room needs members sync to send notifications for call start
                if (call.state === "stopped" || call.participants.length == 0) {
                  // If room call stopped or empty we update all parties in it to sync members 
                  const participants = Object.values(target.members).map(member => {
                    return member.id;
                  });
                  webConferencing.updateParticipants(callId, participants);
                }
              }
              if (call.state === "stopped") {
                // Start the call explicitly if it is in stopped state.
                // This way we will inform all parties about the call start.
                log.info("Call exists but stopped. Starting call: " + callId);
                webConferencing.updateCall(callId, "started").then(call => {
                  log.info("Call started: " + callId + " by " + context.currentUser.id);
                  callProcess.resolve(call);
                }).catch(err => {
                  log.error("Failed start a call: " + callId, err);
                  webConferencing.showError("Failed start a call", webConferencing.errorText(err));
                });
              } else {
                // otherwise, call already running and no need to send notification to its parties
                log.info("Call already running. Joining call: " + callId);
                callProcess.resolve(call);
              }
            }).catch(err => {
              if (err) {
                if (err.code == "NOT_FOUND_ERROR") {
                  createCall(callId, context.currentUser, target).then(call => {
                    log.info("Call created: " + callId);
                    callProcess.resolve(call);
                  });
                } else {
                  log.error("Failed to get call info: " + callId, err);
                  webConferencing.showError("Failed join a call", webConferencing.errorText(err));
                }
              } else {
                log.error("Failed to get call info: " + callId);
                webConferencing.showError("Failed join a call", "Error read call information from the server");
              }
            });
          } else {
            callProcess.reject("The Call App is not active");
          }
        }).catch(err => {
          callProcess.reject("The Call App is temporary unavailable.");
        });

        // We wait for call readiness and invoke start it in the
        // popup window
        callProcess.then(call => {
          const callUrl = getCallUrl(callId);
          if (callWindow.location.href !== callUrl) {
            callWindow.location = callUrl;
            callWindow.document.title = call.title; // TODO was target.title
          }
        }).catch(err => {
          callWindow.close();
          setTimeout(() => {
            webConferencing.showError("Cannot open call page", err); // TODO i18n
          }, 50);
        });
      };

      /**
       * MUST be implemented by a connector provider to build a Call button and
       * call invoked by it. Web Conferencing core provides a context object
       * where following information can be found: - currentUser - username of
       * an user that will run the call - userId - if found, it's 1:1 call
       * context, it's an username of another participant for the call - spaceId -
       * if found, it's space call, it contains a space's pretty name - roomId -
       * if found, it's eXo Chat room call, it contains a room (target) id (e.g.
       * team-we3o23o12eldm) - roomTitle - if roomId found, then roomTitle will
       * contain a human readable title - roomName - if roomId found, then
       * roomName will contain a no-space name of the room for use with Chat
       * APIs or to build connector URLs where need refer a room by its name (in
       * addition to the ID). NOTE: in case of space room, the name will contain
       * the space's pretty name prefixed with 'space-' text. - isGroup - if
       * true, it's a group call, false then 1-one-1 - details - it's
       * asynchronous function to call, it returns jQuery promise which when
       * resolved (done) will provide an object with call information. In
       * general it is a serialized to JSON Java class, extended from
       * IdentityInfo - consult related classes for full set of available bean
       * fields.
       * 
       * This method returns a jQuery promise. When it resolved (done) it should
       * offer a jQuery element of a button(s) container. When rejected
       * (failed), need return an error description text (it may be shown
       * directly to an user), the connector will not be added to the call
       * button and user will not see it.
       */
      this.callButton = function(context, buttonType) {
        var button = $.Deferred();
        if (settings && context && context.currentUser) {
          context.details().then(target => {
            if (!buttonType || buttonType === "vue") {
              const callId = getCallId(context, target);
              const callSettings = {};
              callSettings.target = target;
              callSettings.callId = callId;
              callSettings.context = context;
              callSettings.provider = self;
              callSettings.onCallOpen = () => {
                startCall(context, target);
              };
              callButton.init(callSettings).then(comp => {
                button.resolve(comp);
                getCallState(context, target).then(callState => {
                  // initial state
                  callButton.updateCallState(callId, callState);
                });
              });
              // callButton.initDrawer().then(comp => comp);
              // Resolve with our button - return Vue object here, so it
              // will be appended to Call Button UI in the Platform
            } else if (buttonType === "element") {
              var $button = $("<a title='" + target.title + "' href='javascript:void(0)' class='myCallAction'>" +
                "<i class='uiIconMyCall uiIconVideoPortlet uiIconLightGray'></i>" + "<span class='callTitle'>" +
                self.getCallTitle() + "</span></a>");
              $button.click(function() {
                startCall(context, target);
              });
              $button.data("targetid", target.id);
              button.resolve($button[0]);
            }
          }).catch(err => {
            // On error, we don't show the button for this context
            if (err && err.code == "NOT_FOUND_ERROR") {
              // If target not found, for any reason, we don't need tell it's an
              // error - just no button for the target
              button.reject(err.message);
            } else {
              // For other failures we seems met an error (server or network)
              // and send it as a second parameter,
              // thus the core add-on will be able recognize it and do
              // accordingly (at least log to server log)
              var msg = "Error getting context details";
              log.error(msg, err);
              button.reject(msg, err);
            }
          });
        } else {
          // If not initialized, we don't show the button for this context
          var msg = "Not configured or empty context";
          log.error(msg);
          button.reject(msg);
        }
        // Return a promise, when resolved it will be used by Web Conferencing
        // core to add a button to a required places
        return button.promise();
      };
      
      /**
       * Returns invite link.
       */
      this.getInviteLink = function(call) {
        return getCallUrl(call.id) + "?inviteId=" + call.inviteId;
      };

      /**
       * OPTIONAL method. If implemented, it will be called by Web Conferencing
       * core on addProvider() method. It is assumed that the connector will
       * initialize internals depending on the given context.
       */
      this.init = function(context) {
        var process = $.Deferred();
        // We want initialize call buttons and incoming calls dialog only for
        // portal pages (including Chat, but don't do the work on call pages, e.g. don't ring the call.
        // The settings.isCallPage will be set by a call page only - check it to detect call pages.
        if (eXo && eXo.env && eXo.env.portal && settings && !settings.isCallApp) {
          var currentUserId = webConferencing.getUser().id;
          // Subscribe to user updates (incoming calls will be notified here)
          callButton.initCallPopupList().then(()=> {}); 
          webConferencing.onUserUpdate(currentUserId, update => {
            // This connector cares only about own provider events
            if (update.providerType == self.getType()) {
              var callId = update.callId;
              if (update.eventType == "call_state") {
                // A call state changed (can be 'started', 'stopped', 'paused'
                // (not used for the moment)
                // rely on logic implemented in callButton() here: group call ID
                // starts with 'g/'
                var isGroup = callId.startsWith("g_");
                callButton.updateCallState(callId, update.callState);
                log.trace(">>> User call state updated: " + JSON.stringify(update));
                if (update.callState == "started") {
                  // When call started it means we have an incoming call for
                  // this particular user
                  log.info("Incoming call: " + callId);
                  // Get call details by ID
                  getCall(callId, currentUserId).then(call => {
                    var callerId = call.owner.id;
                    var callerLink = call.owner.profileLink;
                    var callerAvatar = call.owner.avatarLink;
                    const styledOwnerTitle = call.owner.title.bold();
                    var callerMessage = !isGroup ? styledOwnerTitle + " started a Meeting with you." : "A meeting has started in the room " + styledOwnerTitle;
                    call.title = call.owner.title; // for callee the call
                    // Check if current user not already in the call (joined)
                    let canJoinCall = true;
                    for (const part of call.participants) {
                      if (part.id == currentUserId && part.state == "joined") {
                        canJoinCall = false;
                        break;
                      }
                    }
                    if (canJoinCall) {
                      // User can join the call.
                      // Get current user status, we need this to figure out a need of playing ringtone
                      // we'll do for users with status 'Available' or 'Away',
                      // but ones with 'Do Not Disturb' will not hear an incoming ring.
                      webConferencing.getUserStatus(currentUserId).then(user => {
                        // Build a call popover
                        // We use the popover promise to finish initialization on its progress state, on
                        // resolved (done) to act on accepted call and on rejected (fail) on declined call.
                        let playRingtone = !user || user.status == "available" || user.status == "away";
                        callButton.initCallPopup(callId, callerId, callerLink, callerAvatar, callerMessage, playRingtone).then(popup => {
                          popup.onAccepted(() => {
                            log.info("Call accepted: " + callId + " by user: " + currentUserId);
                            const callUrl = getCallUrl(callId);
                            const callWindow = webConferencing.showCallWindow(callUrl, callWindowName(callId));
                            callWindow.document.title = call.title;
                          });
                          popup.onRejected(() => {
                            log.trace("<<< User declined just " + update.callState + " call " + callId);
                            if (isGroup) {
                              // We need inform other windows of the user in the browser to close popups in them
                              webConferencing.updateCall(callId, "leaved").then(() => {
                                log.info("Call declined: " + callId + " by user " + currentUserId);
                              });
                            } else {
                              if (update.callState !== "stopped" && update.callState !== "joined") {
                                // Delete the call if it is not group one, not already stopped and wasn't joined -
                                // a group call will be deleted automatically when last party leave it.
                                webConferencing.deleteCall(callId).then(() => {
                                  log.info("Call deleted: " + callId + " by user " + currentUserId);
                                }).catch(err => {
                                  if (err && (err.code === "NOT_FOUND_ERROR")) {
                                    // already deleted
                                    log.trace("<< Call not found " + callId);
                                  } else {
                                    log.error("Failed to stop call: " + callId, err);
                                    webConferencing.showError("Error stopping call", webConferencing.errorText(err));
                                  }
                                });
                              }
                            }
                          });
                        }).catch(err => {
                          log.error("Error openning call popup for " + callId, err);
                          webConferencing.showError("Filed to open incoming call popup", webConferencing.errorText(err));
                        });
                      }).catch(err => {
                        log.error("Failed to get user status: " + currentUserId, err);
                        if (err) {
                          webConferencing.showError("Incoming call error", webConferencing.errorText(err));
                        } else {
                          webConferencing.showError("Incoming call error", "Error read user status information from the server");
                        }
                      });
                    } else {
                      log.trace("User already in the started call: " + currentUserId + " call: " + callId);
                    }
                  }).catch(err => {
                    log.error("Failed to get call info: " + callId, err);
                    if (err) {
                      webConferencing.showError("Incoming call error", webConferencing.errorText(err));
                    } else {
                      webConferencing.showError("Incoming call error", "Error read call information from the server");
                    }
                  });
                } else if (update.callState == "stopped") {
                  log.info("Call stopped remotelly: " + callId);
                  // Hide call popover for this call, if any callWindow
                  callButton.closeCallPopup(callId);
                }
              } else if (update.eventType == "call_joined") {
                log.debug("User call joined: " + update.callId + ", participant: " + update.part.id);
                // If user has incoming popup open for this call (in several
                // user's windows/clients), then close it
                if (currentUserId == update.part.id) {
                  callButton.updateCallState(callId, "joined");
                  callButton.closeCallPopup(callId);
                }
              } else if (update.eventType == "call_leaved") {
                log.debug("User call leaved: " + update.callId + ", participant: " + update.part.id);
                if (currentUserId === update.part.id) {
                  callButton.updateCallState(callId, "leaved");
                  callButton.closeCallPopup(callId);
                }
              } else {
                log.debug("Unexpected user update: " + JSON.stringify(update));
              }
            } // it's other provider type - skip it
          }, err => {
            log.error("Failed to listen on user updates", err);
          });
        }
        process.resolve();
        return process.promise();
      };

      /**
       * OPTIONAL method. If implemented, it will cause showing a settings
       * button in Web Conferencing Administration page and when button clicked
       * this method will be invoked. In this method you can show a popup to an
       * admin user with provider specific settings.
       */
      this.showSettings = function() {
        // load HTML with settings
        var $popup = $("#jitsi-settings-popup");
        if ($popup.length == 0) {
          $popup = $("<div class='uiPopupWrapper' id='jitsi-settings-popup' style='display: none;'><div>");
          $(document.body).append($popup);
        }
        $popup.load("/jitsi/settings", function(content, textStatus) {
          if (textStatus == "success" || textStatus == "notmodified") {
            var $settings = $popup.find(".settingsForm");
            // TODO fill settings form and handle its changes to update the
            // settings on the server (e.g. by using your provider REST service)
            // .....
            // Show the settings popup when ready
            $popup.show();
          } // otherwise it's error
        });
      };

      // ****** Custom methods required by the connector itself or dependent on
      // it modules ******

      /**
       * Set connector settings from the server-side. Will be called by script
       * of JitsiPortlet class.
       */
      this.configure = function(mySettings) {
        settings = mySettings;
      };

      /**
       * Used in the callButton() code. Also can be used by dependent modules
       * (e.g. when need run a call page in a window).
       */
      this.getApiClientId = function() {
        if (settings) {
          return settings.apiClientId;
        }
      };

      /**
       * Used in the callButton() code. Also can be used by dependent modules
       * (e.g. when need run a call page in a window).
       */
      this.getUrl = function() {
        if (settings) {
          return settings.url;
        }
      };

      /**
       * Used in the callButton() code. Also can be used by dependent modules
       * (e.g. when need run a call page in a window).
       */
      this.getCallTitle = function() {
        return "Jitsi Call"; // TODO Do we need it?
      };

      /**
       * Sample function used by JitsiIMRenderer to show how IM type renderer
       * can be initialized.
       */
      this.initSettings = function(mySettings) {
        // initialize IM type settings UI
      };

      var getCallState = function(context, target) {
        var gettingProcess = new Promise(function(resolve) {
          const callId = getCallId(context, target);
          getCall(callId, context.currentUser.id).then(call => {
            let user;
            if (call.state === "started") {
              for (const participant of call.participants) {
                if (participant.id === context.currentUser.id) {
                  user = participant;
                  break;
                }
              }
            }
            if (user) {
              resolve(user.state);
            } else {
              resolve(call.state);
            }
          }).catch(err => {
            if (err) {
              if (err.code === "NOT_FOUND_ERROR") {
                resolve(err.code);
              } else {
                log.error("Failed to get call info: " + callId, err);
                webConferencing.showError("Getting call error", webConferencing.errorText(err));
              }
            } else {
              log.error("Failed to get call info: " + callId);
              webConferencing.showError("Getting call error", "Error read call information from the server");
            }
          });
        });
        return gettingProcess;
      }
    };

    var provider = new JitsiProvider();

    // Add Jitsi provider into webConferencing object of global eXo namespace
    // (for non AMD uses)
    if (globalWebConferencing) {
      globalWebConferencing.jitsi = provider;
    } else {
      log.warn("eXo.webConferencing not defined");
    }

    log.trace("< Loaded at " + location.origin + location.pathname);
    return provider;
  } else {
    window.console &&
      window.console
        .log("WARN: webConferencing not given and eXo.webConferencing not defined. Jitsi provider registration skipped.");
  }
})($, webConferencing, callButton);