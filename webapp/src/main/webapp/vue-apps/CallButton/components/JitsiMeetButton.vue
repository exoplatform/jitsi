<template>
  <v-btn ref="jitsi" :ripple="false" class="jitsiCallAction btn" outlined @click.stop.prevent="startCall">
    <i :class="buttonTitle.icon" class="uiIconSocPhone uiIconBlue"></i>
    <span>{{ buttonTitle.title }}</span>
  </v-btn>
</template>

<script>
export default {
  name: "JitsiMeetButton",
  props: {
    callSettings: {
      type: Object,
      required: true
    },
    i18n: {
      type: Object,
      required: true
    },
    language: {
      type: String,
      required: true
    },
    resourceBundleName: {
      type: String,
      required: true
    }
  },

  data: function() {
    return {
      settings: this.callSettings,
      log: null,
      callWindow: null
      // callState: null
    };
  },
  computed: {
    callState: function() {
      return this.callSettings.callState;
    },
    buttonTitle: function() {
      if (this.callState === "joined") {
        return {
          title: this.i18n.te("UICallButton.label.joined")
            ? this.$t("UICallButton.label.joined")
            : "Joined",
          icon: "callIcon-joined"
        };
      } else if (this.callState === "started" || this.callState === "leaved") {
        return {
          title: this.i18n.te("UICallButton.label.join")
            ? this.$t("UICallButton.label.join")
            : "Join Call",
          icon: "callIcon-join"
        };
      } else {
        return {
          title: this.i18n.te("UICallButton.label.jitsi")
            ? this.$t("UICallButton.label.jitsi")
            : "Call",
          icon: "callIcon-call"
        };
      }
    }
  },
  created() {
    this.log = webConferencing.getLog("jitsi");
    const callButton = this.$refs.jitsi;
  },

  mounted() {
    // Assign target ID to the button for later use on started
    // event in init()
  },
  methods: {
    startCall: function() {
      this.callSettings.onCallOpen();
    }
  }
};
</script>

<style scoped lang="less">
@import "../../../skin/variables.less";

.VuetifyApp {
  .v-btn:not(.v-btn--round).v-size--default {
    padding: 0px;
    min-width: unset;
    width: 100%;
    height: 100%;
  }
  .v-btn {
    padding: 0px;
    justify-content: flex-start;
  }
  .theme--light.v-btn {
    background: inherit;
    &:focus::before {
      opacity: 0;
      background: transparent;
    }
    &:hover {
      &::before {
        color: @primaryColor;
        opacity: 0;
      }
      // i {
      // color: white;
      // }
      // span {
      // color: white;
      // }
    }
  }
  .call-button-container {
    button {
      .v-btn__content {
        letter-spacing: normal;
        padding: 0 10px;
        height: 100%;
      }
    }
    &.single {
      &:hover {
        button:hover {
          i {
            color: @primaryColor;
          }
          span {
            color: unset;
          }
        }
      }
    }
  }
}
.call-button-mini {
  .VuetifyApp {
    .call-button-container {
      .dropdown-vue {
        .buttons-container {
          [class^="call-button-container-"] {
            button {
              background: transparent;
              box-shadow: none;
              border: none;
            }
            .v-btn {
              padding: 0px;
              vertical-align: baseline;
            }
          }
        }
      }
      &.single {
        &:hover {
          button:hover {
            i {
              color: var(--allPagesGreyColorLighten1, #a8b3c5);
            }
          }
        }
        .single-btn-container {
          button {
            margin-right: 0;
            border: none;
            background: transparent;
            justify-content: center;
            .v-btn__content {
              span {
                display: none;
              }
              .uiIconSocPhone {
                  font-size: 18px;
                  margin-bottom: 0px;
                  &::before {
                    content: "\e92b";
                  }
                }
            }
          }
        }
      }
    }
    &:hover {
      &.single {
        .single-btn-container {
          button {
            width: inherit;
            margin-right: 0;
            border: none;
            background: #ffffff;
            span {
              width: inherit;
            }
          }
        }
      }
    }
  }
  &.call-button--tiptip {
    .VuetifyApp {
      .call-button-container {
        .buttons-container {
          [class^="call-button-container-"] {
            button {
              padding-left: 0;
              .v-btn__content {
                .uiIconSocPhone {
                  font-size: 16px !important;
                  &::before {
                    content: "\e92b";
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
.jitsiCallAction {
  color: var(--allPagesDarkGrey, #4d5466) !important;
  .uiIconSocPhone {
    font-size: 14px;
    margin-bottom: -2px;
    &:before {
      height: 16px;
      width: 16px;
      margin-right: 4px;
      // margin-left: 3px;
    }
  }
  .callIcon-call {
    &:before {
      color: unset;
      content: "\e92b";
    }
  }
  .callIcon-join {
    &:before {
      content: "\E61C";
      color: #fb8e18;
    }
  }
  .callIcon-joined {
    &:before {
      color: #2eb58c;
      content: "\e92b";
    }
  }
}
</style>
<style lang="less">
.VuetifyApp {
  .v-application {
    .btn {
      &.jitsiCallAction {
        border: none !important;
        background-color: inherit !important;
      }
    }
  }
}
.uiAction {
  .jitsiCallAction {
    &.btn:first-child {
      [class^="uiIcon"] {
        color: var(--allPagesPrimaryColor, #578dc9);
      }
    }
  }
}
#tiptip_content {
  .connectAction {
    .btn {
      &.jitsiCallAction {
        height: inherit;
      }
    }
  }
}
</style>
