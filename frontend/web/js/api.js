// Session (sessionStorage : la connexion se termine à la fermeture de l'onglet)
const Session = {
  get token() { return sessionStorage.getItem('token'); },
  get user() {
    try { return JSON.parse(sessionStorage.getItem('user')); } catch (e) { return null; }
  },
  save(token, user) {
    sessionStorage.setItem('token', token);
    sessionStorage.setItem('user', JSON.stringify(user));
  },
  clear() { sessionStorage.clear(); },
};

// Appel de l'API REST ; lève une Error avec le message renvoyé par le serveur
async function api(path, { method = 'GET', body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (Session.token) headers['Authorization'] = 'Bearer ' + Session.token;

  let res;
  try {
    res = await fetch('/api' + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (e) {
    throw new Error('Serveur injoignable. Vérifiez votre connexion.');
  }

  if (res.status === 401 && path !== '/auth/login') {
    window.dispatchEvent(new Event('session-expired'));
    throw new Error('Session expirée, reconnectez-vous.');
  }
  if (res.status === 204) return null;

  let data = null;
  try { data = await res.json(); } catch (e) { /* corps vide */ }
  if (!res.ok) throw new Error((data && data.message) || 'Erreur ' + res.status);
  return data;
}

// Téléchargement d'un fichier binaire (facture PDF) : renvoie un Blob
async function apiBlob(path) {
  let res;
  try {
    res = await fetch('/api' + path, { headers: { Authorization: 'Bearer ' + Session.token } });
  } catch (e) {
    throw new Error('Serveur injoignable. Vérifiez votre connexion.');
  }
  if (res.status === 401) {
    window.dispatchEvent(new Event('session-expired'));
    throw new Error('Session expirée, reconnectez-vous.');
  }
  if (!res.ok) {
    let data = null;
    try { data = await res.json(); } catch (e) { /* corps vide */ }
    throw new Error((data && data.message) || 'Erreur ' + res.status);
  }
  return res.blob();
}
