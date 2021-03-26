import adminDialog from "./components/EditDialog.vue";

Vue.use(Vuetify);

const vuetify = new Vuetify({
  dark: true,
  iconfont: ""
});

// getting language of user
const lang = (eXo && eXo.env && eXo.env.portal && eXo.env.portal.language) || "en";
const localePortlet = "locale.jitsi";
const resourceBundleName = "JitsiAdmin";
const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/${localePortlet}.${resourceBundleName}-${lang}.json`;

export function showSettings(settings) {
  return new Promise((resolve, reject) => {
    const container = document.createElement("div");
    container.setAttribute("id", "jitsiSettingsContainer");
    document.body.appendChild(container);

    // getting locale ressources
    exoi18n.loadLanguageAsync(lang, url).then(i18n => {
      // init Vue app when locale ressources are ready
      const jitsiAdmin = new Vue({
        components: {
          adminDialog
        },
        render: h =>
          h(adminDialog, {
            props: { ...settings, i18n: i18n },
            on: {
              saveConfig: newConfig => {
                resolve(newConfig);
                destroyDialog();
              },
              closeDialog: () => {
                resolve(null);
                destroyDialog();
              }
            }
          }),
        i18n,
        vuetify
      });
      function destroyDialog() {
        // destroy the vue listeners, etc
        jitsiAdmin.$destroy();
        // remove the element from the DOM
        jitsiAdmin.$el.parentNode.removeChild(jitsiAdmin.$el);
      }
      jitsiAdmin.$mount(container);
    });
  });
}
