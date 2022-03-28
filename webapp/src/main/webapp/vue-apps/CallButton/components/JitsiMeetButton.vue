<template>
  <v-btn
    ref="jitsi"
    :ripple="false"
    class="jitsiCallAction btn"
    outlined
    @click.stop.prevent="startCall">
    <i :class="buttonTitle.icon" class="uiIconSocPhone uiIconBlue"></i>
    <span>{{ buttonTitle.title }}</span>
  </v-btn>
</template>

<script>
export default {
  name: 'JitsiMeetButton',
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
    };
  },
  computed: {
    callState: function() {
      return this.callSettings.callState;
    },
    parentClasses: function() {
      return this.callSettings.context.parentClasses;
    },
    buttonTitle: function() {
      if (this.callState === 'joined') {
        return this.generateButtonTitle('UICallButton.label.joined',
          'Joined',
          'uiIconCallJoined');
      } else if (this.callState === 'started' || this.callState === 'leaved') {
        return this.generateButtonTitle('UICallButton.label.join',
          'Join Call',
          'uiIconCallJoin');
      } else {
        return this.generateButtonTitle('UICallButton.label.jitsi',
          'Call',
          'uiIconCallStart');
      }
    }
  },
  created() {
    this.log = webConferencing.getLog('jitsi');
  },

  mounted() {
    // Assign target ID to the button for later use on started
    // event in init()
  },
  methods: {
    startCall: function() {
      this.callSettings.onCallOpen();
    },
    generateButtonTitle: function(label, defaultText, icon) {
      if (this.parentClasses) {
        return {
          title: !this.parentClasses.includes('call-button-mini')
            ? this.i18n.te(label)
              ? this.$t(label)
              : defaultText
            : '',
          icon: icon
        };
      } else {
        return {
          icon: icon
        };
      }
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
.jitsiCallAction {
  color: var(--allPagesDarkGrey, #4d5466) !important;
  .uiIconSocPhone {
    &:before {
      content: "\e92b";
      height: 16px;
      width: 16px;
      margin-right: 4px;
    }
    &.uiIconCallStart {
      &:before {
        color: unset;
        content: "\e92b";
      }
    }
    &.uiIconCallJoin {
      &:before {
        content: "\E61C";
        color: #fb8e18;
      }
    }
    &.uiIconCallJoined {
      &:before {
        color: #2eb58c;
        content: "\e92b";
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
              color: var(--allPagesGreyColorLighten1, #5f708a);
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
                font-size: 18px !important;
                margin-bottom: 0px;
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
.call-button--profile, .call-button--chat {
    .uiIconSocPhone {
      font-size: 14px;
      margin-bottom: -2px;
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
