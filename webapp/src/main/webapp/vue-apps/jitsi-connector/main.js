window.require(['SHARED/webConferencing','SHARED/webConferencingPortlet','SHARED/webConferencing_jitsi'], function(webConferencing, webConferencingPortlet, webConferencing_jitsi) {
  if (webConferencing_jitsi) {
    fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/jitsi/connectorsettings`, {
      credentials: 'include',
      method: 'GET',
    }).then((resp) => {
      if (!resp || !resp.ok) {
        throw new Error('Error while getting jitsi provider configuration');
      } else {
        return resp.json();
      }
    }).then((data) => {
      webConferencingPortlet.start();
      webConferencing_jitsi.configure(data);
      webConferencing.addProvider(webConferencing_jitsi);
    });
  }
});
