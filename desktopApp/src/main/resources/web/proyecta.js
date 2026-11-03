// Cliente web de Proyecta: conexión WebSocket + utilidades compartidas por
// el control remoto (index.html), la vista de escenario y la página para OBS.
// Sin dependencias: debe funcionar en la red de la iglesia aunque no haya internet.
"use strict";

const Proyecta = (() => {
  const S = {
    connected: false,
    state: { title: "", slides: [], index: -1, blank: false, isBible: false, songId: null, stageMessage: "" },
    songs: [],
    bibleName: null,
    plan: { items: [], current: -1 },
  };
  const listeners = {};
  let ws = null;

  function on(event, fn) { (listeners[event] = listeners[event] || []).push(fn); }
  function emit(event, arg) { (listeners[event] || []).forEach((fn) => fn(arg)); }

  function connect() {
    const proto = location.protocol === "https:" ? "wss://" : "ws://";
    ws = new WebSocket(proto + location.host + "/ws");
    ws.onopen = () => { S.connected = true; emit("status"); };
    ws.onclose = () => { S.connected = false; emit("status"); setTimeout(connect, 2000); };
    ws.onerror = () => ws.close();
    ws.onmessage = (e) => {
      const m = JSON.parse(e.data);
      if (m.type === "state") { S.state = m.state; emit("state"); }
      else if (m.type === "library") { S.songs = m.songs; S.bibleName = m.bibleName; emit("library"); }
      else if (m.type === "plan") { S.plan = m.plan; emit("plan"); }
      else if (m.type === "error") emit("error", m.message);
    };
  }

  function send(type, data) {
    if (ws && ws.readyState === 1) ws.send(JSON.stringify(Object.assign({ type }, data || {})));
  }

  // --- Igual que en shared/Model.kt ---
  function sections(st) {
    if (st.isBible || !st.slides.some((s) => s.label)) return [];
    const out = [];
    st.slides.forEach((s, i) => {
      if (s.label) out.push({ label: s.label, start: i });
      else if (i === 0) out.push({ label: "Inicio", start: 0 });
    });
    return out;
  }
  function currentSection(st) {
    const secs = sections(st);
    let k = -1;
    secs.forEach((s, i) => { if (s.start <= st.index) k = i; });
    return k;
  }
  function jumpTarget(st, label) {
    const secs = sections(st);
    const starts = secs.filter((s) => s.label === label).map((s) => s.start);
    if (!starts.length) return null;
    const cur = secs[currentSection(st)];
    const here = cur ? cur.start : -1;
    const after = starts.find((x) => x > here);
    return after !== undefined ? after : starts[0];
  }
  function uniqueLabels(st) { return [...new Set(sections(st).map((s) => s.label))]; }

  function normalize(s) { return s.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, ""); }
  function searchSongs(query) {
    const words = normalize(query).split(/\s+/).filter(Boolean);
    if (!words.length) return S.songs;
    return S.songs.filter((s) => { const hay = normalize(s.title + " " + s.firstLine); return words.every((w) => hay.includes(w)); });
  }

  // Crea un elemento con texto seguro (nunca innerHTML con datos del servidor).
  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text !== undefined && text !== null) e.textContent = text;
    return e;
  }

  return { S, on, connect, send, sections, currentSection, jumpTarget, uniqueLabels, searchSongs, el };
})();
