(function () {
  'use strict';

  // ---------------------------------------------------------------- data
  const KEY = 'fitstreak.v1';

  const DEFAULT_PLAN = [
    { id: 'pushups',   name: 'Push-ups',          type: 'reps', target: 40, sides: 1, icon: '💪',
      tip: 'Body in a straight line, chest close to the floor. Drop to your knees if you need to — just keep going.' },
    { id: 'squats',    name: 'Squats',            type: 'reps', target: 40, sides: 1, icon: '🦵',
      tip: 'Feet shoulder-width apart, push hips back, thighs parallel to the floor, chest up.' },
    { id: 'lunges',    name: 'Lunges',            type: 'reps', target: 20, sides: 2, icon: '🚶',
      tip: 'Step forward, lower until both knees are at 90°. Front knee stays over the ankle.' },
    { id: 'jacks',     name: 'Jumping Jacks',     type: 'reps', target: 50, sides: 1, icon: '⭐',
      tip: 'Jump feet out while raising arms overhead, then back. Stay light on your toes.' },
    { id: 'climbers',  name: 'Mountain Climbers', type: 'reps', target: 50, sides: 1, icon: '⛰️',
      tip: 'High plank position, drive knees to chest one after another. Fast but controlled.' },
    { id: 'plank',     name: 'Plank',             type: 'time', target: 60, sides: 1, icon: '🧱',
      tip: 'Elbows under shoulders, squeeze glutes and core. Don\'t let your hips sag.' },
    { id: 'sideplank', name: 'Side Plank',        type: 'time', target: 60, sides: 2, icon: '📐',
      tip: 'Elbow under shoulder, body in one straight line, hips lifted high.' }
  ];

  const QUOTES = [
    'Your phone can wait. Your health can\'t.',
    'Today\'s sweat is tomorrow\'s strength.',
    'Motivation comes and goes. Discipline keeps you going.',
    'Don\'t break the chain. Not even for one day.',
    'Not tomorrow. Today. Right now.',
    'Small start, big change.',
    '15 minutes for you. The rest of the day for the world.',
    'The hardest part is starting. Just do the first push-up.',
    'Be one step ahead of yesterday\'s you.',
    'Earn your screen time. 💪',
    'You don\'t need to be extreme. Just consistent.',
    'The body follows where the mind leads.'
  ];

  const BADGES = [
    [1,   '🌱', 'First Step'],
    [3,   '🔥', 'Warming Up'],
    [7,   '⚔️', 'Week Warrior'],
    [14,  '💎', 'Two Weeks Strong'],
    [21,  '🧠', 'Habit Formed'],
    [30,  '🏆', 'Monthly Master'],
    [50,  '🚀', 'Unstoppable'],
    [100, '👑', 'Century Club'],
    [365, '🦁', 'Legend']
  ];

  const CHEERS = [
    'Today\'s work is done! Now you\'ve earned your screen time 📱',
    'Awesome! Your future self says thank you.',
    'Another link in the chain. Keep it going! 🔗',
    'Discipline beats motivation. You won today too! 🏅'
  ];

  // ---------------------------------------------------------------- helpers
  const $ = (s, root) => (root || document).querySelector(s);
  const pad = n => String(n).padStart(2, '0');
  const dstr = d => d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  const today = () => dstr(new Date());
  const parse = s => new Date(s + 'T12:00:00');
  const addDays = (s, n) => { const d = parse(s); d.setDate(d.getDate() + n); return dstr(d); };
  const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fmtNum = n => Number(n).toLocaleString('en-IN');
  const native = window.Android || null;
  function callNative(fn) {
    const args = Array.prototype.slice.call(arguments, 1);
    try { if (native && typeof native[fn] === 'function') native[fn].apply(native, args); } catch (e) { /* ignore */ }
  }

  // ---------------------------------------------------------------- state
  function fresh() {
    return {
      plan: DEFAULT_PLAN.map(e => Object.assign({}, e)),
      history: {},
      settings: { remOn: true, remTime: '06:30', eveningOn: true, rest: 15, sound: true, theme: 'light' }
    };
  }

  function load() {
    try {
      const s = JSON.parse(localStorage.getItem(KEY));
      if (s && Array.isArray(s.plan)) {
        s.settings = Object.assign(fresh().settings, s.settings || {});
        s.history = s.history || {};
        s.plan.forEach(e => { delete e.ml; });   // older versions stored Malayalam names
        return s;
      }
    } catch (e) { /* fall through */ }
    return fresh();
  }

  let S = load();
  let tab = 'today';
  let calMonth = new Date(); calMonth.setDate(1);

  function save() {
    localStorage.setItem(KEY, JSON.stringify(S));
    callNative('sync', lastCompleted() || '', currentStreak());
  }

  function pushReminders() {
    const [h, m] = S.settings.remTime.split(':').map(Number);
    callNative('setReminders', !!S.settings.remOn, h, m, !!S.settings.eveningOn);
  }

  const rec = ds => S.history[ds];
  const isComplete = ds => !!(rec(ds) && rec(ds).complete);
  const doneCount = ds => { const r = rec(ds); return r ? S.plan.filter(e => r.done[e.id]).length : 0; };
  const isDone = (ds, id) => !!(rec(ds) && rec(ds).done[id]);

  function markExercise(id) {
    const t = today();
    const r = S.history[t] || (S.history[t] = { done: {}, complete: false });
    const e = S.plan.find(x => x.id === id);
    r.done[id] = e.target * e.sides;
    const was = r.complete;
    r.complete = S.plan.every(x => r.done[x.id]);
    if (r.complete && !was) r.at = Date.now();
    save();
    return r.complete && !was;      // true = this finished the day
  }

  function unmarkExercise(id) {
    const r = rec(today());
    if (!r) return;
    delete r.done[id];
    r.complete = false;
    delete r.at;
    if (!Object.keys(r.done).length) delete S.history[today()];
    save();
  }

  function currentStreak() {
    let d = today();
    if (!isComplete(d)) d = addDays(d, -1);   // today not done yet: the streak is still alive
    let n = 0;
    while (isComplete(d)) { n++; d = addDays(d, -1); }
    return n;
  }

  function bestStreak() {
    const days = Object.keys(S.history).filter(isComplete).sort();
    let best = 0, run = 0, prev = null;
    for (const k of days) {
      run = (prev && addDays(prev, 1) === k) ? run + 1 : 1;
      if (run > best) best = run;
      prev = k;
    }
    return best;
  }

  function lastCompleted() {
    const days = Object.keys(S.history).filter(isComplete).sort();
    return days[days.length - 1];
  }

  const totalDays = () => Object.keys(S.history).filter(isComplete).length;

  function totals() {
    const t = {};
    for (const k in S.history) {
      const r = S.history[k];
      for (const id in r.done) t[id] = (t[id] || 0) + (Number(r.done[id]) || 0);
    }
    return t;
  }

  function targetText(e) {
    const unit = e.type === 'time' ? (e.target >= 60 && e.target % 60 === 0 ? (e.target / 60) + ' min' : e.target + ' sec') : e.target + ' reps';
    return e.sides === 2 ? unit + ' · each side' : unit;
  }

  function quoteOfDay() {
    const n = Math.floor(parse(today()).getTime() / 86400000);
    return QUOTES[n % QUOTES.length];
  }

  function nextBadge() {
    const best = Math.max(bestStreak(), currentStreak());
    return BADGES.find(b => b[0] > best);
  }

  // ---------------------------------------------------------------- sound / buzz
  let actx = null;
  function beep(freq, ms) {
    if (!S.settings.sound) return;
    try {
      actx = actx || new (window.AudioContext || window.webkitAudioContext)();
      const o = actx.createOscillator(), g = actx.createGain();
      o.frequency.value = freq || 880; o.type = 'sine';
      g.gain.setValueAtTime(0.25, actx.currentTime);
      g.gain.exponentialRampToValueAtTime(0.001, actx.currentTime + (ms || 150) / 1000);
      o.connect(g); g.connect(actx.destination);
      o.start(); o.stop(actx.currentTime + (ms || 150) / 1000);
    } catch (e) { /* no audio */ }
  }
  function buzz(p) { try { navigator.vibrate && navigator.vibrate(p); } catch (e) { /* ignore */ } }

  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg;
    t.classList.remove('hidden');
    clearTimeout(toast._t);
    toast._t = setTimeout(() => t.classList.add('hidden'), 2200);
  }

  // ---------------------------------------------------------------- icons
  const ICON = {
    moon: 'M12 3a9 9 0 1 0 9 9c0-.46-.04-.92-.1-1.36a5.389 5.389 0 0 1-4.4 2.26 5.403 5.403 0 0 1-3.14-9.8c-.44-.06-.9-.1-1.36-.1z',
    sun: 'M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10zM11 1h2v3h-2zm0 19h2v3h-2zM3.5 4.9l1.4-1.4 2.1 2.1-1.4 1.4zm12.5 12.5 1.4-1.4 2.1 2.1-1.4 1.4zM1 11h3v2H1zm19 0h3v2h-3zM3.5 19.1l2.1-2.1 1.4 1.4-2.1 2.1zM16 6l2.1-2.1 1.4 1.4-2.1 2.1z',
    check: 'M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
    play: 'M8 5v14l11-7z'
  };
  const svg = (name, size) => '<svg width="' + (size || 24) + '" height="' + (size || 24) + '" viewBox="0 0 24 24" fill="currentColor"><path d="' + ICON[name] + '"/></svg>';

  // soft tinted background per exercise, like category chips
  const TINT = { pushups: 'green', squats: 'blue', lunges: 'orange', jacks: 'yellow', climbers: 'teal', plank: 'purple', sideplank: 'pink' };

  // ---------------------------------------------------------------- how-to guides
  const HOWTO = {
    pushups: {
      steps: [
        'Hands on the floor slightly wider than your shoulders, fingers pointing forward.',
        'Body in one straight line from head to heels — squeeze your glutes and tighten your stomach.',
        'Bend your elbows (about 45° from your body) and lower until your chest is a fist above the floor.',
        'Push the floor away and straighten your arms fully.'
      ],
      mistake: 'Hips sagging or butt sticking up, and elbows flaring straight out to the sides.',
      easier: 'Too hard? Do them with your knees on the floor.'
    },
    squats: {
      steps: [
        'Feet shoulder-width apart, toes turned slightly out.',
        'Push your hips back and down, as if sitting on a chair. Arms forward for balance.',
        'Go down until your thighs are parallel to the floor. Keep your heels down and chest up.',
        'Drive through your heels to stand up tall.'
      ],
      mistake: 'Knees caving inward, heels lifting, or rounding your back.',
      easier: 'Too hard? Squat down to a chair and stand back up.'
    },
    lunges: {
      steps: [
        'Stand tall, hands on your hips.',
        'Take a big step forward with one leg.',
        'Lower your hips until both knees are bent at 90°; the back knee hovers just above the floor.',
        'Push through the front heel to step back. Do all reps on one leg, then switch.'
      ],
      mistake: 'Front knee pushing far past the toes, or leaning your upper body forward.',
      easier: 'Too hard? Hold a wall or chair for balance and don\'t go as low.'
    },
    jacks: {
      steps: [
        'Stand with feet together and arms at your sides.',
        'Jump your feet out wider than your shoulders while swinging your arms overhead.',
        'Jump back to the start position.',
        'Land softly on the balls of your feet and keep a steady rhythm.'
      ],
      mistake: 'Landing hard on your heels or locking your knees.',
      easier: 'Low impact: step one foot out at a time instead of jumping.'
    },
    climbers: {
      steps: [
        'Start in a high plank: hands under your shoulders, body straight.',
        'Drive one knee towards your chest.',
        'Switch legs quickly, like running in place.',
        'Keep your hips low and level the whole time. Each knee drive = 1 rep.'
      ],
      mistake: 'Hips bouncing up high, or hands drifting in front of the shoulders.',
      easier: 'Too hard? Go slower and step each foot in instead of jumping.'
    },
    plank: {
      steps: [
        'Forearms on the floor, elbows directly under your shoulders.',
        'Step your feet back so your body forms a straight line from head to heels.',
        'Squeeze your glutes, pull your belly button in, and look at the floor.',
        'Breathe slowly and hold.'
      ],
      mistake: 'Hips sagging toward the floor or pushed up high, or holding your breath.',
      easier: 'Too hard? Rest your knees on the floor and keep the line from knees to head.'
    },
    sideplank: {
      steps: [
        'Lie on your side, elbow directly under your shoulder, forearm on the floor.',
        'Stack your feet (or put one in front of the other).',
        'Lift your hips until your body is a straight line from head to feet.',
        'Top hand on your hip or reaching up. Hold, then switch sides.'
      ],
      mistake: 'Hips dropping toward the floor or the shoulder shrugging up to the ear.',
      easier: 'Too hard? Keep your bottom knee on the floor.'
    }
  };

  // ---------------------------------------------------------------- exercise animations
  // A simple figure drawn from joint positions; SVG animates between the key poses.
  // Joints: h=head, n=neck/shoulder, p=hip, eL/hL eR/hR = elbows/hands, kL/fL kR/fR = knees/feet.
  // "L" limbs are the far side (drawn faded) unless front view.
  const POSES = {
    pushups: { dur: 2.4, frames: [
      { h: [154, 60], n: [142, 65], p: [90, 85], eL: [141, 86], hL: [140, 108], eR: [141, 86], hR: [140, 108], kL: [60, 97], fL: [30, 108], kR: [60, 97], fR: [30, 108] },
      { h: [152, 89], n: [140, 92], p: [86, 100], eL: [124, 99], hL: [140, 108], eR: [124, 99], hR: [140, 108], kL: [58, 104], fL: [30, 108], kR: [58, 104], fR: [30, 108] }
    ] },
    squats: { dur: 2.4, frames: [
      { h: [100, 12], n: [100, 26], p: [100, 56], eL: [106, 40], hL: [112, 53], eR: [106, 40], hR: [112, 53], kL: [99, 82], fL: [100, 108], kR: [99, 82], fR: [100, 108] },
      { h: [109, 42], n: [102, 54], p: [86, 82], eL: [120, 56], hL: [140, 54], eR: [120, 56], hR: [140, 54], kL: [114, 86], fL: [100, 108], kR: [114, 86], fR: [100, 108] }
    ] },
    lunges: { dur: 2.6, frames: [
      { h: [100, 10], n: [100, 24], p: [100, 54], eL: [88, 38], hL: [97, 52], eR: [88, 38], hR: [97, 52], kL: [97, 81], fL: [93, 108], kR: [103, 81], fR: [105, 108] },
      { h: [100, 34], n: [100, 48], p: [100, 78], eL: [88, 62], hL: [97, 76], eR: [88, 62], hR: [97, 76], kL: [90, 104], fL: [66, 107], kR: [127, 80], fR: [128, 108] }
    ] },
    jacks: { dur: 1.1, front: true, frames: [
      { h: [100, 14], n: [100, 28], p: [100, 64], eL: [93, 46], hL: [91, 64], eR: [107, 46], hR: [109, 64], kL: [98, 86], fL: [96, 108], kR: [102, 86], fR: [104, 108] },
      { h: [100, 12], n: [100, 26], p: [100, 62], eL: [85, 14], hL: [77, 3], eR: [115, 14], hR: [123, 3], kL: [89, 84], fL: [78, 106], kR: [111, 84], fR: [122, 106] }
    ] },
    climbers: { dur: 1.0, frames: [
      { h: [154, 60], n: [142, 65], p: [94, 80], eL: [141, 86], hL: [140, 108], eR: [141, 86], hR: [140, 108], kL: [120, 90], fL: [102, 104], kR: [64, 94], fR: [34, 108] },
      { h: [154, 60], n: [142, 65], p: [94, 80], eL: [141, 86], hL: [140, 108], eR: [141, 86], hR: [140, 108], kL: [64, 94], fL: [34, 108], kR: [120, 90], fR: [102, 104] }
    ] },
    plank: { dur: 3, frames: [
      { h: [150, 80], n: [138, 85], p: [86, 95], eL: [137, 108], hL: [160, 108], eR: [137, 108], hR: [160, 108], kL: [58, 101], fL: [30, 108], kR: [58, 101], fR: [30, 108] },
      { h: [150, 79], n: [138, 84], p: [86, 94], eL: [137, 108], hL: [160, 108], eR: [137, 108], hR: [160, 108], kL: [58, 101], fL: [30, 108], kR: [58, 101], fR: [30, 108] }
    ] },
    sideplank: { dur: 3, front: true, frames: [
      { h: [145, 63], n: [134, 72], p: [86, 90], eL: [136, 50], hL: [138, 28], eR: [134, 106], hR: [156, 107], kL: [60, 99], fL: [34, 108], kR: [60, 99], fR: [34, 108] },
      { h: [145, 62], n: [134, 71], p: [86, 88], eL: [137, 49], hL: [140, 27], eR: [134, 106], hR: [156, 107], kL: [60, 99], fL: [34, 108], kR: [60, 99], fR: [34, 108] }
    ] }
  };

  const LIMBS = [['armL', ['n', 'eL', 'hL']], ['legL', ['p', 'kL', 'fL']], ['torso', ['n', 'p']], ['legR', ['p', 'kR', 'fR']], ['armR', ['n', 'eR', 'hR']]];

  function figure(id, animate) {
    const def = POSES[id];
    if (!def) return '';
    const fr = def.frames;
    const pts = (f, keys) => keys.map(k => f[k].join(',')).join(' ');
    const anim = (attr, a, b) => animate
      ? '<animate attributeName="' + attr + '" values="' + a + ';' + b + ';' + a + '" dur="' + def.dur + 's" repeatCount="indefinite" calcMode="spline" keyTimes="0;0.5;1" keySplines="0.45 0 0.55 1;0.45 0 0.55 1"/>'
      : '';
    let g = '<line class="fig-floor" x1="4" y1="113" x2="196" y2="113"/>';
    for (const [name, keys] of LIMBS) {
      const far = !def.front && name.endsWith('L');
      g += '<polyline class="fig-limb' + (far ? ' far' : '') + '" points="' + pts(fr[0], keys) + '">' +
        anim('points', pts(fr[0], keys), pts(fr[1], keys)) + '</polyline>';
    }
    g += '<circle class="fig-head" cx="' + fr[0].h[0] + '" cy="' + fr[0].h[1] + '" r="9">' +
      anim('cx', fr[0].h[0], fr[1].h[0]) + anim('cy', fr[0].h[1], fr[1].h[1]) + '</circle>';
    return '<svg class="fig" viewBox="0 0 200 118" aria-hidden="true">' + g + '</svg>';
  }

  function howtoHtml(id) {
    const h = HOWTO[id];
    if (!h) return '';
    return '<div class="howto"><h4>How to do it</h4><ol>' + h.steps.map(x => '<li>' + esc(x) + '</li>').join('') + '</ol>' +
      '<div class="mistake"><b>⚠️ Avoid:</b> ' + esc(h.mistake) + '</div>' +
      '<div class="easier"><b>💡</b> ' + esc(h.easier) + '</div></div>';
  }

  // ---------------------------------------------------------------- exercise editor
  const ICON_CHOICES = ['💪', '🦵', '🚶', '⭐', '⛰️', '🧱', '📐', '🏃', '🚴', '🧘', '🤸', '🏋️', '🥊', '🦶', '🙌', '🔥', '⚡', '❤️', '🎯', '⏱️'];
  let ED = null;

  function openEditor(id) {
    const e = id ? S.plan.find(x => x.id === id) : null;
    ED = e ? { id: e.id, icon: e.icon, type: e.type } : { id: null, icon: '🏃', type: 'reps' };
    const builtIn = e && HOWTO[e.id];
    const d = $('#dialog');
    d.innerHTML = '<div class="dlg editor"><h3>' + (e ? 'Edit exercise' : 'Add exercise') + '</h3>' +
      '<label class="fld">Name<input id="edName" type="text" maxlength="30" placeholder="e.g. Burpees" value="' + (e ? esc(e.name) : '') + '"></label>' +
      '<div class="fld">Icon<div class="icon-pick">' + ICON_CHOICES.map(ic =>
        '<button data-act="ed-icon" data-icon="' + ic + '" class="' + (ic === ED.icon ? 'on' : '') + '">' + ic + '</button>').join('') + '</div></div>' +
      '<div class="fld">Type<div class="seg">' +
      '<button data-act="ed-type" data-type="reps" class="' + (ED.type === 'reps' ? 'on' : '') + '">Reps</button>' +
      '<button data-act="ed-type" data-type="time" class="' + (ED.type === 'time' ? 'on' : '') + '">Time</button></div></div>' +
      '<div class="fld-row"><label class="fld">Target<span class="tgt-in"><input id="edTarget" type="number" min="1" max="999" inputmode="numeric" value="' + (e ? e.target : 20) + '">' +
      '<span id="edUnit">' + (ED.type === 'time' ? 'seconds' : 'reps') + '</span></span></label>' +
      '<div class="fld side-sw">Each side<label class="switch"><input id="edSides" type="checkbox"' + (e && e.sides === 2 ? ' checked' : '') + '><span></span></label></div></div>' +
      (builtIn ? '<p class="muted small-note">✓ Includes animated demo and form guide</p>'
        : '<label class="fld">How to do it (optional)<textarea id="edTip" maxlength="300" rows="3" placeholder="Notes on correct form">' + (e && e.tip ? esc(e.tip) : '') + '</textarea></label>') +
      '<div class="dlg-btns"><button class="tbtn" data-act="dlg-no">Cancel</button><button class="tbtn primary" data-act="ed-save">Save</button></div></div>';
    d.classList.remove('hidden');
    dialogResolve = null;
    if (!e) setTimeout(() => $('#edName').focus(), 50);
  }

  function saveEditor() {
    const name = $('#edName').value.trim();
    const target = Math.round(Number($('#edTarget').value));
    if (!name) { toast('Please enter a name'); $('#edName').focus(); return; }
    if (!(target >= 1 && target <= 999)) { toast('Target must be between 1 and 999'); $('#edTarget').focus(); return; }
    const sides = $('#edSides').checked ? 2 : 1;
    const tipEl = $('#edTip');
    let e = ED.id ? S.plan.find(x => x.id === ED.id) : null;
    if (!e) {
      e = { id: 'c' + Date.now().toString(36), tip: '' };
      S.plan.push(e);
    }
    Object.assign(e, { name: name, icon: ED.icon, type: ED.type, target: target, sides: sides });
    if (tipEl) e.tip = tipEl.value.trim();
    const isNew = !ED.id;
    ED = null;
    closeDialog(false);
    planChanged();
    toast(isNew ? name + ' added 💪' : name + ' updated');
  }

  // Today's progress must follow the current exercise list.
  function planChanged() {
    const r = rec(today());
    if (r) {
      const was = r.complete;
      r.complete = S.plan.every(x => r.done[x.id]);
      if (r.complete && !was) r.at = Date.now();
      if (!r.complete) delete r.at;
    }
    save(); render();
  }

  // ---------------------------------------------------------------- dialog
  // In-app replacement for confirm(): Android WebView does not show browser dialogs.
  let dialogResolve = null;
  function ask(title, msg, okLabel, danger) {
    const d = $('#dialog');
    d.innerHTML = '<div class="dlg"><h3>' + esc(title) + '</h3><p>' + esc(msg) + '</p><div class="dlg-btns">' +
      '<button class="tbtn" data-act="dlg-no">Cancel</button>' +
      '<button class="tbtn ' + (danger ? 'danger' : 'primary') + '" data-act="dlg-yes">' + esc(okLabel) + '</button></div></div>';
    d.classList.remove('hidden');
    return new Promise(res => { dialogResolve = res; });
  }
  function closeDialog(answer) {
    $('#dialog').classList.add('hidden');
    const r = dialogResolve; dialogResolve = null;
    if (r) r(answer);
  }

  // ---------------------------------------------------------------- theme
  function applyTheme() {
    const dark = S.settings.theme === 'dark';
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    $('meta[name=theme-color]').setAttribute('content', dark ? '#141218' : '#fef7ff');
    callNative('setDarkTheme', dark);
  }

  function greeting() {
    const h = new Date().getHours();
    return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
  }

  function header(title, sub) {
    const dark = S.settings.theme === 'dark';
    return '<div class="top"><div><div class="sub">' + esc(sub) + '</div><div class="title">' + esc(title) + '</div></div>' +
      '<button class="round-btn" data-act="theme" aria-label="theme">' + svg(dark ? 'sun' : 'moon') + '</button></div>';
  }

  // ---------------------------------------------------------------- views
  function render() {
    document.querySelectorAll('.tabs button').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
    const v = $('#view');
    if (tab === 'today') v.innerHTML = viewToday();
    else if (tab === 'progress') v.innerHTML = viewProgress();
    else v.innerHTML = viewSettings();
    window.scrollTo(0, 0);
  }

  function ringSvg(size, r, frac, cls) {
    const c = 2 * Math.PI * r;
    return '<svg width="' + size + '" height="' + size + '" viewBox="0 0 ' + size + ' ' + size + '">' +
      '<circle class="bgc" cx="' + size / 2 + '" cy="' + size / 2 + '" r="' + r + '"/>' +
      '<circle class="fg ' + (cls || '') + '" cx="' + size / 2 + '" cy="' + size / 2 + '" r="' + r + '" ' +
      'stroke-dasharray="' + c.toFixed(2) + '" stroke-dashoffset="' + (c * (1 - frac)).toFixed(2) + '"/></svg>';
  }

  function viewToday() {
    const t = today();
    const streak = currentStreak();
    const done = doneCount(t), total = S.plan.length, complete = isComplete(t);
    const q = quoteOfDay();
    const nb = nextBadge();
    const best = Math.max(bestStreak(), streak);

    let status;
    if (complete) status = 'All done! Screen time earned 📱';
    else if (done === 0) status = streak > 0 ? 'Keep your ' + streak + '-day streak alive 🔥' : 'Let\'s start your streak today!';
    else status = (total - done) + ' more to go. Don\'t stop now!';

    const monthTxt = new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' });
    let html = header('Today\'s Workout', greeting()) +
      '<div class="hero"><div class="hero-lbl">Current streak</div>' +
      '<div class="hero-num">' + streak + ' <span>day' + (streak === 1 ? '' : 's') + ' 🔥</span></div>' +
      '<div class="hero-date">' + esc(monthTxt) + '</div>' +
      '<div class="hero-bar"><div style="width:' + Math.round(done / total * 100) + '%"></div></div>' +
      '<div class="hero-tiles">' +
      '<div class="tile"><div class="tile-ico">' + svg('check', 20) + '</div><div><small>Done today</small><b>' + done + ' / ' + total + '</b></div></div>' +
      '<div class="tile"><div class="tile-ico">🏆</div><div><small>Best streak</small><b>' + best + ' day' + (best === 1 ? '' : 's') + '</b></div></div>' +
      '</div></div>';

    if (complete) {
      html += '<div class="done-banner">✅ Today\'s workout is complete! Screen time earned 📱</div>';
    } else {
      html += '<button class="btn primary" data-act="start">' + svg('play', 22) + (done ? 'Continue workout' : 'Start workout') + '</button>' +
        '<p class="status">' + status + '</p>';
    }

    html += '<div class="card quote"><div class="q-ml">“' + esc(q) + '”</div></div>';

    html += '<div class="sec-head"><h2>Exercises</h2><span class="muted">' + done + ' of ' + total + ' done</span></div>';
    for (const e of S.plan) {
      const d = isDone(t, e.id);
      html += '<div class="ex ' + (d ? 'done' : '') + '" data-act="open" data-id="' + e.id + '">' +
        '<div class="ico fig-chip tint-' + (TINT[e.id] || 'purple') + '">' + (POSES[e.id] ? figure(e.id, false) : e.icon) + '</div>' +
        '<div class="info"><div class="name">' + esc(e.name) + '</div>' +
        '<div class="tgt">' + targetText(e) + '</div></div>' +
        '<button class="chk" data-act="toggle" data-id="' + e.id + '" aria-label="mark done">' + svg('check', 22) + '</button></div>';
    }

    if (nb) {
      const pct = Math.min(100, Math.round(best / nb[0] * 100));
      const left = nb[0] - streak;
      html += '<div class="sec-head"><h2>Next badge</h2></div><div class="card"><div style="display:flex;align-items:center;gap:14px">' +
        '<div class="ico tint-yellow">' + nb[1] + '</div><div style="flex:1"><b>' + esc(nb[2]) + '</b>' +
        '<div class="muted" style="font-size:.85rem">' + nb[0] + '-day streak · ' +
        (left > 0 ? left + ' more day' + (left > 1 ? 's' : '') + ' in a row' : 'almost there!') + '</div></div></div>' +
        '<div class="bar"><div style="width:' + pct + '%"></div></div></div>';
    }
    return html;
  }

  function viewProgress() {
    const streak = currentStreak(), best = Math.max(bestStreak(), streak);
    const now = new Date();
    const monthPrefix = now.getFullYear() + '-' + pad(now.getMonth() + 1);
    const thisMonth = Object.keys(S.history).filter(k => k.startsWith(monthPrefix) && isComplete(k)).length;

    let html = header('Your Progress', 'Keep the chain going') +
      '<div class="stats">' +
      '<div class="stat"><div class="ico tint-orange">🔥</div><b>' + streak + '</b><span>Current streak</span></div>' +
      '<div class="stat"><div class="ico tint-yellow">🏆</div><b>' + best + '</b><span>Best streak</span></div>' +
      '<div class="stat"><div class="ico tint-green">✅</div><b>' + totalDays() + '</b><span>Total workouts</span></div>' +
      '<div class="stat"><div class="ico tint-blue">📅</div><b>' + thisMonth + '</b><span>This month</span></div>' +
      '</div>';

    // calendar
    const y = calMonth.getFullYear(), m = calMonth.getMonth();
    const first = new Date(y, m, 1), days = new Date(y, m + 1, 0).getDate();
    const startDow = (first.getDay() + 6) % 7; // Monday first
    const t = today();
    const allDays = Object.keys(S.history).sort();
    const firstDay = allDays[0];
    html += '<h2>Calendar</h2><div class="card"><div class="cal-head">' +
      '<button data-act="cal" data-d="-1">‹</button><b>' + first.toLocaleDateString('en-IN', { month: 'long', year: 'numeric' }) +
      '</b><button data-act="cal" data-d="1">›</button></div><div class="cal">';
    ['M', 'T', 'W', 'T', 'F', 'S', 'S'].forEach(d => { html += '<div class="dow">' + d + '</div>'; });
    for (let i = 0; i < startDow; i++) html += '<div class="d empty"></div>';
    for (let d = 1; d <= days; d++) {
      const ds = y + '-' + pad(m + 1) + '-' + pad(d);
      let cls = 'd';
      if (ds > t) cls += ' future';
      else if (isComplete(ds)) cls += ' full';
      else if (doneCount(ds) > 0) cls += ' part';
      else if (firstDay && ds >= firstDay && ds < t) cls += ' miss';
      if (ds === t) cls += ' today';
      html += '<div class="' + cls + '">' + d + '</div>';
    }
    html += '</div><div class="legend"><span><i style="background:var(--green)"></i>Done</span>' +
      '<span><i style="background:var(--amber)"></i>Partial</span>' +
      '<span><i style="background:var(--red-c)"></i>Missed</span></div></div>';

    // lifetime totals
    const tot = totals();
    html += '<h2>Lifetime totals</h2><div class="card totals">';
    for (const e of S.plan) {
      const v = tot[e.id] || 0;
      const txt = e.type === 'time' ? (v >= 60 ? (v / 60).toFixed(v % 60 ? 1 : 0) + ' min' : v + ' sec') : fmtNum(v) + ' reps';
      html += '<div class="row"><span>' + e.icon + ' ' + esc(e.name) + '</span><b>' + txt + '</b></div>';
    }
    html += '</div>';

    // badges
    html += '<h2>Badges</h2><div class="badges">';
    for (const b of BADGES) {
      const got = best >= b[0];
      html += '<div class="badge ' + (got ? '' : 'locked') + '"><div class="e">' + b[1] + '</div><div class="t">' + esc(b[2]) +
        '</div><div class="s">' + (got ? 'Unlocked ✓' : '🔒 ' + b[0] + ' days') + '</div></div>';
    }
    html += '</div>';
    return html;
  }

  function viewSettings() {
    const st = S.settings;
    const app = !!native;
    let html = header('Settings', 'Make it yours');

    html += '<h2>Reminders</h2><div class="card">' +
      '<div class="set-row"><label>⏰ Morning reminder</label>' + sw('remOn', st.remOn) + '</div>' +
      '<div class="set-row"><label>Reminder time</label><input type="time" data-set="remTime" value="' + st.remTime + '"></div>' +
      '<div class="set-row"><label>⚠️ 9 PM alert if not done<br><span class="ml">Streak danger warning</span></label>' + sw('eveningOn', st.eveningOn) + '</div>' +
      (app ? '' : '<p class="muted" style="font-size:.8rem;margin:8px 0 0">Reminders work in the Android app.</p>') +
      '</div>';

    html += '<div class="sec-head"><h2>My workout</h2><span class="muted">' + S.plan.length + ' exercises</span></div><div class="card">';
    S.plan.forEach((e, i) => {
      html += '<div class="plan-row">' +
        '<div class="order"><button data-act="move" data-id="' + e.id + '" data-d="-1"' + (i === 0 ? ' disabled' : '') + ' aria-label="move up">▲</button>' +
        '<button data-act="move" data-id="' + e.id + '" data-d="1"' + (i === S.plan.length - 1 ? ' disabled' : '') + ' aria-label="move down">▼</button></div>' +
        '<div class="plan-ico">' + esc(e.icon) + '</div>' +
        '<div class="plan-info" data-act="edit" data-id="' + e.id + '"><div>' + esc(e.name) + '</div><span class="ml">' + targetText(e) + '</span></div>' +
        '<button class="icon-btn" data-act="edit" data-id="' + e.id + '" aria-label="edit">✏️</button>' +
        '<button class="icon-btn" data-act="del" data-id="' + e.id + '" aria-label="delete">🗑️</button></div>';
    });
    html += '<button class="btn primary" data-act="add">＋ Add exercise</button>' +
      '<div class="btn-row" style="margin-top:12px"><button class="btn small ghost" data-act="levelup">⬆️ Level up (+10%)</button>' +
      '<button class="btn small ghost" data-act="defaults">↺ Reset to default workout</button></div></div>';

    html += '<h2>Workout</h2><div class="card">' +
      '<div class="set-row"><label>Rest between exercises</label><select data-set="rest">' +
      [0, 10, 15, 20, 30, 45, 60].map(s => '<option value="' + s + '"' + (st.rest == s ? ' selected' : '') + '>' + (s ? s + ' sec' : 'No rest') + '</option>').join('') +
      '</select></div>' +
      '<div class="set-row"><label>🔊 Sounds</label>' + sw('sound', st.sound) + '</div>' +
      '<div class="set-row"><label>🌙 Dark mode</label><label class="switch"><input type="checkbox" data-act="theme-sw"' + (st.theme === 'dark' ? ' checked' : '') + '><span></span></label></div></div>';

    html += '<h2>Backup</h2><div class="card"><p class="muted" style="font-size:.85rem;margin-top:0">' +
      'Your history is stored only on this phone. Copy this backup somewhere safe (WhatsApp to yourself, notes…) before reinstalling.</p>' +
      '<textarea class="backup" id="backupBox" placeholder="Paste a backup here to restore"></textarea>' +
      '<div class="btn-row"><button class="btn small ghost" data-act="export">📤 Show backup</button>' +
      '<button class="btn small ghost" data-act="copy">📋 Copy</button>' +
      '<button class="btn small ghost" data-act="import">📥 Restore</button></div></div>';

    html += '<h2>Danger zone</h2><button class="btn danger" data-act="reset">Delete all data</button>' +
      '<p class="muted" style="text-align:center;font-size:.75rem;margin-top:24px">FitStreak · Earn your screen time 💪</p>';
    return html;
  }

  function sw(key, on) {
    return '<label class="switch"><input type="checkbox" data-set="' + key + '"' + (on ? ' checked' : '') + '><span></span></label>';
  }

  // ---------------------------------------------------------------- workout player
  let P = null;

  function buildSteps(ids) {
    const steps = [];
    for (const id of ids) {
      const e = S.plan.find(x => x.id === id);
      if (e.sides === 2) { steps.push({ id: id, side: 'Left side' }); steps.push({ id: id, side: 'Right side' }); }
      else steps.push({ id: id, side: null });
    }
    return steps;
  }

  function openPlayer(ids, guided) {
    if (!ids.length) return;
    P = { steps: buildSteps(ids), i: 0, guided: guided, phase: 'work', count: 0, remaining: 0, timer: null, paused: true };
    callNative('keepScreenOn', true);
    $('#player').classList.remove('hidden');
    startStep();
  }

  function closePlayer() {
    stopTimer();
    P = null;
    callNative('keepScreenOn', false);
    $('#player').classList.add('hidden');
    render();
  }

  function stopTimer() { if (P && P.timer) { clearInterval(P.timer); P.timer = null; } }

  function countdown(sec, onTick, onDone) {
    stopTimer();
    P.remaining = sec;
    P.endAt = Date.now() + sec * 1000;
    P.timer = setInterval(() => {
      const left = Math.max(0, Math.ceil((P.endAt - Date.now()) / 1000));
      if (left !== P.remaining) { P.remaining = left; onTick(left); }
      if (left <= 0) { stopTimer(); onDone(); }
    }, 100);
  }

  function step() { return P.steps[P.i]; }
  function ex() { return S.plan.find(x => x.id === step().id); }

  function dotsHtml() {
    let h = '<div class="dots">';
    P.steps.forEach((s, i) => { h += '<i class="' + (i < P.i ? 'ok' : i === P.i ? 'on' : '') + '"></i>'; });
    return h + '</div>';
  }

  function startStep() {
    const e = ex();
    P.phase = 'work';
    P.count = 0;
    P.paused = true;
    P.remaining = e.target;
    drawWork();
  }

  function drawWork() {
    const e = ex(), s = step();
    const isTime = e.type === 'time';
    const frac = isTime ? (P.remaining / e.target) : Math.min(1, P.count / e.target);
    const html =
      '<div class="pl"><div class="pl-top"><button class="x" data-act="close">✕</button>' + dotsHtml() + '</div>' +
      '<div class="pl-body"><div class="demo tint-' + (TINT[e.id] || 'purple') + '">' + (POSES[e.id] ? figure(e.id, true) : '<div class="pl-ico">' + e.icon + '</div>') + '</div>' +
      '<div class="pl-name">' + esc(e.name) + '</div>' +
      (s.side ? '<div class="pl-side">' + s.side + '</div>' : '') +
      '<div class="big-ring ' + (isTime ? '' : 'tap') + (!isTime && P.count >= e.target ? ' hit' : '') + '" id="bigRing" ' + (isTime ? '' : 'data-act="count"') + '>' +
      ringSvg(230, 100, frac) +
      '<div class="num"><b id="pNum">' + (isTime ? P.remaining : P.count) + '</b><span>' +
      (isTime ? 'seconds' : 'of ' + e.target + ' · tap to count') + '</span></div></div>' +
      '</div>' +
      '<div class="pl-actions">' +
      (isTime
        ? '<button class="btn primary" data-act="timer" id="timerBtn">' + (P.timer ? '⏸ Pause' : (P.remaining < e.target ? '▶ Resume' : '▶ Start timer')) + '</button>' +
          '<button class="btn ghost" data-act="finish">Done ✓</button>'
        : '<button class="btn green" data-act="finish">Done ✓ ' + e.target + ' reps</button>') +
      '</div>' + (HOWTO[e.id] ? howtoHtml(e.id) : (e.tip ? '<div class="howto"><h4>How to do it</h4><p class="note">' + esc(e.tip) + '</p></div>' : '')) + '</div>';
    $('#player').innerHTML = html;
  }

  function updateRing(frac) {
    const fg = $('#bigRing .fg');
    if (!fg) return;
    const c = 2 * Math.PI * 100;
    fg.setAttribute('stroke-dashoffset', (c * (1 - frac)).toFixed(2));
  }

  function toggleTimer() {
    const e = ex();
    if (P.timer) {                                  // pause
      stopTimer();
      $('#timerBtn').textContent = '▶ Resume';
      return;
    }
    beep(660, 120);
    countdown(P.remaining, left => {
      $('#pNum').textContent = left;
      updateRing(left / e.target);
      if (left <= 3 && left > 0) beep(660, 100);
    }, () => {
      beep(990, 400); buzz([200, 100, 200]);
      finishStep();
    });
    $('#timerBtn').textContent = '⏸ Pause';
  }

  function countRep() {
    const e = ex();
    P.count++;
    $('#pNum').textContent = P.count;
    updateRing(Math.min(1, P.count / e.target));
    buzz(15);
    if (P.count === e.target) {
      $('#bigRing').classList.add('hit');
      beep(990, 300); buzz([100, 60, 100]);
      toast('Target hit! 🎯 Tap Done');
    }
  }

  function finishStep() {
    stopTimer();
    const s = step();
    const next = P.steps[P.i + 1];
    let finishedDay = false;
    if (!next || next.id !== s.id) finishedDay = markExercise(s.id);

    if (!next) {
      closePlayer();
      if (finishedDay) celebrate();
      else toast('Nice! 💪 ' + ex2name(s.id) + ' done');
      return;
    }
    P.i++;
    const switchingSides = next.id === s.id;
    const rest = switchingSides ? 5 : (P.guided ? Number(S.settings.rest) : 0);
    if (rest > 0) startRest(rest, switchingSides);
    else startStep();
  }

  function ex2name(id) { return S.plan.find(x => x.id === id).name; }

  function startRest(sec, switching) {
    P.phase = 'rest';
    const e = ex(), s = step();
    $('#player').innerHTML =
      '<div class="pl"><div class="pl-top"><button class="x" data-act="close">✕</button>' + dotsHtml() + '</div>' +
      '<div class="pl-body"><div class="rest-title">' + (switching ? '🔄 Switch sides' : '😮‍💨 Rest') + '</div>' +
      (POSES[e.id] ? '<div class="demo small tint-' + (TINT[e.id] || 'purple') + '">' + figure(e.id, true) + '</div>' : '') +
      '<div class="big-ring rest" id="bigRing">' + ringSvg(230, 100, 1) +
      '<div class="num"><b id="pNum">' + sec + '</b><span>seconds</span></div></div>' +
      '<div class="next-up">Next up: <b>' + e.icon + ' ' + esc(e.name) + (s.side ? ' · ' + s.side : '') + '</b> — ' + targetText(e) + '</div></div>' +
      '<div class="pl-actions"><div class="pair"><button class="btn ghost" data-act="more">+10 sec</button>' +
      '<button class="btn primary" data-act="skip">Skip rest ⏭</button></div></div></div>';
    let total = sec;
    const tick = left => {
      $('#pNum').textContent = left;
      updateRing(left / total);
      if (left <= 3 && left > 0) beep(520, 90);
    };
    const done = () => { beep(990, 250); buzz(150); startStep(); };
    countdown(sec, tick, done);
    P.addRest = () => { total = P.remaining + 10; countdown(P.remaining + 10, tick, done); tick(P.remaining); };
  }

  // ---------------------------------------------------------------- celebration
  function celebrate() {
    const streak = currentStreak();
    const badge = BADGES.find(b => b[0] === streak);
    const isNewBest = badge && bestStreak() === streak;
    const cheer = CHEERS[Math.floor(Math.random() * CHEERS.length)];
    const el = $('#celebrate');
    el.innerHTML = '<div class="pl cel"><div class="big">🎉</div><h1>Day ' + streak + ' complete!</h1>' +
      '<p>🔥 ' + streak + '-day streak</p><p>' + esc(cheer) + '</p>' +
      (isNewBest ? '<div class="newbadge"><div style="font-size:2.4rem">' + badge[1] + '</div><b>New badge: ' + esc(badge[2]) + '</b></div>' : '') +
      '<button class="btn primary" data-act="celok">Awesome! 💪</button></div>';
    el.classList.remove('hidden');
    beep(660, 120); setTimeout(() => beep(880, 120), 140); setTimeout(() => beep(1180, 300), 280);
    buzz([80, 60, 80, 60, 200]);
    const colors = ['#f97316', '#22c55e', '#38bdf8', '#facc15', '#ec4899', '#a78bfa'];
    for (let i = 0; i < 70; i++) {
      const c = document.createElement('div');
      c.className = 'confetti';
      c.style.left = Math.random() * 100 + 'vw';
      c.style.background = colors[i % colors.length];
      c.style.animationDuration = (2 + Math.random() * 2.5) + 's';
      c.style.animationDelay = Math.random() * 0.8 + 's';
      document.body.appendChild(c);
      setTimeout(() => c.remove(), 5500);
    }
  }

  // ---------------------------------------------------------------- events
  document.addEventListener('click', ev => {
    const t = ev.target.closest('[data-act], .tabs button');
    if (!t) return;
    if (t.dataset.tab) { tab = t.dataset.tab; render(); return; }
    const act = t.dataset.act, id = t.dataset.id;

    switch (act) {
      case 'start': {
        const todo = S.plan.filter(e => !isDone(today(), e.id)).map(e => e.id);
        openPlayer(todo, true);
        break;
      }
      case 'open':
        openPlayer([id], false);
        break;
      case 'toggle':
        ev.stopPropagation();
        if (isDone(today(), id)) { unmarkExercise(id); render(); }
        else { const fin = markExercise(id); buzz(30); render(); if (fin) celebrate(); }
        break;
      case 'close': closePlayer(); break;
      case 'count': countRep(); break;
      case 'timer': toggleTimer(); break;
      case 'finish': finishStep(); break;
      case 'skip': stopTimer(); startStep(); break;
      case 'more': if (P && P.addRest) P.addRest(); break;
      case 'theme':
      case 'theme-sw':
        S.settings.theme = S.settings.theme === 'dark' ? 'light' : 'dark';
        save(); applyTheme(); if (act === 'theme') render();
        break;
      case 'dlg-yes': closeDialog(true); break;
      case 'dlg-no': closeDialog(false); break;
      case 'celok': $('#celebrate').classList.add('hidden'); render(); break;
      case 'cal':
        calMonth.setMonth(calMonth.getMonth() + Number(t.dataset.d));
        render();
        break;
      case 'levelup':
        S.plan.forEach(e => { e.target = Math.max(e.target + 1, Math.round(e.target * 1.1)); });
        save(); render(); toast('Targets increased! 💪 Level up!');
        break;
      case 'defaults':
        ask('Reset workout?', 'Your exercise list will go back to the original 7 exercises with default targets. Your history is kept.', 'Reset', true).then(ok => {
          if (!ok) return;
          S.plan = DEFAULT_PLAN.map(e => Object.assign({}, e));
          planChanged(); toast('Default workout restored');
        });
        break;
      case 'add': openEditor(null); break;
      case 'edit': openEditor(id); break;
      case 'del': {
        const e = S.plan.find(x => x.id === id);
        if (S.plan.length <= 1) { toast('You need at least one exercise'); break; }
        ask('Delete ' + e.name + '?', 'It will be removed from your daily workout. Past history is kept.', 'Delete', true).then(ok => {
          if (!ok) return;
          S.plan = S.plan.filter(x => x.id !== id);
          planChanged(); toast(e.name + ' deleted');
        });
        break;
      }
      case 'move': {
        const i = S.plan.findIndex(x => x.id === id), j = i + Number(t.dataset.d);
        if (j < 0 || j >= S.plan.length) break;
        const tmp = S.plan[i]; S.plan[i] = S.plan[j]; S.plan[j] = tmp;
        save(); render();
        break;
      }
      case 'ed-icon':
        ED.icon = t.dataset.icon;
        document.querySelectorAll('.icon-pick button').forEach(b => b.classList.toggle('on', b === t));
        break;
      case 'ed-type':
        ED.type = t.dataset.type;
        document.querySelectorAll('.seg button').forEach(b => b.classList.toggle('on', b === t));
        $('#edUnit').textContent = ED.type === 'time' ? 'seconds' : 'reps';
        break;
      case 'ed-save': saveEditor(); break;
      case 'export':
        $('#backupBox').value = JSON.stringify(S);
        break;
      case 'copy': {
        const box = $('#backupBox');
        if (!box.value) box.value = JSON.stringify(S);
        box.select();
        let ok = false;
        try { ok = document.execCommand('copy'); } catch (e) { /* ignore */ }
        if (!ok && navigator.clipboard) navigator.clipboard.writeText(box.value).then(() => toast('Copied ✓'));
        else toast(ok ? 'Copied ✓' : 'Select the text and copy it');
        break;
      }
      case 'import': {
        try {
          const s = JSON.parse($('#backupBox').value);
          if (!s || !Array.isArray(s.plan) || typeof s.history !== 'object') throw new Error('bad');
          s.settings = Object.assign(fresh().settings, s.settings || {});
          ask('Restore backup?', 'All current data will be replaced with this backup.', 'Restore', false).then(ok => {
            if (!ok) return;
            S = s; save(); pushReminders(); applyTheme(); render(); toast('Backup restored ✓');
          });
        } catch (e) { toast('That doesn\'t look like a FitStreak backup'); }
        break;
      }
      case 'reset':
        ask('Delete all data?', 'All workout history, streaks and settings will be deleted. This cannot be undone.', 'Delete', true)
          .then(ok => ok && ask('Are you really sure?', 'Your ' + currentStreak() + '-day streak will be lost.', 'Yes, delete everything', true))
          .then(ok => {
            if (!ok) return;
            S = fresh(); save(); pushReminders(); applyTheme(); render(); toast('All data deleted');
          });
        break;
    }
  });

  document.addEventListener('change', ev => {
    const t = ev.target;
    if (t.dataset.set) {
      const k = t.dataset.set;
      S.settings[k] = t.type === 'checkbox' ? t.checked : (k === 'rest' ? Number(t.value) : t.value);
      if (k === 'remTime' && !t.value) S.settings.remTime = '06:30';
      save();
      if (k === 'remOn' || k === 'remTime' || k === 'eveningOn') { pushReminders(); toast('Reminder updated ⏰'); }
    }
  });

  // Android back button: return true when something was closed.
  window.handleBack = function () {
    if (!$('#dialog').classList.contains('hidden')) { closeDialog(false); return true; }
    if (!$('#celebrate').classList.contains('hidden')) { $('#celebrate').classList.add('hidden'); render(); return true; }
    if (P) { closePlayer(); return true; }
    if (tab !== 'today') { tab = 'today'; render(); return true; }
    return false;
  };

  // Re-render when coming back to the app (the date may have changed overnight).
  let lastDay = today();
  window.onAppResume = function () {
    if (today() !== lastDay) { lastDay = today(); if (!P) render(); }
  };
  document.addEventListener('visibilitychange', () => { if (!document.hidden) window.onAppResume(); });

  // ---------------------------------------------------------------- start
  applyTheme();
  save();
  pushReminders();
  render();
})();
