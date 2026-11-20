/*
 * Narrauksen kielituki. Sovelluksen tekstit ovat suomea (lähdekieli). Kun käyttäjä on valinnut toisen kielen,
 * sen sanakirja (/i18n/<kieli>.json) ladataan ja selaimessa näkyvät tekstit käännetään lennossa:
 * tekstisolmut (myös avoimissa shadow-juurissa), placeholder-/title-/aria-label-attribuutit ja sivun otsikko.
 * Vain valittu kieli on ladattuna; kielen vaihto lataa uuden sanakirjan (edistymispalkki) ja unohtaa edellisen.
 *
 * Sanakirjan avain on suomenkielinen teksti. Avain voi sisältää paikkamerkkejä {0}, {1}, ... (esim. "Paino {0} kg").
 */
(function () {
  'use strict';

  var COOKIE = 'narraus-lang';
  var LANGUAGES = [
    { code: 'fi', name: 'Suomi' },
    { code: 'sv', name: 'Svenska' },
    { code: 'no', name: 'Norsk' },
    { code: 'en', name: 'English' }
  ];
  var ATTRIBUTES = ['placeholder', 'title', 'aria-label'];
  var SEPARATOR = /(\s+·\s+)/;

  function readCookie() {
    var m = document.cookie.match(new RegExp('(?:^|; )' + COOKIE + '=([^;]*)'));
    var code = m ? decodeURIComponent(m[1]) : 'fi';
    return LANGUAGES.some(function (l) { return l.code === code; }) ? code : 'fi';
  }

  function writeCookie(code) {
    document.cookie = COOKIE + '=' + encodeURIComponent(code) + '; path=/; max-age=31536000; SameSite=Lax';
  }

  function escapeRegExp(s) {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  var current = readCookie();
  var exact = null;     // suomi -> käännös
  var templates = [];   // [{re, target}]
  var processed = new WeakMap(); // solmu -> viimeksi asetettu (käännetty) arvo
  var observedRoots = new WeakSet();

  function setDictionary(map) {
    exact = Object.create(null);
    templates = [];
    Object.keys(map).forEach(function (key) {
      var value = map[key];
      if (/\{\d+\}/.test(key)) {
        var pattern = escapeRegExp(key).replace(/\\\{(\d+)\\\}/g, '([\\s\\S]*?)');
        templates.push({ re: new RegExp('^' + pattern + '$'), target: value, size: key.length });
      } else {
        exact[key] = value;
        exact[key.replace(/\s+/g, ' ')] = value;
      }
    });
    templates.sort(function (a, b) { return b.size - a.size; });
  }

  // Palauttaa käännöksen tai null, jos tekstille ei ole käännöstä
  function translateCore(core) {
    if (!core) return null;
    if (core in exact) return exact[core];
    var norm = core.replace(/\s+/g, ' ');
    if (norm in exact) return exact[norm];

    for (var i = 0; i < templates.length; i++) {
      var m = templates[i].re.exec(core) || templates[i].re.exec(norm);
      if (m) {
        return templates[i].target.replace(/\{(\d+)\}/g, function (all, n) {
          var arg = m[Number(n) + 1];
          return translateArgument(arg);
        });
      }
    }

    // Useasta osasta koostuva teksti: "Paino 5 kg  ·  Pituus 100 cm  ·  📍 Päijänne"
    var parts = core.split(SEPARATOR);
    if (parts.length > 1) {
      var changed = false;
      var out = parts.map(function (p, idx) {
        if (idx % 2 === 1) return p;
        var t = translateCore(p.trim());
        if (t == null) return p;
        changed = true;
        return t;
      });
      return changed ? out.join('') : null;
    }

    // "Ryhmät (3)", "Käynnissä (2)"
    var counted = /^(.+?) \((\d+)\)$/.exec(core);
    if (counted) {
      var head = translateCore(counted[1]);
      if (head != null) return head + ' (' + counted[2] + ')';
    }

    // Kuvake tekstin edessä: "🏆 Pisin kala", "📏 Voittaa se, jolla on ..." (kuvake säilyy, teksti käännetään)
    var iconic = /^([^\p{L}\p{N}]+\s+)(\S[\s\S]*)$/u.exec(core);
    if (iconic) {
      var rest = translateCore(iconic[2]);
      if (rest != null) return iconic[1] + rest;
    }
    return null;
  }

  // Paikkamerkin arvo käännetään, jos se on tunnettu teksti ("App ja web", "Sinä, Matti")
  function translateArgument(arg) {
    var whole = translateCore(arg.trim());
    if (whole != null) return arg.replace(arg.trim(), whole);
    if (arg.indexOf(', ') > 0) {
      var changed = false;
      var parts = arg.split(', ').map(function (p) {
        var t = translateCore(p.trim());
        if (t == null) return p;
        changed = true;
        return t;
      });
      if (changed) return parts.join(', ');
    }
    return arg;
  }

  function translateString(s) {
    if (!exact || !s || !s.trim()) return null;
    var lead = /^\s*/.exec(s)[0];
    var trail = /\s*$/.exec(s)[0];
    var core = s.slice(lead.length, s.length - trail.length);
    var t = translateCore(core);
    return t == null ? null : lead + t + trail;
  }

  function translateTextNode(node) {
    if (!exact) return;
    var value = node.data;
    if (processed.get(node) === value) return; // oma aiempi käännös
    var t = translateString(value);
    if (t != null && t !== value) {
      processed.set(node, t);
      node.data = t;
    }
  }

  function translateAttributes(el) {
    if (!exact || el.nodeType !== 1) return;
    for (var i = 0; i < ATTRIBUTES.length; i++) {
      var name = ATTRIBUTES[i];
      var v = el.getAttribute(name);
      if (v) {
        var t = translateString(v);
        if (t != null && t !== v) el.setAttribute(name, t);
      }
    }
  }

  function skip(node) {
    var n = node.nodeName;
    return n === 'SCRIPT' || n === 'STYLE' || n === 'TEXTAREA';
  }

  function scan(root) {
    if (!root) return;
    if (root.nodeType === 3) { translateTextNode(root); return; }
    if (root.nodeType === 1) {
      if (skip(root)) return;
      translateAttributes(root);
      if (root.shadowRoot) observeRoot(root.shadowRoot);
    }
    var child = root.firstChild;
    while (child) {
      scan(child);
      child = child.nextSibling;
    }
    // Slotittamaton shadow-sisältö käsitellään observeRoot-kutsun kautta
  }

  var observer = new MutationObserver(function (mutations) {
    for (var i = 0; i < mutations.length; i++) {
      var m = mutations[i];
      if (m.type === 'characterData') {
        translateTextNode(m.target);
      } else if (m.type === 'attributes') {
        translateAttributes(m.target);
      } else {
        for (var j = 0; j < m.addedNodes.length; j++) scan(m.addedNodes[j]);
      }
    }
    scheduleShadowScan();
  });

  // Uudet shadow-juuret otetaan seurantaan heti luonnin yhteydessä (ei suomenkielistä välähdystä)
  var pendingRoots = [];
  var nativeAttachShadow = Element.prototype.attachShadow;
  Element.prototype.attachShadow = function (init) {
    var root = nativeAttachShadow.call(this, init);
    if (current !== 'fi') {
      if (exact) observeRoot(root); else pendingRoots.push(root);
    }
    return root;
  };

  function observeRoot(root) {
    if (observedRoots.has(root)) return;
    observedRoots.add(root);
    observer.observe(root, { subtree: true, childList: true, characterData: true, attributes: true,
      attributeFilter: ATTRIBUTES });
    scan(root);
  }

  // Shadow-juuret syntyvät komponentin liittämisen jälkeen: etsitään uudet juuret pienellä viiveellä
  var shadowTimer = null;
  function scheduleShadowScan() {
    if (shadowTimer) return;
    shadowTimer = setTimeout(function () {
      shadowTimer = null;
      findShadowRoots(document.documentElement);
    }, 60);
  }

  function findShadowRoots(root) {
    var all = root.querySelectorAll('*');
    for (var i = 0; i < all.length; i++) {
      var sr = all[i].shadowRoot;
      if (sr && !observedRoots.has(sr)) observeRoot(sr);
      if (sr) findShadowRoots(sr);
    }
  }

  function start() {
    if (document.documentElement) document.documentElement.lang = current === 'no' ? 'nb' : current;
    if (!exact) return;
    pendingRoots.forEach(observeRoot);
    pendingRoots = [];
    observeRoot(document);
    var title = document.querySelector('title');
    if (title) {
      var tt = translateString(title.textContent);
      if (tt != null) title.textContent = tt;
      observer.observe(title, { childList: true, characterData: true, subtree: true });
    }
    findShadowRoots(document.documentElement);
    // Satunnaisesti myöhemmin syntyvät shadow-juuret
    var tries = 0;
    var iv = setInterval(function () {
      findShadowRoots(document.documentElement);
      if (++tries > 20) clearInterval(iv);
    }, 250);
  }

  // Sivu pidetään piilossa, kunnes sanakirja on ladattu (ei suomenkielistä välähdystä)
  function fetchDictionary(code, onProgress) {
    return fetch('/i18n/' + code + '.json', { credentials: 'same-origin' }).then(function (resp) {
      if (!resp.ok) throw new Error('HTTP ' + resp.status);
      var total = Number(resp.headers.get('Content-Length')) || 0;
      if (!resp.body || !resp.body.getReader) {
        return resp.json().then(function (j) { if (onProgress) onProgress(1); return j; });
      }
      var reader = resp.body.getReader();
      var chunks = [];
      var received = 0;
      function pump() {
        return reader.read().then(function (r) {
          if (r.done) return;
          chunks.push(r.value);
          received += r.value.length;
          if (onProgress) onProgress(total ? Math.min(0.99, received / total) : -1);
          return pump();
        });
      }
      return pump().then(function () {
        var all = new Uint8Array(received);
        var pos = 0;
        chunks.forEach(function (c) { all.set(c, pos); pos += c.length; });
        if (onProgress) onProgress(1);
        return JSON.parse(new TextDecoder('utf-8').decode(all));
      });
    });
  }

  function reveal() {
    document.documentElement.classList.remove('narraus-i18n-loading');
  }

  if (current !== 'fi') {
    var hide = document.createElement('style');
    hide.textContent = 'html.narraus-i18n-loading body{visibility:hidden}';
    document.head.appendChild(hide);
    document.documentElement.classList.add('narraus-i18n-loading');
    setTimeout(reveal, 4000); // varmuus: sivu ei jää piiloon, vaikka lataus epäonnistuisi
    fetchDictionary(current).then(function (map) {
      setDictionary(map);
      var go = function () { start(); reveal(); };
      if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', go); else go();
    }).catch(function () { reveal(); });
  } else {
    document.documentElement.lang = 'fi';
  }

  // ---- Kielen vaihto: edistymispalkki, sanakirjan lataus, sitten sivun uudelleenlataus ----

  function showProgress(name) {
    var overlay = document.createElement('div');
    overlay.className = 'narraus-lang-overlay';
    overlay.innerHTML = '<div class="narraus-lang-card"><div class="narraus-lang-name"></div>' +
      '<div class="narraus-lang-bar"><div class="narraus-lang-fill"></div></div></div>';
    overlay.querySelector('.narraus-lang-name').textContent = name + ' …';
    document.body.appendChild(overlay);
    var fill = overlay.querySelector('.narraus-lang-fill');
    var bar = overlay.querySelector('.narraus-lang-bar');
    return {
      set: function (p) {
        if (p < 0) { bar.classList.add('indeterminate'); return; }
        bar.classList.remove('indeterminate');
        fill.style.width = Math.round(p * 100) + '%';
      },
      remove: function () { overlay.remove(); }
    };
  }

  function setLanguage(code) {
    var lang = LANGUAGES.filter(function (l) { return l.code === code; })[0];
    if (!lang || code === current) return Promise.resolve(false);
    var progress = showProgress(lang.name);
    var started = Date.now();
    var load = code === 'fi' ? Promise.resolve(null) : fetchDictionary(code, progress.set);
    return load.then(function () {
      // Edistymispalkki näkyy vähintään hetken, jotta vaihto ei vaikuta tökkivältä
      var wait = Math.max(0, 500 - (Date.now() - started));
      return new Promise(function (r) { setTimeout(r, wait); });
    }).then(function () {
      progress.set(1);
      writeCookie(code);
      // Edellinen kieli unohdetaan; sivu rakentuu uudelleen valitulla kielellä
      exact = null;
      templates = [];
      window.location.reload();
      return true;
    }).catch(function () {
      progress.remove();
      return false;
    });
  }

  window.narrausLang = {
    languages: LANGUAGES,
    current: function () { return current; },
    set: setLanguage
  };
})();
