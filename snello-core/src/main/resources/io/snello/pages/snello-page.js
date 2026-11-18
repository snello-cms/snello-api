/*
 * snello page client: loaded by every workflow page (/p/{name}?task={uuid}), before the page body.
 *
 *   await snello.ready()                    Keycloak login (public client from window.SNELLO_PAGE), token refresh
 *   await snello.api.get('rent_contracts/' + id)          REST on /api with the viewer's token
 *   snello.api.post / put / del (path, body);  snello.api.blob(path) -> Blob (protected files, prints)
 *   const ctx = await snello.context()      {task, record, fields, workflow, user} of ?task=...
 *   await snello.decide('APPROVE', comment) / snello.undo()
 *   snello.renderTask(el)                   generic page: record fields + decision bar (what /p/task shows)
 *   snello.renderDecisions(el)              only the decision bar (status, comment, buttons, UNDO): put it at the end
 *                                           of a custom page
 *   snello.onChange(fn)                     called with the fresh context every 10 s and after each decision
 *   snello.params                           the URL query as an object, e.g. /p/new-event?key=abc -> {key: 'abc'}:
 *                                           a page without a task reads its record with snello.api and params
 *   snello.fatal(err)                       replaces the page with "not allowed / not found" (err.status 403 / 404)
 *   snello.safeHtml(html)                   rich text from a record (tinymce) -> HTML safe for innerHTML
 * The sp-* classes (sp-wrap, sp-card, sp-table, sp-btn...) are styled on every page, whatever the page calls.
 *
 * Every rule (who may decide, which decisions, task still open) is enforced by snello, not here.
 */
(function () {
  'use strict';
  const CFG = window.SNELLO_PAGE || {};
  const API = (CFG.api || '/api').replace(/\/+$/, '');
  const KEYCLOAK_JS = 'https://cdn.jsdelivr.net/npm/keycloak-js@26.2.0/lib/keycloak.js';
  const params = Object.freeze(Object.fromEntries(new URLSearchParams(location.search)));
  const taskId = params.task || null;
  let kc = null;
  let readyPromise = null;
  const listeners = [];
  let lastContext = null;
  let timer = null;

  const log = (...a) => console.log('[snello-page]', ...a);

  function ready() {
    if (readyPromise) return readyPromise;
    readyPromise = (async () => {
      const {default: Keycloak} = await import(KEYCLOAK_JS);
      kc = new Keycloak(CFG.keycloak);
      await kc.init({onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false});
      setInterval(() => kc.updateToken(60).catch(() => kc.login()), 30000);
      log('logged in as', kc.tokenParsed && kc.tokenParsed.preferred_username, 'page', CFG.name, 'task', taskId);
      return {username: kc.tokenParsed && kc.tokenParsed.preferred_username, token: kc.tokenParsed};
    })();
    return readyPromise;
  }

  async function call(method, path, body) {
    await ready();
    await kc.updateToken(30).catch(() => kc.login());
    const res = await fetch(API + '/' + String(path).replace(/^\/+/, ''), {
      method,
      headers: Object.assign({'Authorization': 'Bearer ' + kc.token, 'Accept': 'application/json'},
        body !== undefined ? {'Content-Type': 'application/json'} : {}),
      body: body !== undefined ? JSON.stringify(body) : undefined
    });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
    if (!res.ok) {
      const msg = (data && (data.msg || data.error || data.message)) || res.statusText;
      log(method, path, '->', res.status, msg);
      const err = new Error(msg);
      err.status = res.status;
      throw err;
    }
    return data;
  }

  /** A file with the viewer's token (e.g. a protected print): resolves to a Blob. */
  async function blob(path) {
    await ready();
    await kc.updateToken(30).catch(() => kc.login());
    const res = await fetch(API + '/' + String(path).replace(/^\/+/, ''), {headers: {'Authorization': 'Bearer ' + kc.token, 'Accept': '*/*'}});
    if (!res.ok) throw new Error(path + ': ' + res.status);
    return res.blob();
  }

  const api = {
    blob,
    get: (p) => call('GET', p),
    post: (p, b) => call('POST', p, b === undefined ? {} : b),
    put: (p, b) => call('PUT', p, b),
    del: (p) => call('DELETE', p)
  };

  /** Not allowed / unknown task: replace the whole page (a custom page would otherwise stay half drawn). */
  function fatal(e) {
    ensureStyle();
    const who = kc && kc.tokenParsed ? kc.tokenParsed.preferred_username : null;
    const thing = taskId ? 'this task' : 'this page';
    const what = e.status === 403 ? 'You are not allowed to open ' + thing + '.'
      : e.status === 404 ? (taskId ? 'This task does not exist (any more).' : 'Not found (any more).')
      : 'This page cannot be shown.';
    log('fatal', e.status, e.message);
    document.body.innerHTML = '<div class="sp-wrap"><div class="sp-card"><h1 class="sp-h1">' + esc(what) + '</h1>'
      + '<div class="sp-muted">' + esc(e.message) + (who ? ' · signed in as <b>' + esc(who) + '</b>' : '') + '</div>'
      + (who ? '<div class="sp-btns"><button class="sp-btn sp-undo" id="sp-switch">Sign in as someone else</button></div>' : '')
      + '</div></div>';
    const sw = document.getElementById('sp-switch');
    if (sw) sw.addEventListener('click', () => kc.logout({redirectUri: location.href}));
    if (timer) { clearInterval(timer); timer = null; }
  }

  async function context() {
    if (!taskId) { const e = new Error('this page needs ?task=<task id>'); fatal(e); throw e; }
    try {
      lastContext = await api.get('humantasks/' + encodeURIComponent(taskId) + '/context');
    } catch (e) {
      if (e.status === 403 || e.status === 404 || e.status === 400) fatal(e);
      throw e;
    }
    listeners.forEach(fn => { try { fn(lastContext); } catch (e) { console.error(e); } });
    return lastContext;
  }

  async function decide(decision, comment) {
    log('decide', decision);
    await api.post('humantasks/' + encodeURIComponent(taskId) + '/decide', {decision, comment: comment || null});
    return context();
  }

  async function undo() {
    log('undo');
    await api.post('humantasks/' + encodeURIComponent(taskId) + '/undo');
    return context();
  }

  function onChange(fn) {
    listeners.push(fn);
    if (lastContext) fn(lastContext);
    if (!timer) timer = setInterval(() => context().catch(e => log('refresh failed', e.message)), 10000);
  }

  // ---------------------------------------------------------------- rendering helpers
  const esc = (v) => String(v == null ? '' : v).replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
  const fmt = (v) => {
    if (v == null || v === '') return '<span class="sp-muted">–</span>';
    if (typeof v === 'string' && /^\d{4}-\d\d-\d\dT\d\d:\d\d/.test(v)) return esc(new Date(v).toLocaleString());
    return esc(v);
  };
  const STYLE = `
    .sp-wrap{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;max-width:860px;margin:24px auto;padding:0 16px;color:#222}
    .sp-card{border:1px solid #ddd;border-radius:8px;padding:16px 20px;margin-bottom:16px;background:#fff}
    .sp-h1{font-size:22px;margin:0 0 4px} .sp-muted{color:#888} .sp-small{font-size:13px}
    .sp-table{width:100%;border-collapse:collapse} .sp-table td{padding:6px 8px;border-bottom:1px solid #eee;vertical-align:top}
    .sp-table td:first-child{color:#666;width:34%}
    .sp-status{display:inline-block;padding:2px 10px;border-radius:12px;font-size:13px;font-weight:600}
    .sp-OPEN{background:#cfe2ff;color:#052c65} .sp-CLOSED{background:#d1e7dd;color:#0a3622} .sp-CANCELLED{background:#f8d7da;color:#58151c}
    .sp-btns{display:flex;gap:10px;flex-wrap:wrap;margin-top:12px}
    .sp-btn{border:0;border-radius:6px;padding:10px 22px;font-size:15px;font-weight:600;cursor:pointer;color:#fff;background:#0d6efd}
    .sp-btn:disabled{opacity:.5;cursor:default} .sp-btn.sp-neg{background:#dc3545} .sp-btn.sp-undo{background:#6c757d}
    .sp-btn.sp-chosen{outline:3px solid #222}
    .sp-comment{width:100%;min-height:60px;margin-top:10px;padding:8px;border:1px solid #ccc;border-radius:6px;font:inherit;box-sizing:border-box}
    .sp-error{background:#f8d7da;color:#58151c;padding:10px;border-radius:6px;margin-top:10px}
    .sp-note{background:#fff3cd;color:#664d03;padding:10px;border-radius:6px;margin-top:10px}`;
  function ensureStyle() {
    if (document.getElementById('snello-page-style')) return;
    const s = document.createElement('style');
    s.id = 'snello-page-style';
    s.textContent = STYLE;
    document.head.appendChild(s);
  }
  const NEGATIVE = /^(REJECT|REFUSE|DENY|DECLINE|NO|DISAPPROVE)/i;

  // Rich text from records (tinymce fields) is written by other people: keep the formatting, drop everything that
  // can run code (scripts, event handlers, styles, iframes, javascript: links...). Allowlist, not a blocklist.
  const SAFE_TAGS = new Set(['P', 'BR', 'B', 'STRONG', 'I', 'EM', 'U', 'S', 'SUB', 'SUP', 'SPAN', 'DIV', 'BLOCKQUOTE',
    'PRE', 'CODE', 'UL', 'OL', 'LI', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'HR', 'A', 'TABLE', 'THEAD', 'TBODY', 'TR',
    'TH', 'TD']);
  const DROP_WITH_CONTENT = new Set(['SCRIPT', 'STYLE', 'TEMPLATE', 'IFRAME', 'OBJECT', 'EMBED', 'NOSCRIPT', 'SVG', 'MATH']);
  /** HTML from data -> HTML safe to put in innerHTML (unknown tags are unwrapped, their text kept). */
  function safeHtml(html) {
    // DOMParser documents are inert: nothing in them runs or loads while we clean
    const doc = new DOMParser().parseFromString('<body>' + String(html == null ? '' : html) + '</body>', 'text/html');
    let dropped = 0;
    const clean = (node) => {
      for (const child of Array.from(node.childNodes)) {
        if (child.nodeType === Node.TEXT_NODE) continue;
        if (child.nodeType !== Node.ELEMENT_NODE) { child.remove(); continue; }
        if (DROP_WITH_CONTENT.has(child.tagName)) { child.remove(); dropped++; continue; }
        clean(child);
        if (!SAFE_TAGS.has(child.tagName)) { child.replaceWith(...child.childNodes); dropped++; continue; }
        for (const a of Array.from(child.attributes)) {
          const keep = child.tagName === 'A' && a.name === 'href' && /^(https?:|mailto:)/i.test(a.value.trim());
          if (!keep) child.removeAttribute(a.name);
        }
        if (child.tagName === 'A') { child.setAttribute('target', '_blank'); child.setAttribute('rel', 'noopener noreferrer'); }
      }
    };
    clean(doc.body);
    if (dropped) log('safeHtml: dropped', dropped, 'unsafe element(s)');
    return doc.body.innerHTML;
  }

  function fieldValue(f, record) {
    const v = record ? record[f.name] : null;
    if (f.type === 'media' && v) {
      return '<a href="' + esc(API + '/documents/' + encodeURIComponent(v) + '/download') + '" target="_blank" rel="noopener">download</a>';
    }
    if (f.type === 'tinymce' && v) return safeHtml(v);
    return fmt(v);
  }

  /** The decision bar: status, who decided, comment box, one button per decision, UNDO while still open. */
  function renderDecisions(el) {
    ensureStyle();
    el.innerHTML = '<div class="sp-card sp-muted">Loading…</div>';
    let busy = false;
    let drawn = null;
    const draw = (ctx) => {
      const t = ctx.task;
      // the 10 s refresh must not wipe a comment being typed or swap buttons under the cursor: redraw only when the
      // task changed, and carry an unsent comment over
      const key = [t.status, t.decision, t.decided_at, t.decision_comment].join('|');
      if (key === drawn) return;
      drawn = key;
      const typing = el.querySelector('.sp-comment');
      const pending = typing ? typing.value : '';
      const hadFocus = typing && document.activeElement === typing;
      const open = t.status === 'OPEN';
      const decisions = String(t.decisions || '').split(',').filter(Boolean);
      el.innerHTML = '<div class="sp-card">'
        + '<div><span class="sp-status sp-' + esc(t.status) + '">' + esc(t.status) + '</span> '
        + (t.decision ? 'decision <b>' + esc(t.decision) + '</b> by ' + esc(t.decided_by) + ' <span class="sp-muted sp-small">' + fmt(t.decided_at) + '</span>' : '<span class="sp-muted">waiting for a decision</span>')
        + '</div>'
        + (t.decision_comment ? '<div class="sp-small" style="margin-top:6px">' + esc(t.decision_comment) + '</div>' : '')
        + (open && t.decision ? '<div class="sp-note sp-small">' + (t.instance_id
            ? 'The workflow applies the decision shortly: until then you can UNDO it.'
            : 'You can UNDO it until the task is closed.') + '</div>' : '')
        + (open && !t.decision ? '<textarea class="sp-comment" placeholder="comment (optional)"></textarea>' : '')
        + '<div class="sp-btns">'
        + (open ? decisions.map(d => '<button class="sp-btn' + (NEGATIVE.test(d) ? ' sp-neg' : '') + (t.decision === d ? ' sp-chosen' : '') + '" data-d="' + esc(d) + '"' + (t.decision ? ' disabled' : '') + '>' + esc(d) + '</button>').join('') : '')
        + (open && t.decision ? '<button class="sp-btn sp-undo" data-undo="1">UNDO</button>' : '')
        + '</div><div class="sp-err"></div></div>';
      const box = el.querySelector('.sp-comment');
      if (box && pending) box.value = pending;
      if (box && hadFocus) box.focus();
      el.querySelectorAll('[data-d]').forEach(b => b.addEventListener('click', async () => {
        if (busy) return;
        busy = true;
        try { await decide(b.dataset.d, (el.querySelector('.sp-comment') || {}).value); }
        catch (e) { el.querySelector('.sp-err').innerHTML = '<div class="sp-error">' + esc(e.message) + '</div>'; }
        finally { busy = false; }
      }));
      el.querySelectorAll('[data-undo]').forEach(b => b.addEventListener('click', async () => {
        if (busy) return;
        busy = true;
        try { await undo(); } catch (e) { el.querySelector('.sp-err').innerHTML = '<div class="sp-error">' + esc(e.message) + '</div>'; }
        finally { busy = false; }
      }));
    };
    onChange(draw);
    context().catch(e => { el.innerHTML = '<div class="sp-error">' + esc(e.message) + '</div>'; });
  }

  /** Generic page: title, description, the record's fields (snello metadata order), workflow position, decision bar. */
  function renderTask(el) {
    ensureStyle();
    el.innerHTML = '<div class="sp-wrap"><div class="sp-head"></div><div class="sp-record"></div><div class="sp-decisions"></div></div>';
    onChange((ctx) => {
      const t = ctx.task;
      el.querySelector('.sp-head').innerHTML = '<div class="sp-card"><h1 class="sp-h1">' + esc(t.title) + '</h1>'
        + (t.description ? '<div>' + esc(t.description) + '</div>' : '')
        + '<div class="sp-muted sp-small" style="margin-top:6px">'
        + (t.workflow_name ? 'workflow ' + esc(t.workflow_namespace) + ':' + esc(t.workflow_name) : '')
        + (ctx.workflow && ctx.workflow.currentTask ? ' — now at <b>' + esc(ctx.workflow.currentTask) + '</b> (' + esc(ctx.workflow.status) + ')' : '')
        + ' · viewer ' + esc(ctx.user && ctx.user.name) + '</div></div>';
      const fields = (ctx.fields || []).filter(f => !['created_by'].includes(f.name));
      el.querySelector('.sp-record').innerHTML = ctx.record
        ? '<div class="sp-card"><table class="sp-table">' + fields.map(f => '<tr><td>' + esc(f.label || f.name) + '</td><td>' + fieldValue(f, ctx.record) + '</td></tr>').join('') + '</table></div>'
        : '';
    });
    renderDecisions(el.querySelector('.sp-decisions'));
  }

  window.snello = {ready, api, context, decide, undo, onChange, renderTask, renderDecisions, taskId, params, fatal, safeHtml, config: CFG, esc};
  ensureStyle();
  log('page', CFG.name, 'task', taskId, 'params', Object.keys(params).join(',') || '-');
})();
