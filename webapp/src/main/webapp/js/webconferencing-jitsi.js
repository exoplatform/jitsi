/**
 * Jitsi provider module for Web Conferencing. This script will be used to add a
 * provider to Web Conferencing module and then handle calls for portal
 * user/groups.
 */
(function($, webConferencing, callButton) {
  "use strict";
  const EVENT_ROOM_SELECTION_CHANGED = "exo-chat-selected-contact-changed";

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

      var self = this;
      var settings;
      var jitsiProviderCallButton = null;

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
        return callId;
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


      var startCall = function(context, target) {
        var callProcess = $.Deferred();
        var callId = getCallId(context, target);
        // Open call window
        var callWindow;

        webConferencing.getCall(callId).done(function(call) {
          // Call already running - join it
          log.info("Call already exists. Joining call: " + callId);
          // For grop calls
          if (call.state === "stopped" && (target.type === "space" || target.type === "chat_room")) {
            webConferencing.updateCall(callId, "started").done(function() {
              log.info("Updated call state to started");
            });
          }
          // Open new call window
          callWindow = webConferencing.showCallPopup("", target.title);
          callProcess.resolve(call);
        }).fail(function(err) {
          if (err) {
            if (err.code == "NOT_FOUND_ERROR") {
              createCall(callId, currentUser, target).done(function(call) {
                log.info("Call created: " + callId);
                // Open new call window
                callWindow = webConferencing.showCallPopup("", target.title);
                callProcess.resolve(call);
              });
            } else {
              log.error("Failed to get call info: " + callId, err);
              webConferencing.showError("Joining call error", webConferencing.errorText(err));
            }
          } else {
            log.error("Failed to get call info: " + callId);
            webConferencing.showError("Joining call error", "Error read call information from the server");
          }
        });
        // We wait for call readiness and invoke start it in the
        // popup window
        callProcess.done(function(call) {
          callWindow.location = getCallUrl(callId);
          callWindow.document.title = target.title;

          // TODO: check if call loaded and if not - leave the call
          // This solution is not finished yet
          /*
          callWindow.addEventListener('load', function() {
            if (!callWindow.callLoaded) {
              webConferencing.updateCall(callId, "leaved").done(function(){
                log.error("Call " + callId + " hasn't been loaded. Left the call.");
              });
            }
          });*/

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
          context.details().done(
            function(target) {
              if (buttonType === "vue") {
                const callSettings = {};
                callSettings.target = target;
                callSettings.context = context;
                callSettings.provider = self;
                callSettings.onCallOpen = function() {
                  startCall(context, target);
                };
                // callSettings.callWindow = callWindow;
                callButton.init(callSettings).then(jitsiCallButton => {
                  jitsiProviderCallButton = jitsiCallButton;
                  button.resolve(jitsiCallButton);
                });
                // Resolve with our button - return Vue object here, so it
                // will be appended to Call Button UI in the Platform
              } else {
                var $button = $("<a title='" + target.title + "' href='javascript:void(0)' class='myCallAction'>" +
                  "<i class='uiIconMyCall uiIconVideoPortlet uiIconLightGray'></i>" + "<span class='callTitle'>" +
                  self.getCallTitle() + "</span></a>");
                $button.click(function() {
                  startCall(context, target);
                });
                $button.data("targetid", target.id);
                button.resolve($button);
              }
            }).fail(function(err) {
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
       * Helper method to build an incoming call popup.
       */
      var acceptCallPopover = function(callerLink, callerAvatar, callerMessage, playRingtone) {
        log.trace(">> acceptCallPopover '" + callerMessage + "' caler:" + callerLink + " avatar:" + callerAvatar);
        var process = $.Deferred();
        var $call = $("div.uiIncomingCall");
        // Remove previous dialogs (if you need handle several incoming at the
        // same time - implement special logic for this)
        if ($call.length > 0) {
          try {
            // By destroying a dialog, it should reject its incoming call
            $call.dialog("destroy");
          } catch (e) {
            log.error("acceptCallPopover: error destroing previous dialog ", e);
          }
          $call.remove();
        }
        $call = $("<div class='uiIncomingCall' title='Incoming call'></div>");
        $call.append($("<div class='messageAuthor'><a target='_blank' href='" + callerLink + "' class='avatarMedium'>" +
          "<img src='" + callerAvatar + "'></a></div>" + "<div class='messageBody'><div class='messageText'>" + callerMessage +
          "</div></div>"));
        $(document.body).append($call);
        $call.dialog({
          resizable: false,
          height: "auto",
          width: 400,
          modal: false,
          buttons: {
            "Answer": function() {
              process.resolve("accepted");
              $call.dialog("close");
            },
            "Decline": function() {
              process.reject("declined");
              $call.dialog("close");
            }
          }
        });
        $call.on("dialogclose", function(event, ui) {
          if (process.state() == "pending") {
            process.reject("closed");
          }
        });
        process.notify($call);
        if (playRingtone) {
          // Start ringing incoming sound only if requested (depends on user
          // status)
          var $ring = $("<audio loop autoplay style='display: none;'>" // controls
            +
            "<source src='/jitsi/resources/audio/incoming.mp3' type='audio/mpeg'>" +
            "Your browser does not support the audio element.</audio>");
          $(document.body).append($ring);
          process.fail(function() {
            if ($call.callState != "joined") {
              var $cancel = $("<audio autoplay style='display: none;'>" // controls
                +
                "<source src='/jitsi/resources/audio/incoming_cancel.mp3' type='audio/mpeg'>" +
                "Your browser does not support the audio element.</audio>");
              $(document.body).append($cancel);
              setTimeout(function() {
                $cancel.remove();
              }, 2500);
            }
          });
          process.always(function() {
            // Stop incoming ringing on dialog completion
            $ring.remove();
          });
        }
        return process.promise();
      };

      /**
       * OPTIONAL method. If implemented, it will be called by Web Conferencing
       * core on addProvider() method. It is assumed that the connector will
       * initialize internals depending on the given context.
       */
      this.init = function(context) {
        var process = $.Deferred();
        if (eXo && eXo.env && eXo.env.portal) {
          // We want initialize call buttons and incoming calls dialog only for
          // portal pages (including Chat)
          var currentUserId = webConferencing.getUser().id;
          // Incoming call popup (embedded into the current page), it is based
          // on jQueryUI dialog widget
          var $callPopup;
          var closeCallPopup = function(callId, state) {
            if ($callPopup && $callPopup.callId && $callPopup.callId == callId) {
              if ($callPopup.is(":visible")) {
                // Set state before closing the dialog, it will be used by
                // promise failure handler
                $callPopup.callState = state;
                $callPopup.dialog("close");
              }
            }
          };
          // When call is already running we want lock a call button and then
          // unlock on stop.
          // As we may find several call buttons on eXo pages, need update only
          // related to the call.
          // On space pages (space call button) we can rely on call ownerId (it
          // will be a space pretty_name),
          // for Chat page we need use its internal room name to distinguish
          // rooms and ownerId for users.
          var lockCallButton = function(targetId, callId) {
            var $buttons = $(".myCallAction");
            $buttons.each(function() {
              var $button = $(this);
              if ($button.data("targetid") == targetId) {
                if (!$button.hasClass("callDisabled")) {
                  // log.trace(">> lockCallButton " + targetId);
                  // TODO: add class (removed for testing)
                  // $button.addClass("callDisabled");
                  $button.data("callid", callId);
                }
              }
            });
          };
          var unlockCallButton = function(callId) {
            var $buttons = $(".myCallAction");
            $buttons.each(function() {
              var $button = $(this);
              if ($button.data("callid") == callId) {
                // log.trace(">> unlockCallButton " + callId + " " +
                // $button.data("targetid"));
                $button.removeClass("callDisabled");
                $button.removeData("callid"); // we don't touch targetid, it
                // managed by callButton()
              }
            });
          };
          // Subscribe to user updates (incoming calls will be notified here)
          webConferencing.onUserUpdate(currentUserId, function(update) {
            // This connector cares only about own provider events
            if (update.providerType == self.getType()) {
              var callId = update.callId;
              if (update.eventType == "call_state") {
                // A call state changed (can be 'started', 'stopped', 'paused'
                // (not used for the moment)
                // rely on logic implemented in callButton() here: group call ID
                // starts with 'g/'
                var isGroup = callId.startsWith("g_");
                log.trace(">>> User call state updated: " + JSON.stringify(update));
                if (update.callState == "started") {
                  // When call started it means we have an incoming call for
                  // this particular user
                  log.info("Incoming call: " + callId);
                  // Get call details by ID
                  webConferencing.getCall(callId).done(
                    function(call) {
                      var callerId = call.owner.id;
                      var callerLink = call.owner.profileLink;
                      var callerAvatar = call.owner.avatarLink;
                      var callerMessage = call.owner.title + " is calling you...";
                      var callerRoom = callerId;
                      call.title = call.owner.title; // for callee the call
                      // title is a caller
                      // name
                      // Get current user status, we need this to figure out a
                      // need of playing ringtone
                      // we'll do for users with status 'Available' or 'Away',
                      // but ones with 'Do Not Disturb' will not hear an
                      // incoming ring.
                      webConferencing.getUserStatus(currentUserId).done(
                        function(user) {
                          // Build a call popover
                          var popover = acceptCallPopover(callerLink, callerAvatar, callerMessage, !user ||
                            user.status == "available" || user.status == "away");
                          // We use the popover promise to finish
                          // initialization on its progress state, on
                          // resolved (done)
                          // to act on accepted call and on rejected (fail)
                          // on declined call.
                          popover.progress(function($callDialog) {
                            // Finish initialization...
                            $callPopup = $callDialog;
                            // And some extra info to distinguish the popup
                            $callPopup.callId = callId;
                            $callPopup.callState = update.callState;
                          });
                          popover.done(function(msg) {
                            // User accepted the call...
                            log.info("User " + msg + " call: " + callId);
                            var longTitle = self.getTitle() + " " + self.getCallTitle();

                            var callUrl = window.location.protocol + "//" + window.location.host + "/jitsi/meet/" + encodeURIComponent(callId);

                            var callWindow = webConferencing.showCallPopup(callUrl, longTitle);
                            callWindow.document.title = call.title;
                            // Optionally, we may invoke a call window to
                            // initialize the call.
                            // First wait the call window loaded
                            $(callWindow).on("load", function() {
                              log.debug("Call page loaded: " + callId);
                              lockCallButton(update.owner.id, callId);
                            });
                          });
                          popover.fail(function(err) {
                            // User rejected the call, call was stopped or
                            // joined on another client/page.
                            if (!isGroup && $callPopup.callState != "stopped" && $callPopup.callState != "joined") {
                              // Delete the call if it is not group one, not
                              // already stopped and wasn't joined -
                              // a group call will be deleted automatically
                              // when last party leave it.
                              log.trace("<<< User " + err + ($callPopup.callState ? " just " + $callPopup.callState : "") +
                                " call " + callId + ", deleting it.");
                              webConferencing.deleteCall(callId).done(function() {
                                log.info("Call deleted: " + callId);
                              }).fail(function(err) {
                                if (err && (err.code == "NOT_FOUND_ERROR")) {
                                  // already deleted
                                  log.trace("<< Call not found " + callId);
                                } else {
                                  log.error("Failed to stop call: " + callId, err);
                                  webConferencing.showError("Error stopping call", webConferencing.errorText(err));
                                }
                              });
                            }
                          });
                        }).fail(
                        function(err) {
                          log.error("Failed to get user status: " + currentUserId, err);
                          if (err) {
                            webConferencing.showError("Incoming call error", webConferencing.errorText(err));
                          } else {
                            webConferencing.showError("Incoming call error",
                              "Error read user status information from the server");
                          }
                        });
                    }).fail(function(err) {
                    log.error("Failed to get call info: " + callId, err);
                    if (err) {
                      webConferencing.showError("Incoming call error", webConferencing.errorText(err));
                    } else {
                      webConferencing.showError("Incoming call error", "Error read call information from the server");
                    }
                  });
                } else if (update.callState == "stopped") {
                  log.info("Call stopped remotelly: " + callId);
                  // Hide call popover for this call, if any
                  closeCallPopup(callId, update.callState);
                  // Unclock the call button
                  unlockCallButton(callId);
                }
              } else if (update.eventType == "call_joined") {
                log.debug("User call joined: " + update.callId);
                // If user has incoming popup open for this call (in several
                // user's windows/clients), then close it
                if (currentUserId == update.part.id) {
                  closeCallPopup(callId, "joined");
                }
              } else if (update.eventType == "call_leaved") {
                log.debug("User call leaved: " + update.callId);
                // When user leaves a call, we unlock his button, thus it will
                // be possible to join the call again -
                // actual for group conversations.
                if (currentUserId == update.part.id) {
                  unlockCallButton(callId);
                }
              } else {
                log.debug("Unexpected user update: " + JSON.stringify(update));
              }
            } // it's other provider type - skip it
          }, function(err) {
            log.error("Failed to listen on user updates", err);
          });

          // Handle an event when select other contacts in chat
          document.addEventListener(EVENT_ROOM_SELECTION_CHANGED, function (target) {
            self.replaceVueButton(target);
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
        return "Jitsi Call"; // TODO i18n
      };

      /**
       * Sample function used by JitsiIMRenderer to show how IM type renderer
       * can be initialized.
       */
      this.initSettings = function(mySettings) {
        // initialize IM type settings UI
      };

      // delete old and add new vue button
      this.replaceVueButton = async function (target) {
        if (target && target.detail) {
          if (webConferencing && provider) {
            let chat = webConferencing.getChat();
            let callButtonContext = await webConferencing.getCallContext();

            if (callButtonContext && jitsiProviderCallButton) {
              if (chat) {
                //destroy old jitsi button
                jitsiProviderCallButton.$destroy();
                log.trace("Selected the other contact in chat");

                let roomId = target.detail.user;
                let roomTitle = target.detail.fullName;
                let isSpace = target.detail.type === "s"; // roomId && roomId.startsWith("space-");
                let isRoom = target.detail.type === "t"; // roomId && roomId.startsWith("team-");
                let isGroup = isSpace || isRoom;
                let isUser = !isGroup && target.detail.type === "u";

                // It is a logic used in Chat, so reuse it here:
                let roomName = roomTitle.toLowerCase().split(" ").join("_");

                callButtonContext.roomId = roomId;
                callButtonContext.roomName = roomName; // has no sense for team rooms, but for spaces it's pretty_name
                callButtonContext.roomTitle = roomTitle;
                callButtonContext.isGroup = isGroup;
                callButtonContext.isSpace = isSpace;
                callButtonContext.isRoom = isRoom;
                callButtonContext.isUser = isUser;

                callButtonContext.details = function () {
                  let data = $.Deferred();
                  if (isGroup) {
                    if (isSpace) {
                      let spaceId = roomName; // XXX no other way within Chat
                      chat.getSpaceInfoReq(spaceId).done(function (space) {
                        data.resolve(space);
                      }).fail(function (err) {
                        log.trace("Error getting space info " + spaceId + " for chat context", err);
                        data.reject(err);
                      });
                    } else if (isRoom) {
                      eXo.chat.getUsers(roomId).done(function (users) {
                        var unames = [];
                        for (var i = 0; i < users.length; i++) {
                          var u = users[i];
                          if (u && u.name && u.name != "null") {
                            unames.push(u.name);
                          }
                        }
                        chat.getRoomInfoReq(roomId, roomTitle, unames).done(function (info) {
                          data.resolve(info);
                        }).fail(function (err) {
                          log.trace("Error getting Chat room info " + roomName + "/" + roomId + " for chat context", err);
                          data.reject(err);
                        });
                      }).fail(function (err) {
                        log.trace("Error getting Chat room users " + roomId + " for chat context", err);
                        data.reject("Error reading Chat room users for " + roomId);
                      });
                    } else {
                      data.reject("Unexpected context chat type for " + roomTitle);
                    }
                  } else {
                    // roomId is an user name for P2P chats
                    chat.getUserInfoReq(roomId).done(function (user) {
                      data.resolve(user);
                    }).fail(function (err) {
                      log.trace("Error getting user info " + roomId + " for chat context", err);
                      data.reject(err);
                    });
                  }
                  return data.promise();
                }

                // Create the new vue button
                jitsiProviderCallButton = await self.callButton(callButtonContext, "vue");

                // Add the new vue button
                jitsiProviderCallButton.$mount("#call-button-container");
              } else {
                log.warn("No chat from web conferencing");
              }
            }
          }
        } else {
          log.warn("No details provided for Chat room");
        }
      };
    }

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