<template>
  <div class="VuetifyApp">
    <v-app v-if="isDialogVisible">
      <v-dialog
        ref="incoming"
        :retain-focus="false"
        v-model="isDialogVisible"
        content-class="incoming-dialog"
        no-click-animation
        persistent
        hide-overlay
        width="430">
        <v-card>
          <v-avatar color="#578dc9" width="70" height="70">
            <img :src="avatar" :alt="caller" >
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
            <v-btn class="ma-2 decline-button" outlined fab color="#b1b5b9" @click="passRejected()">
              <i class="uiIconPopupClose"></i>
            </v-btn>
            <span class="button-title" @click="passRejected()">
              {{ i18n.te("UICallPopup.label.ignore")
                ? $t("UICallPopup.label.ignore")
              : "Ignore" }}
            </span>
            <audio ref="audio" class="audio-call-popup" style="display: none" loop preload="auto">
              <source src="/jitsi/resources/audio/ringtone_exo-1.m4a" >
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
  name: "CallPopup",
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
    this.state = "shown";
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
        console.log("Error playing ringtone for Jitsi call: " + this.caller, e);
      }
    }
  },
  methods: {
    passAccepted() {
      if (this.state === "shown") {
        this.state = "closed";
        this.$emit("accepted");
        stopAudio(this.$refs.audio);
      }
    },
    passRejected() {
      if (this.state === "shown") {
        this.state = "closed";
        this.$emit("rejected");
        stopAudio(this.$refs.audio);
      }
    }
  }
};
</script>
<style scoped lang="less">
// .VuetifyApp {
.spacer {
  flex-grow: unset !important;
  width: 12%;
}
//.theme--light.v-card > .v-card__text
.v-dialog__content {
  justify-content: center;
  left: unset;
  position: static;
  margin: 0px 10px 20px;
  height: fit-content;
  width: fit-content;
  height: -moz-fit-content;
  width: -moz-fit-content;
}
.v-dialog {
  border-radius: 2px;
  height: 160px;
  .v-sheet.v-card {
    border-radius: 2px;
    height: 160px;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    grid-template-rows: repeat(2, 80px);
    grid-auto-rows: 10px;
    width: initial;
    [class^="uiIcon"].start-call {
      position: absolute;
      top: 15%;
      left: 24%;
      &::before {
        content: "\e61c";
        color: #fb8e18;
        font-size: 20px;
      }
    }
    .v-avatar {
      border-radius: 50% !important;
      align-self: center;
      justify-self: center;
      top: 15px;
      left: -15px;
    }
    .v-card__text {
      grid-column: 2 / span 2;
      grid-row: 1 / span 1;
      padding: 20px 15px 20px 0px;
      font-size: 16px;
      color: #333;
    }
    .v-card__actions {
      grid-column: 1 / span 3;
      grid-row: 2 / span 1;
      padding: 8px 0px !important;
      display: flex;
      justify-content: center;
      .v-btn {
        padding: 0;
        height: 50px;
        width: 50px;
        border: 1px solid;
        margin-left: 0px !important;
        .v-btn__content {
          [class^="uiIcon"] {
            position: relative;
            height: 25px;
            width: 50px;
            &::before {
              position: absolute;
              font-size: 24px;
              right: 50%;
              transform: translateX(50%);
            }
          }
        }
        &.accept-button {
          .uiIconPopupPhone {
            color: white;
            &::before {
              content: "\e92b";
            }
          }
        }
        &.decline-button {
          //&:before {
          //  color: transparent;
          //}
          .uiIconPopupClose {
            opacity: 1;
            &::before {
              color: #aeb3b7;
              content: "\e9d2";
            }
          }
        }
      }
      .button-title {
        font-weight: 700;
        font-size: 14px;
        cursor: pointer;
        color: #333;
      }
    }
  }
}
// }
</style>

<style>
#vuetify-apps {
  padding-top: 20px;
  display: flex;
  flex-flow: column;
  align-items: flex-end;
  justify-content: flex-end;
  width: 100%;
  min-height: 100vh;
}
.incoming-dialog {
  border: 1px solid #aeb3b7;
}
.VuetifyApp.call-popup {
  position: absolute;
  width: 100%;
  height: 100%;
  overflow-y: scroll;
}
</style>