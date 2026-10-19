import JitsiMeetButton from './components/JitsiMeetButton.vue';
import CallPopup from './components/CallPopup.vue';

Vue.component('jitsi-meet-button', JitsiMeetButton);
const vuetify = new Vuetify(eXo.env.portal.vuetifyPreset);

// getting language of user
const lang = (window.eXo && eXo.env && eXo.env.portal && eXo.env.portal.language) || 'en';
const localePortlet = 'locale.jitsi';
const resourceBundleName = 'Jitsi';
const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/${localePortlet}.${resourceBundleName}-${lang}.json`;
const log = webConferencing.getLog('jitsi');

// An incoming call is alerted for 1 minute at most, unless the server sets another timeout
const DEFAULT_INCOMING_CALL_TIMEOUT = 60000;

/**
 * How long, in milliseconds, an incoming call is alerted: the server notifies the participants who missed the call
 * when it is over.
 *
 * @returns {number} the incoming call timeout
 */
function incomingCallTimeout() {
  return webConferencing.getIncomingCallTimeout && webConferencing.getIncomingCallTimeout() || DEFAULT_INCOMING_CALL_TIMEOUT;
}

const callStates = new Map();
const callPopups = new Map();

export function init(settings) {
  // getting locale ressources
  const parentContainer = document.querySelector('.leftHeaderDrawer');
  if (parentContainer) {
    parentContainer.addEventListener('click', e => {
      if (e.target.classList.contains('backButton')
        && e.target.parentElement.classList.contains('leftHeaderDrawer')) {
        const container = document.querySelector('.single-btn-container');
        const button = document.querySelector('.jitsiCallAction');
        if (container && button) {
          container.removeChild(button);
        }
      }
    });
  }
  return exoi18n.loadLanguageAsync(lang, url).then((i18n) => {
    // init Vue app when locale ressources are ready
    const comp = new Vue({
      data() {
        return {
          callSettings: settings
        };
      },
      created() {
        if (!callStates.has(this.callSettings.callId)) {
          callStates.set(this.callSettings.callId, new Map());
        }
        // different buttons for the same call states
        const statesForTheSameCall = callStates.get(this.callSettings.callId);
        statesForTheSameCall.set(this.callSettings.context.parentClasses, {
          setCallState: this.setCallState,
          getCallState: this.getCallState
        });
      },
      methods: {
        setCallState: function(callState) {
          this.$set(this.callSettings, 'callState', callState);
        },
        getCallState: function() {
          return this.callSettings.callState;
        }
      },
      render: (h) =>
        h(JitsiMeetButton, {
          props: {
            callSettings: settings,
            i18n: i18n,
            language: lang,
            resourceBundleName: resourceBundleName,
          }
        }),
      i18n,
      vuetify,
    });
    return comp;
  });
}

export function updateCallState(callId, state) {
  log.trace(`>>> updateCallState for ${  callId  } state: ${  state}`);
  if (state === 'started') {
    savePopupLoader(callId);
  }
  const buttonStates = callStates.get(callId);
  if (buttonStates) {
    buttonStates.forEach((stateHandler) => {
      stateHandler.setCallState(state);
    });
  }
}

function savePopupLoader(callId) {
  let callPopup = callPopups.get(callId);
  if (!callPopup) {
    let popupResolve = null;
    const popupLoading = new Promise((resolve) => {
      popupResolve = resolve;
    });
    let resolved = false;
    callPopup = {
      loader: popupLoading,
      resolve: (popup) => {
        if (resolved) {
          log.trace(`>> Call popup already resolved for ${  callId}`);
        } else {
          resolved = true; 
          popupResolve(popup);
        }
      }
    };
    // Add sooner when call state to come
    callPopups.set(callId, callPopup);
    log.trace(`>> Save call popup for ${  callId}`);    
  } else {
    log.trace(`>> Call popup already loading for ${  callId}`);
  }
}

export function initCallPopup(
  callId,
  callerId,
  callerLink,
  callerAvatar,
  callerMessage,
  playRingtone) {

  const currentUserId = webConferencing.getUser().id;
      
  // Ring ID should be unique per a Platform instance
  const ringId = `jitsi-call-ring-${window.location.host}-${callerId}`;
  if (playRingtone) {
    // TODO We need play a single ringtone per page - it will play for at least one incoming call
    const callRinging = localStorage.getItem(ringId);
    if (!callRinging || Date.now() - callRinging > 5000) {
      log.trace(`>>> Call start ringing: ${  callId  } for ${  currentUserId}`);
      // if not rnging or ring flag too old (for cases of crashed browser page w/o work in process.always below)
      localStorage.setItem(
        ringId,
        Date.now()
      ); // set it quick as possible to avoid race conditions
    } else {
      playRingtone = false;
      log.trace(`>>> Call already ringing: ${  callId  } for ${  currentUserId}`);
    }
  }
  
  return exoi18n.loadLanguageAsync(lang, url).then((i18n) => {
    const container = document.createElement('div');
    // TODO why we need an ID unique per page?
    document.body.appendChild(container);
    let onAccepted;
    let onRejected;
    let alertTimeoutId;
    const comp = new Vue({
      el: container,
      components: {
        CallPopup
      },
      data() {
        return {
          isDialogVisible: true,
          callerId: callerId,
          avatar: callerAvatar,
          callerMessage: callerMessage,
          playRingtone: playRingtone
        };
      },
      mounted() {
        alertTimeoutId = setTimeout(() => {
          log.info(`Incoming call alert timed out: ${  callId  } user: ${  currentUserId}`);
          closeCallPopup(callId);
        }, incomingCallTimeout()); // Close the alert if the user hasn't answered, without declining the call
      },
      i18n,
      vuetify,
      render: function(h) {
        return h(CallPopup, {
          props: {
            isDialogVisible: this.isDialogVisible,
            caller: this.callerId,
            avatar: this.avatar,
            callerMessage: this.callerMessage,
            playRingtone: this.playRingtone,
            i18n
          },
          on: {
            accepted: doAccept,
            rejected: doReject
          }
        });
      }
    });
    function doAccept() {
      closeCallPopup(callId);
      if (onAccepted) {
        onAccepted();
      }
    }
    
    function doReject(isClosed) {
      closeCallPopup(callId);
      if (onRejected) {
        onRejected(isClosed);
      }
    }
    const popup = {
      callId,
      callerId,
      close: function() {
        clearTimeout(alertTimeoutId); // Clear the alert timeout for the call
        if (playRingtone) {
          localStorage.removeItem(ringId);
        }
        comp.isDialogVisible = false;
        comp.$destroy();
      },
      onAccepted: function(callback) {
        onAccepted = callback;
      },
      onRejected: function(callback) {
        onRejected = callback;
      }
    };
    const callPopup = callPopups.get(callId);
    if (callPopup) {
      callPopup.resolve(popup);
    } else {
      log.trace(`Call popup loader not found for the call: ${callId}`);
    }
    return popup;
  });
}

export function closeCallPopup(callId) {
  const callPopup = callPopups.get(callId);
  if (callPopup) {
    callPopups.delete(callId);
    callPopup.loader.then(popup => {
      log.trace(`>>> Close popup for the call: ${callId}`);
      popup.close();
    });
  }
}
