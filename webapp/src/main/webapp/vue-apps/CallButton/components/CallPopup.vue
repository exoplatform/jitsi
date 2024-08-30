<template>
  <div class="VuetifyApp">
    <v-app v-if="isDialogVisible">
      <v-dialog
        ref="incoming"
        id="incomingCallPopup"
        :retain-focus="false"
        v-model="isDialogVisible"
        content-class="incoming-dialog"
        no-click-animation
        persistent
        hide-overlay
        width="430">
        <v-card>
          <v-avatar
            color="#476A9C"
            width="70"
            height="70">
            <img :src="avatar" :alt="caller">
          </v-avatar>
          <i class="uiIconSocPhone start-call"></i>
          <v-card-text color="#333" v-html="callerMessage" />
          <v-card-actions color="#333">
            <v-btn
              class="ma-2 accept-button"
              color="#2eb58c"
              elevation="0"
              fab
              @click="passAccepted">
              <i class="uiIconPopupPhone"></i>
            </v-btn>
            <span class="button-title" @click="passAccepted">
              {{ i18n.te("UICallPopup.label.join")
                ? $t("UICallPopup.label.join")
                : "Join" }}
            </span>
            <v-spacer />
            <v-btn
              class="ma-2 decline-button"
              outlined
              fab
              color="#b1b5b9"
              @click="passRejected()">
              <i class="uiIconPopupClose"></i>
            </v-btn>
            <span class="button-title" @click="passRejected()">
              {{ i18n.te("UICallPopup.label.ignore")
                ? $t("UICallPopup.label.ignore")
                : "Ignore" }}
            </span>
            <audio
              ref="audio"
              class="audio-call-popup"
              style="display: none"
              loop
              preload="auto">
              <source src="/jitsi/resources/audio/ringtone_exo-1.m4a">
              <p>"Your browser does not support the audio element</p>
            </audio>
          </v-card-actions>
        </v-card>
      </v-dialog>
    </v-app>
  </div>
</template>

<script>
function stopAudio(audio) {
  if (audio) {
    audio.pause();
    audio.currentTime = 0;
  }
}

export default {
  name: 'CallPopup',
  props: {
    isDialogVisible: {
      type: Boolean,
      required: true,
      default: false
    },
    caller: {
      type: String,
      required: true
    },
    avatar: {
      type: String,
      required: true
    },
    callerMessage: {
      type: String,
      required: true
    },
    playRingtone: {
      type: Boolean,
      required: true
    },
    i18n: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      state: null
    };
  },
  mounted() {
    this.state = 'shown';
    if (this.playRingtone) {
      try {
        //audio.muted = true;
        // TODO this would help to fix "Uncaught (in promise) DOMException: play() failed because the user didn't interact with the document first."
        //document.body.addEventListener("mousemove", function () {
        //  audio.play();
        //});
        this.$refs.audio.play();
      } catch (e) {
        // TODO we need remove this popup flag from local storage to let others to play
        console.error(`Error playing ringtone for Jitsi call: ${  this.caller}`, e);
      }
    }
  },
  methods: {
    passAccepted() {
      if (this.state === 'shown') {
        this.state = 'closed';
        this.$emit('accepted');
        stopAudio(this.$refs.audio);
      }
    },
    passRejected() {
      if (this.state === 'shown') {
        this.state = 'closed';
        this.$emit('rejected');
        stopAudio(this.$refs.audio);
      }
    }
  }
};
</script>