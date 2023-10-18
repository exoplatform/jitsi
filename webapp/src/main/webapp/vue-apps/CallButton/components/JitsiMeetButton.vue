<template>
  <div>
    <v-tooltip bottom :disabled="!displayTooltip">
      <template #activator="{ on, attrs }">
        <v-btn
          id="btnJitsiButton"
          class="jitsiCallAction"
          @click.stop.prevent="startCall"
          v-bind="attrs"
          v-on="on"
          icon>
          <v-icon
            size="16"
            class="uiIconStatus fas fa-phone"
            :color="buttonColor"
           />
        </v-btn>
      </template>
      <span v-if="displayTooltip">{{ buttonTitle.title }}</span>
    </v-tooltip>
    <span v-if="!displayTooltip"
      @click.stop.prevent="startCall">{{ buttonTitle.title }}</span>
  </div>
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
    displayTooltip: function() {
      return this.parentClasses.includes('call-button-mini');
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
    },
    buttonColor: function() {
      if (this.callState === 'joined') {
        return '#2eb58c';
      } else if (this.callState === 'started' || this.callState === 'leaved') {
        return '#fb8e18';
      } else {
        return '';
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
          title: this.parentClasses.includes('call-button-mini') || this.parentClasses.includes('call-button')
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
#chat-application {
  .call-button-container {
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
      }
    }
  }
}
.jitsiCallAction {
  color: var(--allPagesDarkGrey, #4d5466) !important;
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
            &:hover {
               .v-btn {
                 color: white!important;
                 i.uiIconSocPhone {
                   color: white!important;
                 }
               }
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
        color: var(--allPagesPrimaryColor, #476A9C);
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
.jitsiCallAction {
  .v-btn__content {
    justify-content:center;
  }
}
</style>
