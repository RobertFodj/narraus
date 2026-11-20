/*
 * Käynnistysvahti: jos sovellus ei ole piirtynyt näytölle 10 sekunnissa (liian vanha selain, estetty skripti,
 * katkennut yhteys), näytetään selkeä ohje tyhjän sivun sijaan ja ilmoitetaan syy palvelimelle (/client-error).
 * Teksti on käyttäjän kielellä (narraus-lang-eväste). Kirjoitettu ES5:llä, jotta se toimii vanhoissakin selaimissa.
 */
(function () {
  var TEXTS = {
    fi: ['Narrauksen avaaminen ei onnistunut', 'Selaimesi voi olla liian vanha. Päivitä selain tai puhelimen järjestelmä (iPhonessa vaaditaan iOS 17 tai uudempi) tai kokeile toista selainta.', 'Yritä uudelleen'],
    sv: ['Det gick inte att öppna Narraus', 'Din webbläsare kan vara för gammal. Uppdatera webbläsaren eller telefonens system (på iPhone krävs iOS 17 eller nyare) eller prova en annan webbläsare.', 'Försök igen'],
    no: ['Kunne ikke åpne Narraus', 'Nettleseren din kan være for gammel. Oppdater nettleseren eller telefonens system (på iPhone kreves iOS 17 eller nyere) eller prøv en annen nettleser.', 'Prøv igjen'],
    en: ['Narraus could not be opened', 'Your browser may be too old. Update the browser or your phone\'s system (on iPhone iOS 17 or newer is required) or try another browser.', 'Try again']
  };
  var errors = [];
  window.addEventListener('error', function (e) {
    if (errors.length < 3) errors.push((e.message || 'error') + ' @' + (e.filename || '') + ':' + (e.lineno || ''));
  });

  function language() {
    var m = document.cookie.match(/(?:^|; )narraus-lang=([^;]*)/);
    var code = m ? decodeURIComponent(m[1]) : 'fi';
    return TEXTS[code] ? code : 'fi';
  }

  function rendered() {
    return !!document.querySelector('vaadin-app-layout, vaadin-login-form, vaadin-vertical-layout, vaadin-horizontal-layout');
  }

  function features() {
    try {
      return 'popover=' + ('popover' in HTMLElement.prototype) + ' hasSel=' + (window.CSS && CSS.supports && CSS.supports('selector(:has(*))'))
        + ' structuredClone=' + (typeof structuredClone) + ' cookies=' + navigator.cookieEnabled;
    } catch (e) {
      return 'features-unknown';
    }
  }

  function report() {
    try {
      var data = JSON.stringify({ url: location.pathname, ua: navigator.userAgent, lang: navigator.language,
        features: features(), errors: errors });
      if (navigator.sendBeacon) navigator.sendBeacon('/client-error', data);
      else { var x = new XMLHttpRequest(); x.open('POST', '/client-error'); x.send(data); }
    } catch (e) { /* ilmoitus on vain apu */ }
  }

  // Kaatuminen kesken latauksen (esim. iPhonen "Toistuva ongelma verkkosivulla"): sivun skriptit eivät ehdi ilmoittaa
  // mitään, joten merkitään lataus alkaneeksi ja poistetaan merkintä vasta, kun sovellus on piirtynyt.
  // Jos merkintä on jäänyt edelliseltä kerralta, ilmoitetaan siitä palvelimelle seuraavalla latauksella.
  var KEY = 'narraus-boot-started';
  try {
    var previous = window.localStorage.getItem(KEY);
    if (previous && Date.now() - Number(previous) < 600000) {
      errors.push('edellinen lataus ei päättynyt (kaatuminen tai keskeytys) ' + Math.round((Date.now() - Number(previous)) / 1000) + ' s sitten');
      report();
    }
    window.localStorage.setItem(KEY, String(Date.now()));
  } catch (e) { /* tallennus ei käytössä */ }

  function markRendered() {
    try { window.localStorage.removeItem(KEY); } catch (e) { /* ei väliä */ }
  }

  var box = null;
  function show() {
    if (box) return;
    var t = TEXTS[language()];
    box = document.createElement('div');
    box.setAttribute('id', 'narraus-boot-failed');
    box.style.cssText = 'position:fixed;left:0;right:0;top:0;bottom:0;z-index:2147483647;display:flex;align-items:center;justify-content:center;background:#f2f4f7;font-family:sans-serif;color:#111;padding:24px;box-sizing:border-box;text-align:center';
    var card = document.createElement('div');
    card.style.cssText = 'max-width:26em';
    var h = document.createElement('h2'); h.textContent = '🎣 ' + t[0];
    var p = document.createElement('p'); p.textContent = t[1]; p.style.lineHeight = '1.5';
    var b = document.createElement('button'); b.textContent = t[2];
    b.style.cssText = 'font-size:16px;padding:10px 18px;border-radius:10px;border:0;background:#1b5fc4;color:#fff';
    b.onclick = function () { location.reload(); };
    card.appendChild(h); card.appendChild(p); card.appendChild(b);
    box.appendChild(card);
    (document.body || document.documentElement).appendChild(box);
    report();
  }

  function hide() {
    if (box && box.parentNode) box.parentNode.removeChild(box);
    box = null;
  }

  var early = setInterval(function () {
    if (rendered()) { markRendered(); clearInterval(early); }
  }, 500);

  setTimeout(function () {
    clearInterval(early);
    if (rendered()) markRendered();
    if (!rendered()) show();
    // Hidas yhteys: ohje poistuu, kun sovellus lopulta piirtyy
    var iv = setInterval(function () {
      if (rendered()) { hide(); markRendered(); clearInterval(iv); }
    }, 1000);
    setTimeout(function () { clearInterval(iv); }, 120000);
  }, 10000);
})();
