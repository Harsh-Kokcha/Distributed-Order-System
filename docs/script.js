(function () {
  'use strict';

  /* ---------------------------------------------------------------------
     Theme toggle
     ------------------------------------------------------------------- */
  var root = document.documentElement;
  var themeBtn = document.getElementById('theme-toggle');
  var THEME_KEY = 'dos-theme';

  function applyStoredTheme() {
    try {
      var stored = localStorage.getItem(THEME_KEY);
      if (stored === 'light' || stored === 'dark') root.setAttribute('data-theme', stored);
    } catch (e) { /* localStorage unavailable — fall back to prefers-color-scheme */ }
  }
  applyStoredTheme();

  if (themeBtn) {
    themeBtn.addEventListener('click', function () {
      var current = root.getAttribute('data-theme');
      var isDark = current ? current === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
      var next = isDark ? 'light' : 'dark';
      root.setAttribute('data-theme', next);
      try { localStorage.setItem(THEME_KEY, next); } catch (e) {}
    });
  }

  /* ---------------------------------------------------------------------
     Mobile nav toggle
     ------------------------------------------------------------------- */
  var navToggle = document.getElementById('nav-toggle');
  var navLinks = document.getElementById('nav-links');
  if (navToggle && navLinks) {
    navToggle.addEventListener('click', function () {
      var open = navLinks.classList.toggle('is-open');
      navToggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    navLinks.querySelectorAll('a[data-nav]').forEach(function (a) {
      a.addEventListener('click', function () {
        navLinks.classList.remove('is-open');
        navToggle.setAttribute('aria-expanded', 'false');
      });
    });
  }

  /* ---------------------------------------------------------------------
     Scroll progress bar + scrollspy nav
     ------------------------------------------------------------------- */
  var progressBar = document.getElementById('nav-progress');
  var navAnchors = Array.prototype.slice.call(document.querySelectorAll('a[data-nav]'));
  var sections = navAnchors
    .map(function (a) { return document.querySelector(a.getAttribute('href')); })
    .filter(Boolean);

  function onScroll() {
    var doc = document.documentElement;
    var scrollTop = doc.scrollTop || document.body.scrollTop;
    var height = (doc.scrollHeight || document.body.scrollHeight) - doc.clientHeight;
    if (progressBar) progressBar.style.width = (height > 0 ? (scrollTop / height) * 100 : 0) + '%';
  }
  document.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  if ('IntersectionObserver' in window && sections.length) {
    var spy = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        var id = entry.target.getAttribute('id');
        navAnchors.forEach(function (a) {
          a.classList.toggle('is-active', a.getAttribute('href') === '#' + id);
        });
      });
    }, { rootMargin: '-40% 0px -55% 0px', threshold: 0 });
    sections.forEach(function (s) { spy.observe(s); });
  }

  /* ---------------------------------------------------------------------
     Scroll-reveal
     ------------------------------------------------------------------- */
  var revealEls = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window) {
    var revealer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('in-view');
          revealer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });
    revealEls.forEach(function (el) { revealer.observe(el); });
  } else {
    revealEls.forEach(function (el) { el.classList.add('in-view'); });
  }

  /* ---------------------------------------------------------------------
     Floating SVG tooltips (architecture + docker diagrams)
     ------------------------------------------------------------------- */
  var tooltip = document.createElement('div');
  tooltip.className = 'svg-tooltip';
  document.body.appendChild(tooltip);

  function showTooltip(text, x, y) {
    tooltip.textContent = text;
    tooltip.classList.add('is-visible');
    positionTooltip(x, y);
  }
  function positionTooltip(x, y) {
    var pad = 14;
    var rect = tooltip.getBoundingClientRect();
    var left = x + pad;
    var top = y + pad;
    if (left + rect.width > window.innerWidth - 10) left = x - rect.width - pad;
    if (top + rect.height > window.innerHeight - 10) top = y - rect.height - pad;
    tooltip.style.left = left + 'px';
    tooltip.style.top = top + 'px';
  }
  function hideTooltip() { tooltip.classList.remove('is-visible'); }

  document.querySelectorAll('.node[data-tooltip]').forEach(function (node) {
    node.addEventListener('mousemove', function (e) {
      showTooltip(node.getAttribute('data-tooltip'), e.clientX, e.clientY);
    });
    node.addEventListener('mouseleave', hideTooltip);
    node.addEventListener('focus', function () {
      var box = node.getBoundingClientRect();
      showTooltip(node.getAttribute('data-tooltip'), box.left + box.width / 2, box.top);
    });
    node.addEventListener('blur', hideTooltip);
  });

  /* ---------------------------------------------------------------------
     Tabs (Concurrency section)
     ------------------------------------------------------------------- */
  document.querySelectorAll('[data-tabs]').forEach(function (wrap) {
    var buttons = wrap.querySelectorAll('.tab-btn');
    var panels = wrap.querySelectorAll('.tab-panel');
    buttons.forEach(function (btn) {
      btn.addEventListener('click', function () {
        buttons.forEach(function (b) { b.classList.remove('is-active'); });
        panels.forEach(function (p) { p.classList.remove('is-active'); });
        btn.classList.add('is-active');
        wrap.querySelector('[data-panel="' + btn.getAttribute('data-tab') + '"]').classList.add('is-active');
      });
    });
  });

  /* ---------------------------------------------------------------------
     SAGA state-machine path highlighting
     ------------------------------------------------------------------- */
  var sagaCaptions = {
    happy: 'PENDING → INVENTORY_RESERVED → COMPLETED. Inventory reserved, payment confirmed, order done.',
    'inv-rejected': 'PENDING → INVENTORY_REJECTED. Nothing was ever reserved, so there’s nothing to compensate — the saga ends immediately.',
    rollback: 'PENDING → INVENTORY_RESERVED → ROLLING_BACK → ROLLED_BACK. Payment failed after stock was reserved, so order-service publishes the compensating order-rolled-back event.'
  };
  var pathButtons = document.querySelectorAll('.path-buttons .chip-btn');
  var sagaCaption = document.getElementById('saga-caption');
  var stateNodes = document.querySelectorAll('[data-node]');
  var stateEdges = document.querySelectorAll('[data-edge]');

  function setSagaPath(path) {
    function matches(el) { return (el.getAttribute('data-group') || '').split(' ').indexOf(path) !== -1; }
    stateNodes.forEach(function (el) {
      el.classList.toggle('is-active', matches(el));
      el.classList.toggle('is-dim', !matches(el));
    });
    stateEdges.forEach(function (el) {
      el.classList.toggle('is-active', matches(el));
      el.classList.toggle('is-dim', !matches(el));
    });
    if (sagaCaption && sagaCaptions[path]) sagaCaption.textContent = sagaCaptions[path];
  }
  pathButtons.forEach(function (btn) {
    btn.addEventListener('click', function () {
      pathButtons.forEach(function (b) { b.classList.remove('is-active'); });
      btn.classList.add('is-active');
      setSagaPath(btn.getAttribute('data-path'));
    });
  });
  if (pathButtons.length) setSagaPath(pathButtons[0].getAttribute('data-path'));

  /* ---------------------------------------------------------------------
     Kafka sequence diagram playback
     ------------------------------------------------------------------- */
  var playBtn = document.getElementById('play-sequence');
  var seqSteps = document.querySelectorAll('.seq-step');
  var maxStep = 0;
  seqSteps.forEach(function (s) { maxStep = Math.max(maxStep, parseInt(s.getAttribute('data-step'), 10) || 0); });

  function playSequence() {
    if (!seqSteps.length) return;
    seqSteps.forEach(function (s) { s.classList.remove('is-played'); });
    playBtn.disabled = true;
    for (var i = 1; i <= maxStep; i++) {
      (function (step) {
        setTimeout(function () {
          document.querySelectorAll('.seq-step[data-step="' + step + '"]').forEach(function (s) {
            s.classList.add('is-played');
          });
          if (step === maxStep) playBtn.disabled = false;
        }, step * 650);
      })(i);
    }
  }
  if (playBtn) playBtn.addEventListener('click', playSequence);

  /* Auto-play once when the Kafka diagram first scrolls into view */
  var kafkaDiagram = document.getElementById('kafka');
  if (kafkaDiagram && 'IntersectionObserver' in window) {
    var autoPlay = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          playSequence();
          autoPlay.disconnect();
        }
      });
    }, { threshold: 0.4 });
    autoPlay.observe(kafkaDiagram);
  }

  /* ---------------------------------------------------------------------
     Copy-to-clipboard on command lines
     ------------------------------------------------------------------- */
  document.querySelectorAll('.cmd-line[data-copy]').forEach(function (line) {
    line.addEventListener('click', function () {
      var text = line.getAttribute('data-copy');
      var done = function () {
        line.classList.add('is-copied');
        setTimeout(function () { line.classList.remove('is-copied'); }, 1400);
      };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done).catch(function () {});
      } else {
        var ta = document.createElement('textarea');
        ta.value = text;
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand('copy'); done(); } catch (e) {}
        document.body.removeChild(ta);
      }
    });
  });

  /* ---------------------------------------------------------------------
     Syntax highlighting (progressive enhancement — page works without it)
     ------------------------------------------------------------------- */
  if (window.hljs) {
    try {
      // Scoped to language-tagged blocks only — the .cmd-lines command
      // block relies on its own hand-built <span> structure for the
      // copy-to-clipboard behavior above and must not be re-parsed.
      document.querySelectorAll('pre code[class*="language-"]').forEach(function (block) {
        window.hljs.highlightElement(block);
      });
    } catch (e) {}
  }
})();
