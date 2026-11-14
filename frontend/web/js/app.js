/* LogiStock SA - interface web (HTML/CSS/JS sans framework) */

const ROLES = { ADMIN: 'Administrateur', GEST: 'Gestionnaire de stock', COM: 'Commercial' };

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const money = (n) => new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n);
const dateFr = (s) => new Date(s).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });
const $ = (sel, root = document) => root.querySelector(sel);

const isAdmin = () => Session.user && Session.user.role === ROLES.ADMIN;
const canSell = () => Session.user && [ROLES.ADMIN, ROLES.COM].includes(Session.user.role);
const canEditStock = () => Session.user && [ROLES.ADMIN, ROLES.GEST].includes(Session.user.role);

// ---------- Utilitaires d'interface ----------

let toastTimer;
function toast(message, erreur = false) {
  const t = $('#toast');
  t.textContent = message;
  t.className = 'show' + (erreur ? ' err' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.className = ''; }, 3200);
}

function openModal(titre, corpsHtml, onSubmit, libelleBouton = 'Enregistrer') {
  const m = $('#modal');
  m.innerHTML = `<form class="modal-form"><h2>${esc(titre)}</h2>${corpsHtml}
    <p class="error" id="modal-error"></p>
    <div class="actions">
      <button type="button" class="btn ghost" data-close>Annuler</button>
      <button type="submit" class="btn primary">${esc(libelleBouton)}</button>
    </div></form>`;
  $('[data-close]', m).onclick = () => m.close();
  $('form', m).onsubmit = async (e) => {
    e.preventDefault();
    $('#modal-error').textContent = '';
    try {
      await onSubmit(new FormData(e.target));
      m.close();
    } catch (ex) {
      $('#modal-error').textContent = ex.message;
    }
  };
  m.showModal();
}

function options(liste, valeur, libelle, selectionne) {
  return liste.map((x) =>
    `<option value="${esc(x[valeur])}"${String(x[valeur]) === String(selectionne) ? ' selected' : ''}>${esc(x[libelle])}</option>`
  ).join('');
}

function tableau(entetes, lignesHtml, vide) {
  if (!lignesHtml.length) return `<div class="empty">${esc(vide)}</div>`;
  return `<table><thead><tr>${entetes.join('')}</tr></thead><tbody>${lignesHtml.join('')}</tbody></table>`;
}

async function agir(promesse, messageSucces, rechargement) {
  try {
    await promesse;
    if (messageSucces) toast(messageSucces);
    if (rechargement) await rechargement();
  } catch (e) {
    toast(e.message, true);
  }
}

// ---------- Connexion ----------

function vueConnexion() {
  $('#app').innerHTML = `
    <div class="login-wrap">
      <form class="login-card" id="login-form">
        <h1>LogiStock SA</h1>
        <p class="sub">Connectez-vous pour accéder à l'application.</p>
        <label for="email">Adresse e-mail</label>
        <input id="email" name="email" type="email" autocomplete="username" required autofocus>
        <label for="mdp">Mot de passe</label>
        <input id="mdp" name="motDePasse" type="password" autocomplete="current-password" required>
        <p class="error" id="login-error"></p>
        <button class="btn primary" style="width:100%;margin-top:8px" type="submit">Se connecter</button>
      </form>
    </div>`;
  $('#login-form').onsubmit = async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    $('#login-error').textContent = '';
    try {
      const rep = await api('/auth/login', {
        method: 'POST',
        body: { email: f.get('email'), motDePasse: f.get('motDePasse') },
      });
      Session.save(rep.token, rep.utilisateur);
      if (location.hash === '#/produits') route(); else location.hash = '#/tableau';
    } catch (ex) {
      $('#login-error').textContent = ex.message;
    }
  };
}

function deconnexion() {
  Session.clear();
  if (location.hash) location.hash = ''; else route();
}
window.addEventListener('session-expired', deconnexion);

// ---------- Squelette et navigation ----------

const ROUTES = {
  '/tableau': { label: 'Tableau de bord', visible: () => true, vue: vueTableau },
  '/produits': { label: 'Produits', visible: () => true, vue: vueProduits },
  '/categories': { label: 'Catégories', visible: () => true, vue: vueCategories },
  '/mouvements': { label: 'Mouvements', visible: canEditStock, vue: vueMouvements },
  '/clients': { label: 'Clients', visible: canSell, vue: vueClients },
  '/ventes': { label: 'Ventes', visible: canSell, vue: vueVentes },
  '/alertes': { label: 'Alertes', visible: () => true, vue: vueAlertes },
  '/utilisateurs': { label: 'Utilisateurs', visible: isAdmin, vue: vueUtilisateurs },
};

function squelette(chemin) {
  const u = Session.user;
  const liens = Object.entries(ROUTES)
    .filter(([, r]) => r.visible())
    .map(([p, r]) => `<a href="#${p}" class="${p === chemin ? 'active' : ''}">${esc(r.label)}${p === '/alertes' ? '<span class="badge" id="badge-alertes" hidden></span>' : ''}</a>`)
    .join('');
  $('#app').innerHTML = `
    <header class="topbar">
      <span class="brand">LogiStock SA</span>
      <span class="who"><span class="name">${esc(u.nom)}</span><span class="role">${esc(u.role)}</span>
        <button class="btn small" id="logout">Déconnexion</button></span>
    </header>
    <nav class="nav">${liens}</nav>
    <main id="main"></main>`;
  $('#logout').onclick = deconnexion;
}

async function majBadgeAlertes() {
  try {
    const alertes = await api('/alertes');
    const b = $('#badge-alertes');
    if (b) {
      b.textContent = alertes.length;
      b.hidden = alertes.length === 0;
    }
  } catch (e) { /* non bloquant */ }
}

async function route() {
  if (!Session.token || !Session.user) return vueConnexion();
  const chemin = location.hash.slice(1) || '/tableau';
  const r = ROUTES[chemin];
  if (!r || !r.visible()) {
    location.hash = '#/produits';
    return;
  }
  squelette(chemin);
  const main = $('#main');
  main.innerHTML = '<p class="muted">Chargement…</p>';
  try {
    await r.vue(main);
  } catch (e) {
    main.innerHTML = `<p class="error">${esc(e.message)}</p>`;
  }
  majBadgeAlertes();
}
window.addEventListener('hashchange', route);

// ---------- Produits ----------

async function vueProduits(main) {
  const categories = await api('/categories');
  const edition = canEditStock();
  main.innerHTML = `
    <div class="page-head"><h1>Produits</h1>
      ${edition ? '<button class="btn primary" id="nouveau">+ Nouveau produit</button>' : ''}</div>
    <div class="toolbar">
      <input type="search" id="q" placeholder="Rechercher par référence ou désignation…">
      <select id="filtre-cat"><option value="">Toutes les catégories</option>${options(categories, 'id', 'nom')}</select>
    </div>
    <div class="table-wrap" id="liste"></div>`;

  let produits = [];
  async function charger() {
    const params = new URLSearchParams();
    if ($('#q').value.trim()) params.set('q', $('#q').value.trim());
    if ($('#filtre-cat').value) params.set('categorie', $('#filtre-cat').value);
    produits = await api('/produits' + (params.toString() ? '?' + params : ''));
    const lignes = produits.map((p) => `<tr>
      <td>${esc(p.reference)}</td><td>${esc(p.designation)}</td><td>${esc(p.categorie)}</td>
      <td class="num">${money(p.prix)}</td>
      <td class="num">${p.quantiteStock} ${p.enAlerte
        ? `<span class="tag ${p.quantiteStock === 0 ? 'danger' : 'warn'}">${p.quantiteStock === 0 ? 'Rupture' : 'Alerte'}</span>` : ''}</td>
      <td class="num">${p.seuilAlerte}</td>
      ${edition ? `<td class="actions">
        <button class="btn small" data-act="mouvement" data-id="${p.id}">Mouvement</button>
        <button class="btn small" data-act="modifier" data-id="${p.id}">Modifier</button>
        <button class="btn small danger" data-act="supprimer" data-id="${p.id}">Supprimer</button></td>` : ''}
    </tr>`);
    $('#liste').innerHTML = tableau(
      ['<th>Référence</th>', '<th>Désignation</th>', '<th>Catégorie</th>', '<th class="num">Prix</th>',
        '<th class="num">Stock</th>', '<th class="num">Seuil</th>', edition ? '<th></th>' : ''],
      lignes, 'Aucun produit ne correspond.');
  }

  function formulaire(p) {
    return `
      <label>Référence</label><input name="reference" maxlength="30" required value="${esc(p?.reference)}">
      <label>Désignation</label><input name="designation" maxlength="150" required value="${esc(p?.designation)}">
      <label>Catégorie</label><select name="idCategorie" required>${options(categories, 'id', 'nom', p?.idCategorie)}</select>
      <label>Prix unitaire</label><input name="prix" type="number" step="0.01" min="0" required value="${p ? p.prix : ''}">
      <label>Seuil d'alerte</label><input name="seuilAlerte" type="number" min="0" required value="${p ? p.seuilAlerte : 0}">`;
  }
  const corps = (f) => ({
    reference: f.get('reference'), designation: f.get('designation'), idCategorie: Number(f.get('idCategorie')),
    prix: Number(f.get('prix')), seuilAlerte: Number(f.get('seuilAlerte')),
  });

  $('#q').oninput = () => { clearTimeout(vueProduits.t); vueProduits.t = setTimeout(() => agir(charger()), 250); };
  $('#filtre-cat').onchange = () => agir(charger());

  if (edition) {
    $('#nouveau').onclick = () => {
      if (!categories.length) return toast("Créez d'abord une catégorie.", true);
      openModal('Nouveau produit', formulaire(null), async (f) => {
        await api('/produits', { method: 'POST', body: corps(f) });
        toast('Produit créé (stock à 0 : ajoutez une entrée de stock)');
        await charger();
      });
    };
    $('#liste').onclick = (e) => {
      const b = e.target.closest('button[data-act]');
      if (!b) return;
      const p = produits.find((x) => x.id === Number(b.dataset.id));
      if (b.dataset.act === 'modifier') {
        openModal('Modifier le produit', formulaire(p), async (f) => {
          await api('/produits/' + p.id, { method: 'PUT', body: corps(f) });
          toast('Produit modifié');
          await charger();
        });
      } else if (b.dataset.act === 'supprimer') {
        if (confirm(`Supprimer le produit « ${p.designation} » ?`)) {
          agir(api('/produits/' + p.id, { method: 'DELETE' }), 'Produit supprimé', charger);
        }
      } else if (b.dataset.act === 'mouvement') {
        openModal(`Mouvement de stock : ${p.designation}`, `
          <p class="muted">Stock actuel : <strong>${p.quantiteStock}</strong></p>
          <label>Type</label><select name="type"><option value="ENTREE">Entrée</option><option value="SORTIE">Sortie</option></select>
          <label>Quantité</label><input name="quantite" type="number" min="1" required value="1">`,
        async (f) => {
          const r = await api('/mouvements', {
            method: 'POST',
            body: { idProduit: p.id, type: f.get('type'), quantite: Number(f.get('quantite')) },
          });
          toast(`Mouvement enregistré. Nouveau stock : ${r.quantiteStock}`);
          await charger();
          majBadgeAlertes();
        });
      }
    };
  }
  await charger();
}

// ---------- Catégories ----------

async function vueCategories(main) {
  const edition = canEditStock();
  main.innerHTML = `
    <div class="page-head"><h1>Catégories</h1>
      ${edition ? '<button class="btn primary" id="nouvelle">+ Nouvelle catégorie</button>' : ''}</div>
    <div class="table-wrap" id="liste"></div>`;
  let categories = [];
  async function charger() {
    categories = await api('/categories');
    $('#liste').innerHTML = tableau(
      ['<th>Nom</th>', '<th class="num">Produits</th>', edition ? '<th></th>' : ''],
      categories.map((c) => `<tr><td>${esc(c.nom)}</td><td class="num">${c.nbProduits}</td>
        ${edition ? `<td class="actions">
          <button class="btn small" data-act="modifier" data-id="${c.id}">Renommer</button>
          <button class="btn small danger" data-act="supprimer" data-id="${c.id}">Supprimer</button></td>` : ''}</tr>`),
      'Aucune catégorie.');
  }
  if (edition) {
    const champ = (c) => `<label>Nom de la catégorie</label><input name="nom" maxlength="80" required value="${esc(c?.nom)}">`;
    $('#nouvelle').onclick = () => openModal('Nouvelle catégorie', champ(null), async (f) => {
      await api('/categories', { method: 'POST', body: { nom: f.get('nom') } });
      toast('Catégorie créée');
      await charger();
    });
    $('#liste').onclick = (e) => {
      const b = e.target.closest('button[data-act]');
      if (!b) return;
      const c = categories.find((x) => x.id === Number(b.dataset.id));
      if (b.dataset.act === 'modifier') {
        openModal('Renommer la catégorie', champ(c), async (f) => {
          await api('/categories/' + c.id, { method: 'PUT', body: { nom: f.get('nom') } });
          toast('Catégorie modifiée');
          await charger();
        });
      } else if (confirm(`Supprimer la catégorie « ${c.nom} » ?`)) {
        agir(api('/categories/' + c.id, { method: 'DELETE' }), 'Catégorie supprimée', charger);
      }
    };
  }
  await charger();
}

// ---------- Mouvements de stock ----------

async function vueMouvements(main) {
  const produits = await api('/produits');
  main.innerHTML = `
    <div class="page-head"><h1>Mouvements de stock</h1></div>
    <form class="card inline-form" id="form-mvt">
      <div><label>Produit</label><select name="idProduit" required>${options(produits, 'id', 'designation')}</select></div>
      <div><label>Type</label><select name="type"><option value="ENTREE">Entrée</option><option value="SORTIE">Sortie</option></select></div>
      <div><label>Quantité</label><input name="quantite" type="number" min="1" value="1" required></div>
      <button class="btn primary" type="submit">Enregistrer</button>
    </form>
    <div class="toolbar">
      <select id="filtre-produit"><option value="">Tous les produits</option>${options(produits, 'id', 'designation')}</select>
      <select id="filtre-type"><option value="">Entrées et sorties</option><option value="ENTREE">Entrées</option><option value="SORTIE">Sorties</option></select>
    </div>
    <div class="table-wrap" id="liste"></div>`;

  async function charger() {
    const params = new URLSearchParams();
    if ($('#filtre-produit').value) params.set('produit', $('#filtre-produit').value);
    if ($('#filtre-type').value) params.set('type', $('#filtre-type').value);
    const rows = await api('/mouvements' + (params.toString() ? '?' + params : ''));
    $('#liste').innerHTML = tableau(
      ['<th>Date</th>', '<th>Produit</th>', '<th>Type</th>', '<th class="num">Quantité</th>', '<th>Auteur</th>', '<th>Vente</th>'],
      rows.map((m) => `<tr><td>${dateFr(m.dateHeure)}</td><td>${esc(m.reference)} · ${esc(m.designation)}</td>
        <td><span class="tag ${m.type === 'ENTREE' ? 'ok' : 'warn'}">${m.type === 'ENTREE' ? 'Entrée' : 'Sortie'}</span></td>
        <td class="num">${m.quantite}</td><td>${esc(m.auteur)}</td><td>${m.idCommande ? '#' + m.idCommande : ''}</td></tr>`),
      'Aucun mouvement.');
  }
  $('#filtre-produit').onchange = () => agir(charger());
  $('#filtre-type').onchange = () => agir(charger());
  $('#form-mvt').onsubmit = (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    agir((async () => {
      const r = await api('/mouvements', {
        method: 'POST',
        body: { idProduit: Number(f.get('idProduit')), type: f.get('type'), quantite: Number(f.get('quantite')) },
      });
      toast(`Mouvement enregistré. Nouveau stock : ${r.quantiteStock}`);
      await charger();
      majBadgeAlertes();
    })());
  };
  await charger();
}

// ---------- Alertes ----------

async function vueAlertes(main) {
  const alertes = await api('/alertes');
  main.innerHTML = `
    <div class="page-head"><h1>Alertes de stock</h1></div>
    <p class="muted">Produits en rupture ou dont le stock est passé sous le seuil d'alerte.</p>
    <div class="table-wrap">${tableau(
      ['<th>Référence</th>', '<th>Désignation</th>', '<th>Catégorie</th>', '<th class="num">Stock</th>', '<th class="num">Seuil</th>', '<th>État</th>'],
      alertes.map((a) => `<tr><td>${esc(a.reference)}</td><td>${esc(a.designation)}</td><td>${esc(a.categorie)}</td>
        <td class="num">${a.quantiteStock}</td><td class="num">${a.seuilAlerte}</td>
        <td><span class="tag ${a.etat === 'RUPTURE' ? 'danger' : 'warn'}">${a.etat === 'RUPTURE' ? 'Rupture' : 'Stock bas'}</span></td></tr>`),
      'Aucune alerte : tous les stocks sont au-dessus de leur seuil.')}</div>`;
}

// ---------- Utilisateurs (administrateur) ----------

async function vueUtilisateurs(main) {
  const roles = await api('/roles');
  main.innerHTML = `
    <div class="page-head"><h1>Utilisateurs</h1><button class="btn primary" id="nouveau">+ Nouvel utilisateur</button></div>
    <div class="table-wrap" id="liste"></div>`;
  let utilisateurs = [];
  async function charger() {
    utilisateurs = await api('/utilisateurs');
    $('#liste').innerHTML = tableau(
      ['<th>Nom</th>', '<th>E-mail</th>', '<th>Rôle</th>', '<th>Statut</th>', '<th></th>'],
      utilisateurs.map((u) => `<tr><td>${esc(u.nom)}</td><td>${esc(u.email)}</td><td>${esc(u.role)}</td>
        <td><span class="tag ${u.actif ? 'ok' : 'off'}">${u.actif ? 'Actif' : 'Désactivé'}</span></td>
        <td class="actions">
          <button class="btn small" data-act="modifier" data-id="${u.id}">Modifier</button>
          <button class="btn small" data-act="mdp" data-id="${u.id}">Mot de passe</button></td></tr>`),
      'Aucun utilisateur.');
  }
  $('#nouveau').onclick = () => openModal('Nouvel utilisateur', `
    <label>Nom complet</label><input name="nom" maxlength="100" required>
    <label>Adresse e-mail</label><input name="email" type="email" maxlength="150" required>
    <label>Mot de passe (8 caractères minimum)</label><input name="motDePasse" type="password" minlength="8" maxlength="72" required autocomplete="new-password">
    <label>Rôle</label><select name="idRole">${options(roles, 'id', 'libelle')}</select>`,
  async (f) => {
    await api('/utilisateurs', {
      method: 'POST',
      body: { nom: f.get('nom'), email: f.get('email'), motDePasse: f.get('motDePasse'), idRole: Number(f.get('idRole')) },
    });
    toast('Utilisateur créé');
    await charger();
  });
  $('#liste').onclick = (e) => {
    const b = e.target.closest('button[data-act]');
    if (!b) return;
    const u = utilisateurs.find((x) => x.id === Number(b.dataset.id));
    if (b.dataset.act === 'modifier') {
      openModal('Modifier l\'utilisateur', `
        <label>Nom complet</label><input name="nom" maxlength="100" required value="${esc(u.nom)}">
        <label>Rôle</label><select name="idRole">${options(roles, 'id', 'libelle', u.idRole)}</select>
        <label>Statut</label><select name="actif"><option value="true"${u.actif ? ' selected' : ''}>Actif</option><option value="false"${u.actif ? '' : ' selected'}>Désactivé</option></select>`,
      async (f) => {
        await api('/utilisateurs/' + u.id, {
          method: 'PUT',
          body: { nom: f.get('nom'), idRole: Number(f.get('idRole')), actif: f.get('actif') === 'true' },
        });
        toast('Utilisateur modifié');
        await charger();
      });
    } else {
      openModal(`Nouveau mot de passe : ${u.nom}`, `
        <label>Nouveau mot de passe (8 caractères minimum)</label>
        <input name="motDePasse" type="password" minlength="8" maxlength="72" required autocomplete="new-password">`,
      async (f) => {
        await api('/utilisateurs/' + u.id + '/mot-de-passe', { method: 'PUT', body: { motDePasse: f.get('motDePasse') } });
        toast('Mot de passe modifié');
      });
    }
  };
  await charger();
}

// ---------- Facture PDF ----------

// La fenêtre est ouverte tout de suite (clic de l'utilisateur) pour éviter le blocage des pop-ups
async function ouvrirFacture(idCommande) {
  const fenetre = window.open('', '_blank');
  try {
    const blob = await apiBlob(`/commandes/${idCommande}/facture`);
    const url = URL.createObjectURL(blob);
    if (fenetre) {
      fenetre.location = url;
    } else {
      const a = document.createElement('a');
      a.href = url;
      a.download = `facture-${idCommande}.pdf`;
      a.click();
    }
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch (e) {
    if (fenetre) fenetre.close();
    toast(e.message, true);
  }
}

const STATUTS = {
  BROUILLON: ['Brouillon', 'off'],
  VALIDEE: ['Validée', 'ok'],
  ANNULEE: ['Annulée', 'danger'],
};
const tagStatut = (s) => `<span class="tag ${STATUTS[s][1]}">${STATUTS[s][0]}</span>`;

// ---------- Clients (F4) ----------

async function vueClients(main) {
  main.innerHTML = `
    <div class="page-head"><h1>Clients</h1><button class="btn primary" id="nouveau">Nouveau client</button></div>
    <div class="toolbar"><input id="recherche" type="search" placeholder="Rechercher (nom, e-mail, téléphone)"></div>
    <div class="table-wrap" id="liste"></div>`;

  const formulaire = (c = {}) => `
    <label>Nom *</label><input name="nom" required maxlength="100" value="${esc(c.nom)}">
    <label>Prénom</label><input name="prenom" maxlength="100" value="${esc(c.prenom)}">
    <label>E-mail</label><input name="email" type="email" maxlength="150" value="${esc(c.email)}">
    <label>Téléphone</label><input name="telephone" maxlength="30" value="${esc(c.telephone)}">
    <label>Adresse</label><input name="adresse" maxlength="255" value="${esc(c.adresse)}">`;
  const corps = (f) => ({
    nom: f.get('nom'), prenom: f.get('prenom'), email: f.get('email'),
    telephone: f.get('telephone'), adresse: f.get('adresse'),
  });

  let clients = [];
  async function charger() {
    const q = $('#recherche').value.trim();
    clients = await api('/clients' + (q ? '?q=' + encodeURIComponent(q) : ''));
    $('#liste').innerHTML = tableau(
      ['<th>Nom</th>', '<th>E-mail</th>', '<th>Téléphone</th>', '<th>Adresse</th>', '<th class="num">Achats</th>', '<th></th>'],
      clients.map((c) => `<tr><td>${esc(c.nom)} ${esc(c.prenom)}</td><td>${esc(c.email)}</td><td>${esc(c.telephone)}</td>
        <td>${esc(c.adresse)}</td><td class="num">${c.nbAchats}</td>
        <td class="actions">
          <button class="btn small" data-histo="${c.id}">Historique</button>
          <button class="btn small" data-edit="${c.id}">Modifier</button>
          <button class="btn small danger" data-del="${c.id}">Supprimer</button></td></tr>`),
      'Aucun client.');
    document.querySelectorAll('[data-edit]').forEach((b) => b.onclick = () => {
      const c = clients.find((x) => x.id === Number(b.dataset.edit));
      openModal('Modifier le client', formulaire(c), async (f) => {
        await api('/clients/' + c.id, { method: 'PUT', body: corps(f) });
        toast('Client modifié');
        await charger();
      });
    });
    document.querySelectorAll('[data-del]').forEach((b) => b.onclick = () => {
      const c = clients.find((x) => x.id === Number(b.dataset.del));
      if (confirm(`Supprimer le client ${c.nom} ?`)) {
        agir(api('/clients/' + c.id, { method: 'DELETE' }), 'Client supprimé', charger);
      }
    });
    document.querySelectorAll('[data-histo]').forEach((b) => b.onclick = () => historique(Number(b.dataset.histo)));
  }

  async function historique(id) {
    const c = clients.find((x) => x.id === id);
    try {
      const achats = (await api('/commandes?statut=VALIDEE&client=' + id));
      const m = $('#modal');
      m.innerHTML = `<div class="modal-form"><h2>Historique : ${esc(c.nom)} ${esc(c.prenom)}</h2>
        <div class="table-wrap">${tableau(
          ['<th>Date</th>', '<th>Facture</th>', '<th class="num">Total</th>', '<th></th>'],
          achats.map((o) => `<tr><td>${dateFr(o.dateCommande)}</td><td>${esc(o.numeroFacture)}</td>
            <td class="num">${money(o.total)}</td>
            <td><button class="btn small" data-pdf="${o.id}">Facture PDF</button></td></tr>`),
          'Aucun achat pour ce client.')}</div>
        <div class="actions"><button class="btn ghost" data-close>Fermer</button></div></div>`;
      $('[data-close]', m).onclick = () => m.close();
      m.querySelectorAll('[data-pdf]').forEach((b) => b.onclick = () => ouvrirFacture(b.dataset.pdf));
      m.showModal();
    } catch (e) { toast(e.message, true); }
  }

  $('#nouveau').onclick = () => openModal('Nouveau client', formulaire(), async (f) => {
    await api('/clients', { method: 'POST', body: corps(f) });
    toast('Client créé');
    await charger();
  });
  let t;
  $('#recherche').oninput = () => { clearTimeout(t); t = setTimeout(() => agir(charger()), 250); };
  await charger();
}

// ---------- Ventes (F5) ----------

async function vueVentes(main) {
  main.innerHTML = `
    <div class="page-head"><h1>Ventes</h1><button class="btn primary" id="nouvelle">Nouvelle vente</button></div>
    <div class="toolbar">
      <select id="filtre-statut"><option value="">Tous les statuts</option>
        <option value="BROUILLON">Brouillons</option><option value="VALIDEE">Validées</option><option value="ANNULEE">Annulées</option></select>
    </div>
    <div class="table-wrap" id="liste"></div>`;

  async function charger() {
    const s = $('#filtre-statut').value;
    const rows = await api('/commandes' + (s ? '?statut=' + s : ''));
    $('#liste').innerHTML = tableau(
      ['<th>N°</th>', '<th>Date</th>', '<th>Client</th>', '<th>Statut</th>', '<th class="num">Total</th>', '<th>Facture</th>', '<th></th>'],
      rows.map((o) => `<tr><td>#${o.id}</td><td>${dateFr(o.dateCommande)}</td><td>${esc(o.client)}</td>
        <td>${tagStatut(o.statut)}</td><td class="num">${money(o.total)}</td><td>${esc(o.numeroFacture)}</td>
        <td class="actions">${o.statut === 'BROUILLON'
          ? `<button class="btn small" data-edit="${o.id}">Modifier</button>
             <button class="btn small primary" data-val="${o.id}">Valider</button>
             <button class="btn small danger" data-ann="${o.id}">Annuler</button>`
          : o.statut === 'VALIDEE' ? `<button class="btn small" data-pdf="${o.id}">Facture PDF</button>` : ''}</td></tr>`),
      'Aucune vente.');
    document.querySelectorAll('[data-edit]').forEach((b) => b.onclick = () => editeurVente(main, Number(b.dataset.edit)));
    document.querySelectorAll('[data-pdf]').forEach((b) => b.onclick = () => ouvrirFacture(b.dataset.pdf));
    document.querySelectorAll('[data-val]').forEach((b) => b.onclick = () => {
      if (confirm('Valider cette vente ? Le stock sera diminué et la facture créée.')) {
        agir((async () => {
          const r = await api(`/commandes/${b.dataset.val}/valider`, { method: 'POST' });
          toast(`Vente validée. Facture ${r.numero}`);
          await charger();
          majBadgeAlertes();
        })());
      }
    });
    document.querySelectorAll('[data-ann]').forEach((b) => b.onclick = () => {
      if (confirm('Annuler ce brouillon ?')) {
        agir(api(`/commandes/${b.dataset.ann}/annuler`, { method: 'POST' }), 'Vente annulée', charger);
      }
    });
  }
  $('#filtre-statut').onchange = () => agir(charger());
  $('#nouvelle').onclick = () => editeurVente(main, null);
  await charger();
}

// Éditeur de vente : id = null pour une nouvelle vente, sinon modification d'un brouillon
async function editeurVente(main, id) {
  const [clients, produits, existante] = await Promise.all([
    api('/clients'), api('/produits'), id ? api('/commandes/' + id) : Promise.resolve(null),
  ]);
  let lignes = existante
    ? existante.lignes.map((l) => ({ idProduit: l.idProduit, quantite: l.quantite }))
    : [];
  const parId = (pid) => produits.find((p) => p.id === Number(pid));

  main.innerHTML = `
    <div class="page-head"><h1>${id ? 'Modifier la vente #' + id : 'Nouvelle vente'}</h1>
      <button class="btn ghost" id="retour">Retour</button></div>
    <div class="card">
      <label>Client *</label>
      <select id="client"><option value="">Choisir un client…</option>${options(clients, 'id', 'nom', existante ? existante.commande.idClient : '')}</select>
      <div class="inline-form vente-ajout" style="margin-top:14px">
        <div><label>Produit</label><select id="produit">${produits.map((p) =>
          `<option value="${p.id}">${esc(p.designation)} (stock ${p.quantiteStock}, ${money(p.prix)})</option>`).join('')}</select></div>
        <div><label>Quantité</label><input id="qte" type="number" min="1" value="1"></div>
        <button class="btn" id="ajouter" type="button">Ajouter</button>
      </div>
    </div>
    <div class="table-wrap" id="lignes"></div>
    <p class="error" id="err"></p>
    <div class="page-head" style="margin-top:14px">
      <strong id="total"></strong>
      <span>
        <button class="btn" id="brouillon">Enregistrer le brouillon</button>
        <button class="btn primary" id="valider">Enregistrer et valider</button></span>
    </div>`;

  function afficher() {
    let total = 0;
    const rows = lignes.map((l, i) => {
      const p = parId(l.idProduit);
      const st = l.quantite * Number(p.prix);
      total += st;
      const insuffisant = l.quantite > p.quantiteStock;
      return `<tr><td>${esc(p.reference)} · ${esc(p.designation)}</td>
        <td class="num">${money(p.prix)}</td>
        <td class="num"><input type="number" min="1" value="${l.quantite}" data-q="${i}" style="width:80px"></td>
        <td class="num">${money(st)}</td>
        <td>${insuffisant ? `<span class="tag danger">Stock : ${p.quantiteStock}</span>` : ''}</td>
        <td><button class="btn small danger" data-rm="${i}">Retirer</button></td></tr>`;
    });
    $('#lignes').innerHTML = tableau(
      ['<th>Produit</th>', '<th class="num">Prix</th>', '<th class="num">Quantité</th>', '<th class="num">Sous-total</th>', '<th></th>', '<th></th>'],
      rows, 'Aucune ligne : ajoutez un produit.');
    $('#total').textContent = 'Total : ' + money(total);
    document.querySelectorAll('[data-rm]').forEach((b) => b.onclick = () => { lignes.splice(Number(b.dataset.rm), 1); afficher(); });
    document.querySelectorAll('[data-q]').forEach((inp) => inp.onchange = () => {
      lignes[Number(inp.dataset.q)].quantite = Math.max(1, parseInt(inp.value, 10) || 1);
      afficher();
    });
  }

  $('#ajouter').onclick = () => {
    const pid = Number($('#produit').value);
    const q = Math.max(1, parseInt($('#qte').value, 10) || 1);
    if (!pid) return;
    const l = lignes.find((x) => x.idProduit === pid);
    if (l) l.quantite += q; else lignes.push({ idProduit: pid, quantite: q });
    afficher();
  };

  async function enregistrer(valider) {
    $('#err').textContent = '';
    if (!$('#client').value) { $('#err').textContent = 'Choisissez un client.'; return; }
    if (!lignes.length) { $('#err').textContent = 'Ajoutez au moins un produit.'; return; }
    const corps = { idClient: Number($('#client').value), lignes };
    try {
      const d = id
        ? await api('/commandes/' + id, { method: 'PUT', body: corps })
        : await api('/commandes', { method: 'POST', body: corps });
      if (valider) {
        const r = await api(`/commandes/${d.commande.id}/valider`, { method: 'POST' });
        toast(`Vente validée. Facture ${r.numero}`);
      } else {
        toast('Brouillon enregistré');
      }
      await vueVentes(main);
      majBadgeAlertes();
    } catch (e) {
      $('#err').textContent = e.message;
    }
  }
  $('#brouillon').onclick = () => enregistrer(false);
  $('#valider').onclick = () => enregistrer(true);
  $('#retour').onclick = () => agir(vueVentes(main));
  afficher();
}

// ---------- Tableau de bord KPI (F7) ----------

function graphBarres(donnees, couleur) {
  // donnees : [{ libelle, valeur }] ; barres en CSS, sans bibliothèque
  const max = Math.max(1, ...donnees.map((d) => d.valeur));
  return `<div class="bars">${donnees.map((d) =>
    `<div class="bar-col" title="${esc(d.libelle)} : ${esc(d.texte ?? d.valeur)}">
       <div class="bar-val">${esc(d.court ?? '')}</div>
       <div class="bar ${couleur || ''}" style="height:${Math.max(2, Math.round(d.valeur / max * 100))}%"></div>
       <div class="bar-lbl">${esc(d.libelle)}</div></div>`).join('')}</div>`;
}

async function vueTableau(main) {
  const k = await api('/kpi');
  const jj = (s) => new Date(s + 'T00:00:00').toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' });
  const v = k.variationMoisPct;
  const variation = v === null || v === undefined
    ? '<span class="muted">pas de ventes le mois précédent</span>'
    : `<span class="tag ${v >= 0 ? 'ok' : 'danger'}">${v >= 0 ? '+' : ''}${String(v).replace('.', ',')} % vs mois précédent</span>`;

  main.innerHTML = `
    <div class="page-head"><h1>Tableau de bord</h1></div>
    <div class="kpis">
      <div class="card kpi"><span class="muted">CA du jour</span><strong>${money(k.caJour)}</strong><small>${k.ventesJour} vente(s)</small></div>
      <div class="card kpi"><span class="muted">CA du mois</span><strong>${money(k.caMois)}</strong><small>${k.ventesMois} vente(s) · ${variation}</small></div>
      <div class="card kpi"><span class="muted">CA du mois précédent</span><strong>${money(k.caMoisPrecedent)}</strong></div>
      <div class="card kpi"><span class="muted">Valeur du stock</span><strong>${money(k.valeurStock)}</strong></div>
      <div class="card kpi"><span class="muted">Produits en alerte</span><strong>${k.produitsEnAlerte}</strong><small>dont ${k.produitsEnRupture} en rupture</small></div>
    </div>
    <div class="grid2">
      <div class="card"><h2>Chiffre d'affaires par jour (30 derniers jours)</h2>
        ${k.caParJour.length ? graphBarres(k.caParJour.map((d) => ({ libelle: jj(d.jour), valeur: Number(d.chiffreAffaires),
          texte: money(d.chiffreAffaires) + ' (' + d.nbVentes + ' vente(s))' }))) : '<div class="empty">Aucune vente validée sur la période.</div>'}</div>
      <div class="card"><h2>Mouvements de la semaine</h2>
        <p class="muted legend"><span class="dot in"></span>Entrées <span class="dot out"></span>Sorties</p>
        <div class="bars two">${k.mouvementsSemaine.map((d) => {
          const max = Math.max(1, ...k.mouvementsSemaine.map((x) => Math.max(x.entrees, x.sorties)));
          return `<div class="bar-col" title="${jj(d.jour)} : ${d.entrees} entrée(s), ${d.sorties} sortie(s)">
            <div class="pair"><div class="bar in" style="height:${Math.max(2, Math.round(d.entrees / max * 100))}%"></div>
            <div class="bar out" style="height:${Math.max(2, Math.round(d.sorties / max * 100))}%"></div></div>
            <div class="bar-lbl">${jj(d.jour)}</div></div>`;
        }).join('')}</div>
        <p class="muted">Total : ${k.mouvementsSemaine.reduce((a, d) => a + d.entrees, 0)} entrées, ${k.mouvementsSemaine.reduce((a, d) => a + d.sorties, 0)} sorties.</p></div>
    </div>
    <h2>Produits les plus vendus</h2>
    <div class="table-wrap">${tableau(
      ['<th>#</th>', '<th>Référence</th>', '<th>Désignation</th>', '<th class="num">Quantité vendue</th>', '<th class="num">CA</th>'],
      k.topProduits.map((p, i) => `<tr><td>${i + 1}</td><td>${esc(p.reference)}</td><td>${esc(p.designation)}</td>
        <td class="num">${p.quantiteVendue}</td><td class="num">${money(p.chiffreAffaires)}</td></tr>`),
      'Aucune vente validée pour le moment.')}</div>`;
}

route();
