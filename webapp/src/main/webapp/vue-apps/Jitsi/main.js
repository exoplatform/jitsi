import Jitsi from './components/Admin.vue';

Vue.use(Vuetify);
Vue.component('Jitsi', Jitsi);
const vuetify = new Vuetify({
  dark: true,
  iconfont: ''
});

// getting language of user
const lang = (window.eXo && window.eXo.env && window.eXo.env.portal && window.eXo.env.portal.language) || 'en';
const localePortlet = 'locale.webconferencing';
const resourceBundleName = 'Jitsi';
const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/${localePortlet}.${resourceBundleName}-${lang}.json`;

export function init(settings) {
  // getting locale ressources
  exoi18n.loadLanguageAsync(lang, url).then(i18n => {
    const props = Object.assign({}, settings, {
      i18n: i18n,
      language: lang,
      resourceBundleName: resourceBundleName,
    });
    // init Vue app when locale ressources are ready
    new Vue({
      render: h =>
        h(Jitsi, {props}),
      i18n,
      vuetify
    }).$mount('#Jitsi');
  });
}

  
