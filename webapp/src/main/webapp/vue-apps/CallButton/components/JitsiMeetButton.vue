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
            :color="buttonColor">
            fas fa-video
          </v-icon>
        </v-btn>
      </template>
      <span v-if="displayTooltip" class="buttonTitle">{{ buttonTitle.title }}</span>
    </v-tooltip>
    <span
      v-if="displayConnectorName"
      @click.stop.prevent="startCall">{{ 'Jitsi' }}</span>
    <span
      v-else-if="!displayTooltip"
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
    displayConnectorName() {
      return document.querySelector('.single-btn-container') === null;
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
        return '#5f708a';
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
