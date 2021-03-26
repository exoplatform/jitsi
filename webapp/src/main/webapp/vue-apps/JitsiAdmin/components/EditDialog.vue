<template>
  <v-app class="VuetifyApp">
    <v-container style="width: 95%" class="v-application--is-ltr">
      <v-dialog
        v-model="showDialog"
        content-class="jitsi-settings-dialog"
        width="500"
        style="overflow-x: hidden">
        <v-card class="settingsContainer uiPopup">
          <v-card-title class="headline popupHeader justify-space-between providerHeader mb-0">
            <span class="PopupTitle popupTitle providerHeaderTitle">{{ this.$t("jitsi.admin.title") }}</span>
            <i class="uiIconClose providerHeaderClose" @click="closeDialog"></i>
          </v-card-title>
          <v-card-text class="popupContent providerContent pa-4">
            <v-container class="permissions px-0">
              <v-row class="errorDiagnostic ma-0">
                {{ this.$t("jitsi.admin.errorDiagnostic") }}
              </v-row>
              <v-row>
                <div class="diagnostic-errors">
                  <div class="control-group">
                    <span class="d-flex align-center uiCheckbox">
                      <v-checkbox
                        v-model="config.logEnabled"
                        :ripple="false"
                        color="#578dc9"
                        dense
                        class="ma-0"
                        data-test="everybodyCheckbox" />
                      <span class="errorDiagnosticLabel">{{ this.$t("jitsi.admin.enableLogCollection") }}</span>&nbsp;&nbsp;
                      <i
                        :title="this.$t('jitsi.admin.enableLogCollection')"
                        class="uiIconInformation uiIconBlue"
                        data-container=".diagnostic-errors"
                        data-placement="top"
                        data-toggle="tooltip"></i>
                    </span>
                  </div>
                </div>
              </v-row>
              <v-row class="permissionsTitle ma-0">
                {{ this.$t("jitsi.admin.permissions") }}
              </v-row>
              <v-row class="search">
                <v-col>
                  <label class="searchLabel">{{ this.$t("jitsi.admin.searchLabel") }}</label>
                  <v-autocomplete
                    v-model="selectedItems"
                    :loading="searchLoading"
                    :items="searchResults"
                    :search-input.sync="search"
                    :menu-props="{ maxHeight: 140 }"
                    return-object
                    color="#333"
                    class="searchPermissions pt-0"
                    flat
                    hide-no-data
                    hide-details
                    solo-inverted
                    hide-selected
                    chips
                    multiple
                    attach
                    dense
                    dark
                    item-text="displayName"
                    item-value="id"
                    append-icon=""
                    @input="selectionChange">
                    <template slot="selection" slot-scope="data">
                      <v-chip
                        :input-value="data"
                        close
                        light
                        small
                        class="chip--select-multi searchChip"
                        @click:close="removeSelection(data.item)">
                        {{ data.item.displayName }}
                      </v-chip>
                    </template>
                    <template
                      slot="item"
                      slot-scope="{ item, parent }"
                      class="permissionsItem">
                      <v-list-tile-avatar><img :src="item.avatarUrl" class="permissionsItemAvatar"></v-list-tile-avatar>
                      <v-list-tile-content class="permissionsItemName">
                        <v-list-tile-title v-html="parent.genFilteredText(item.displayName)" />
                      </v-list-tile-content>
                    </template>
                  </v-autocomplete>
                </v-col>
              </v-row>
              <v-row>
                <v-col class="permissionsContainer">
                  <div class="d-flex justify-space-between">
                    <label class="searchLabel ma-0">{{ this.$t("jitsi.admin.withPermissions") }}</label>
                    <div class="d-flex align-center">
                      <v-checkbox
                        v-model="accessibleToAll"
                        :ripple="false"
                        color="#578dc9"
                        dense
                        class="ma-0"
                        data-test="everybodyCheckbox" />
                      <label style="color: #333">{{ this.$t("jitsi.admin.everybody") }}</label>
                    </div>
                  </div>
                  <v-row v-if="!accessibleToAll">
                    <v-col v-if="existingPermissions.length > 0">
                      <ul class="permissionsList ps-0">
                        <li
                          v-for="permission in existingPermissions"
                          :key="permission.id"
                          :class="[
                            'permissionsItem',
                            'permissionsItem--large',
                            permission.className === 'removed' ? 'permissionsItem--removed' : '']">
                          <v-tooltip bottom>
                            <template v-slot:activator="{ on }">
                              <div v-on="on">
                                <img :src="permission.avatarUrl" class="permissionsItemAvatar permissionsItemAvatar--large">
                                <span class="permissionsItemName">{{ permission.displayName }}</span>
                              </div>
                            </template>
                            <span>{{ permission.id }}</span>
                          </v-tooltip>
                          <i
                            v-show="existingPermissions.length > 0 && permission.displayName"
                            class="uiIconDelete permissionsItemDelete"
                            @click="removePermission(permission)">
                          </i>
                        </li>
                      </ul>
                    </v-col>
                    <v-col
                      v-else
                      cols="12"
                      md="8">
                      <label>{{ this.$t("jitsi.admin.none") }}</label>
                    </v-col>
                  </v-row>
                </v-col>
              </v-row>
            </v-container>
          </v-card-text>
          <v-card-actions class="uiAction dialogFooter footer justify-center pb-5">
            <v-btn
              class="btn btn-primary dialogFooterBtn me-2 saveButton"
              text
              data-test="saveButton"
              @click.native="saveChanges">
              {{ this.$t("jitsi.admin.save") }}
            </v-btn>
            <v-btn
              class="btn dialogFooterBtn cancelButton"
              text
              data-test="cancelButton"
              @click.native="closeDialog">
              {{ this.$t("jitsi.admin.cancel") }}
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-dialog>
    </v-container>
  </v-app>
</template>

<script>
async function searchIdentity(url) {
  try {
    const response = await fetch(url, {
      headers: {
        "Content-Type": "application/json"
      },
      method: "GET"
    });
    if (response.ok) {
      return response.json();
    } else {
      const err = await response.json();
      throw new Error(err.errorMessage ? err.errorMessage : err.errorCode);
    }
  } catch(err) {
    // network failure or anything prevented the request from completing.
    throw new Error("Failed to search an identity. Cause: " + err.message); // localized errorCode here?
  }
}

export default {
  props: {
    configuration: {
      type: Object,
      required: true
    },
    searchUrl: {
      type: String,
      required: true
    },
    i18n: {
      type: Object,
      required: true
    },
  },
  data() {
    return {
      config: this.configuration,
      searchLoading: false,
      searchResults: [],
      search: null,
      selectedItems: [],
      existingPermissions: [],
      permissionChanges: [],
      accessibleToAll: false,
      showDialog: true,
      log: null
    };
  },
  watch: {
    search(val) {
      return val && val !== this.selectedItems && this.querySelections(val);
    }
  },
  mounted() {
    this.log = webConferencing.getLog("jitsi");
    this.showSettings();
  },
  methods: {
    showSettings() {
      try {
        if (this.config.permissions) {
          this.accessibleToAll = this.config.permissions.some(({id}) => id === "*");
          this.existingPermissions = this.config.permissions
            .filter(({displayName}) => displayName !== null)
            .map(obj => ({...obj, className: ""}));
        }
      } catch (err) {
        this.log.showError(this.$t("jitsi.admin.errorOpenSettings"), err.message);
        return false;
      }
      return true;
    },
    // updating items in dropdown depend on user input v
    async querySelections(v) {
      this.searchLoading = true;
      try {
        const identities = await webConferencing.findIdentities("jitsi", v);
        this.searchResults = identities.filter(
          ({ displayName }) => (displayName || "").toLowerCase().indexOf((v || "").toLowerCase()) > -1
        ).filter(el => {
          el.id = this.generatePermission(el);
          return !this.existingPermissions.map(item => item.id).includes(el.id)
        });
        this.searchLoading = false;
      } catch (err) {
        this.log.showError(this.$t("jitsi.admin.errorSearchPermissions"), err.message);
      }
    },
    // removes selected item from selection
    removeSelection(value) {
      this.selectedItems = this.selectedItems.filter(({ id }) => id !== value.id);
    },
    saveChanges() {
      let editedPermissions = this.existingPermissions.filter(el => !this.permissionChanges.map(item => item.id).includes(el.id));
      if (this.selectedItems) {
        editedPermissions = editedPermissions.concat(this.selectedItems);
      }
      // form array with permission names before sending request
      const newPermissions = this.accessibleToAll 
        ? [{ id: "*" }] 
        : editedPermissions.filter(({ id }) => id.length > 0).map(({ id }) => ({ id: id}));
      this.config.permissions = newPermissions;
      this.$emit("saveConfig", this.config);
    },
    closeDialog() {
      // reset and clearing user changes
      this.selectedItems = null;
      this.$emit("closeDialog");
    },
    // removing permission from list and also from selection
    removePermission(item) {
      this.permissionChanges.push(item);
      item.className = "removed";
      if (this.selectedItems) {
        this.selectedItems = this.selectedItems.filter(({ id }) => id !== item.id);
      }
    },
    selectionChange(selection) {
      this.search = "";
      // if everyone permission enabled, it will be automatically disabled in case of some another permission selected
      if (selection.length > 0 && this.accessibleToAll) {
        this.accessibleToAll = false;
      }
    },
    generatePermission(identity){
      if(identity.type === "user"){
        return identity.id;
      } else {
        return "*:" + identity.id;
      }
    }
  }
};
</script>
