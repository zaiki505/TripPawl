const navIntroKey = "navIntroPlayed:v2";
const navigationEntry = performance.getEntriesByType("navigation")[0];

if (navigationEntry?.type === "reload") {
  sessionStorage.removeItem(navIntroKey);
}

if (!sessionStorage.getItem(navIntroKey)) {
  document.body.classList.add("nav-intro");
  sessionStorage.setItem(navIntroKey, "true");
}

if ("scrollRestoration" in history) {
  history.scrollRestoration = "manual";
}

/* A little hello for anyone poking around in DevTools. */
console.log(
  "%c👀 Since you're here - I'm open for work.%c\n✉️  shamsulzire@gmail.com",
  "font-size:14px;font-weight:bold;color:#a754ff;line-height:1.7;",
  "font-size:12px;color:#9b9b9b;"
);

document.addEventListener("DOMContentLoaded", () => {
  /* ==================== Theme toggle ==================== */
  const toggleBtn = document.getElementById("theme-toggle");
  const label = toggleBtn?.querySelector(".label");

  const setDark = () => {
    document.body.classList.remove("light");
    if (label) label.textContent = "Theme ☀️";
    toggleBtn?.setAttribute("data-theme-icon", "moon");
    localStorage.setItem("theme", "dark");
  };

  const setLight = () => {
    document.body.classList.add("light");
    if (label) label.textContent = "Theme 🌙";
    toggleBtn?.setAttribute("data-theme-icon", "sun");
    localStorage.setItem("theme", "light");
  };

  const saved = localStorage.getItem("theme");
  if (saved === "light") setLight();
  else if (saved === "dark") setDark();
  else if (window.matchMedia("(prefers-color-scheme: light)").matches) setLight();
  else setDark();

  // While toggling, apply a broad eased colour transition to every element for a short time
  let themeTransTimer = null;
  toggleBtn?.addEventListener("click", () => {
    document.body.classList.add("theme-transition");
    clearTimeout(themeTransTimer);
    themeTransTimer = setTimeout(
      () => document.body.classList.remove("theme-transition"),
      650
    );
    if (document.body.classList.contains("light")) setDark();
    else setLight();
    // Sample the nav contrast across the theme cross-fade so the text colour tracks the changing background.
    if (typeof updateHeaderContrast === "function") {
      const endT = performance.now() + 720;
      const tick = () => {
        updateHeaderContrast();
        if (performance.now() < endT) requestAnimationFrame(tick);
      };
      requestAnimationFrame(tick);
    }
  });

  /* ==================== Mobile menu ==================== */
  const nav = document.querySelector("nav");
  const mobileMenuToggle = document.getElementById("mobile-menu-toggle");

  const openMenu = () => {
    nav?.classList.add("menu-open");
    document.body.classList.add("nav-menu-open");
    if (typeof updateHeaderContrast === "function") updateHeaderContrast();
  };

  const closeMenu = () => {
    nav?.classList.remove("menu-open");
    document.body.classList.remove("nav-menu-open");
  };

  mobileMenuToggle?.addEventListener("click", () => {
    if (nav.classList.contains("menu-open")) closeMenu();
    else openMenu();
  });

  document.addEventListener("click", (event) => {
    if (nav?.classList.contains("menu-open") && !nav.contains(event.target)) {
      closeMenu();
    }
  });

  /* ==================== Squish click animation (delegated) ==================== */
  document.addEventListener("click", (event) => {
    const interactive = event.target.closest(
      "a, button, .bento-card, .project-card-modern, .project-card, .timeline-item, .evidence-card, .edu-card, .milestone-card, .gallery-item, .skill-card-v2, .quality-card, .about-block, .spm-summary, .skill-tier-header, .feature-card, .tech-concept-item, .contact-pill, .outcome-tag, .meta-tool-tag"
    );
    if (!interactive || interactive.closest("[data-no-squish], .no-squish")) return;

    interactive.classList.remove("ui-clicked");
    void interactive.offsetWidth;
    interactive.classList.add("ui-clicked");
  });

  document.addEventListener("animationend", (event) => {
    if (event.target.classList?.contains("ui-clicked")) {
      event.target.classList.remove("ui-clicked");
    }
  });

  /* ==================== Nav pill indicator (glides between pages) ==================== */
  const navList = document.querySelector(".nav-links");
  let indicator = null;
  let fill = null;

  const placeAt = (link) => {
    indicator.style.width = `${link.offsetWidth}px`;
    indicator.style.height = `${link.offsetHeight}px`;
    indicator.style.transform = `translate(${link.offsetLeft}px, ${link.offsetTop}px)`;
    indicator.classList.add("is-ready");
  };

  const snapTo = (link) => {
    indicator.style.transition = "none";
    placeAt(link);
    void indicator.offsetWidth;
    indicator.style.transition = "";
  };

  const updatePill = (animate) => {
    if (!navList || !indicator) return;
    const active = navList.querySelector('a[aria-current="page"]');
    if (!active) {
      indicator.classList.remove("is-ready");
      return;
    }
    if (animate && indicator.classList.contains("is-ready")) {
      indicator.classList.add("is-gliding");
      placeAt(active);
      fill.addEventListener(
        "animationend",
        () => indicator.classList.remove("is-gliding"),
        { once: true }
      );
    } else {
      snapTo(active);
    }
  };

  if (navList) {
    indicator = document.createElement("span");
    indicator.className = "nav-indicator";
    indicator.setAttribute("aria-hidden", "true");
    fill = document.createElement("span");
    fill.className = "nav-indicator-fill";
    indicator.appendChild(fill);
    navList.appendChild(indicator);
    window.addEventListener("resize", () => updatePill(false));

    navList.addEventListener("click", (e) => {
      const link = e.target.closest("a");
      if (!link || link.getAttribute("aria-current") !== "page") return;
      if (indicator.classList.contains("is-gliding")) return;
      indicator.classList.remove("is-clicking");
      void indicator.offsetWidth;
      indicator.classList.add("is-clicking");
      indicator.addEventListener("animationend", () => indicator.classList.remove("is-clicking"), { once: true });
      link.classList.add("pill-sync");
      link.addEventListener("animationend", () => link.classList.remove("pill-sync"), { once: true });
    });
  }

  /* ==================== Scroll-to-top button (mobile) ==================== */
  const scrollTopBtn = document.createElement("button");
  scrollTopBtn.className = "scroll-top-btn";
  scrollTopBtn.type = "button";
  scrollTopBtn.setAttribute("aria-label", "Scroll to top");
  scrollTopBtn.setAttribute("data-no-squish", "");
  scrollTopBtn.innerHTML =
    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="18 15 12 9 6 15"></polyline></svg>';
  document.body.appendChild(scrollTopBtn);

  let aboutMorph = null;
  let heroCardEdge = null;
  let atTop = true;

  let suppressAboutSnap = false;
  let suppressAboutSnapTimer = null;

  // Tracks how fast the user is scrolling so the About auto-snap only engages for slow/deliberate scrolls
  let lastScrollY = window.scrollY;
  let lastScrollT = performance.now();
  let scrollVelocity = 0; // smoothed px/ms
  const FAST_SCROLL_PX_PER_MS = 1.8;

  // Adaptive nav text colour: sample the luminance of the content just under the
  // header and flip the nav between light/dark text so it stays readable over
  // whatever is scrolling behind the (translucent) pill. Thanks to internet.
  const headerEl = document.querySelector("header");
  
  const _lumCanvas = document.createElement("canvas");
  _lumCanvas.width = _lumCanvas.height = 1;
  const _lumCtx = _lumCanvas.getContext("2d", { willReadFrequently: true });
  
  const colourLum = (c) => {
    if (!c) return null;
    _lumCtx.clearRect(0, 0, 1, 1);
    _lumCtx.fillStyle = c;
    _lumCtx.fillRect(0, 0, 1, 1);
    const d = _lumCtx.getImageData(0, 0, 1, 1).data;
    if (d[3] < 30) return null;
    return (0.2126 * d[0] + 0.7152 * d[1] + 0.0722 * d[2]) / 255;
  };

  const imgLum = (img) => {
    try {
      if (!img.complete || img.naturalWidth === 0) return null;
      _lumCtx.clearRect(0, 0, 1, 1);
      _lumCtx.drawImage(img, 0, 0, 1, 1);
      const d = _lumCtx.getImageData(0, 0, 1, 1).data;
      if (d[3] < 30) return null;
      return (0.2126 * d[0] + 0.7152 * d[1] + 0.0722 * d[2]) / 255;
    } catch (e) {
      return null;
    }
  };

  const bgLuminance = (el) => {
    while (el && el.nodeType === 1) {
      if (el.tagName.toLowerCase() === "img") {
        const l = imgLum(el);
        if (l !== null) return l;
      }
      const l = colourLum(getComputedStyle(el).backgroundColor);
      if (l !== null) return l;
      el = el.parentElement;
    }
    return colourLum(getComputedStyle(document.body).backgroundColor) ?? 0.1;
  };

  const updateHeaderContrast = () => {
    if (!headerEl) return;
    const r = headerEl.getBoundingClientRect();
    const y = r.top + r.height * 0.5;

    headerEl.style.pointerEvents = "none";
    let lums = [];
    for (const f of [0.2, 0.35, 0.5, 0.65, 0.8]) {
      const el = document.elementFromPoint(r.left + r.width * f, y);
      if (!el) continue;
      lums.push(bgLuminance(el));
    }
    headerEl.style.pointerEvents = "";
    if (!lums.length) return;

    const isLight = document.body.classList.contains("light");
    // Sort to find the "worst" contrast offenders
    // Light theme (dark text); worst is darkest (lowest lum), thus ascending
    // Dark theme (light text); worst is lightest (highest lum), thus descending
    lums.sort((a, b) => isLight ? a - b : b - a);

    // Average the worst ~40% of the strip
    const worstCount = Math.max(1, Math.round(lums.length * 0.4));
    let sum = 0;
    for (let i = 0; i < worstCount; i++) {
      sum += lums[i];
    }
    const lum = sum / worstCount;

    if (isLight) {
      // Light theme = dark text: a LIGHT scrim that appears only over dark content and fades out over light content.
      headerEl.style.setProperty("--nav-scrim-rgb", "255, 255, 255");
      headerEl.style.setProperty(
        "--nav-scrim",
        Math.min(0.55, (1 - lum) * 0.62).toFixed(3)
      );
    } else {
      // Dark theme = white text: a DARK scrim that strengthens over light content and stays subtle over dark content.
      headerEl.style.setProperty("--nav-scrim-rgb", "0, 0, 0");
      headerEl.style.setProperty("--nav-scrim", (0.16 + lum * 0.3).toFixed(3));
    }
  };

  const resetOnTop = () => {
    // Note: card scroll-reveal is intentionally NOT replayed. It plays once per page visit (fresh load or SPA navigation re-runs setupReveals).
    if (aboutMorph) aboutMorph.reset();
  };
  const onScroll = () => {
    const now = performance.now();
    const y = window.scrollY;
    const dt = now - lastScrollT;
    if (dt > 4) {
      const instVel = Math.abs(y - lastScrollY) / dt;
      scrollVelocity = scrollVelocity * 0.7 + instVel * 0.3;
    }
    lastScrollY = y;
    lastScrollT = now;

    scrollTopBtn.classList.toggle("is-visible", window.scrollY > 400);
    updateHeaderContrast();
    heroCardEdge ??= document.querySelector(".hero-card-edge");
    heroCardEdge?.classList.toggle("peeking", window.scrollY > 20);
    const nowTop = window.scrollY <= 2;
    if (nowTop && !atTop) resetOnTop();
    atTop = nowTop;
  };
  let scrollTicking = false;
  const requestScrollUpdate = () => {
    if (scrollTicking) return;
    scrollTicking = true;
    requestAnimationFrame(() => {
      onScroll();
      scrollTicking = false;
    });
  };
  onScroll();
  window.addEventListener("scroll", requestScrollUpdate, { passive: true });
  scrollTopBtn.addEventListener("click", () => {
    window.scrollTo({ top: 0, behavior: "smooth" });
  });

  // Smooth-scroll triggers
  document.addEventListener("click", (event) => {
    const trigger = event.target.closest("[data-scroll-to]");
    if (!trigger) return;
    const target = document.querySelector(trigger.getAttribute("data-scroll-to"));
    if (target) {
      event.preventDefault();
      target.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  });

  // Any same-page link heading somewhere other than About suppresses the About
  // auto-snap for the duration of that scroll, so it doesn't get yanked
  // back up to About mid-transit.
  document.addEventListener("click", (event) => {
    const link = event.target.closest('a[href^="#"]');
    if (!link) return;
    const hash = link.getAttribute("href");
    if (!hash || hash.length < 2 || hash === "#about") return;
    if (!document.querySelector(hash)) return;

    suppressAboutSnap = true;
    clearTimeout(suppressAboutSnapTimer);
    const clearSuppress = () => {
      suppressAboutSnap = false;
    };
    if ("onscrollend" in window) {
      window.addEventListener("scrollend", clearSuppress, { once: true });
      suppressAboutSnapTimer = setTimeout(clearSuppress, 3000);
    } else {
      suppressAboutSnapTimer = setTimeout(clearSuppress, 1200);
    }
  });

  /* ==================== URL helpers ==================== */
  const normPath = (href) => {
    let p = new URL(href, location.href).pathname;
    p = p.replace(/\/index\.html$/, "/").replace(/\.html$/, "");
    return p === "" ? "/" : p;
  };

  const isSpecialUrl = (v) =>
    !v || v.startsWith("#") || v.startsWith("data:") || /^[a-z]+:/i.test(v);

  const absolutize = (root, baseHref) => {
    root.querySelectorAll("[href]").forEach((el) => {
      const v = el.getAttribute("href");
      if (!isSpecialUrl(v)) el.setAttribute("href", new URL(v, baseHref).href);
    });
    root.querySelectorAll("[src]").forEach((el) => {
      const v = el.getAttribute("src");
      if (!isSpecialUrl(v)) el.setAttribute("src", new URL(v, baseHref).href);
    });
  };

  const header = document.querySelector("header");
  if (header) absolutize(header, location.href);

  const setActiveNav = (href) => {
    const target = normPath(href);
   
    let activePath = target;
    if (target.startsWith("/projects/")) activePath = "/projects";
    else if (target === "/resume") activePath = "/about";
    navList?.querySelectorAll("a").forEach((a) => {
      const ap = normPath(a.href);
      const isActive = ap === activePath;
      if (isActive) a.setAttribute("aria-current", "page");
      else a.removeAttribute("aria-current");
    });
    const logoA = document.querySelector(".logo a");
    if (logoA) {
      const home = normPath(logoA.href) === target;
      if (home) logoA.setAttribute("aria-current", "page");
      else logoA.removeAttribute("aria-current");
      logoA.setAttribute("data-tooltip", home ? "You're home" : "Back to home");
    }
  };

  // Split the hero title into per-character spans so a soft blur wave can travel across it. Words stay grouped so they don't wrap mid-word.
  const splitHeroTitle = () => {
    const title = document.querySelector(".hero-title");
    if (!title || title.dataset.split) return;
    title.dataset.split = "1";

    let index = 0;
    const buildWord = (text, isGrad) => {
      const word = document.createElement("span");
      word.className = isGrad ? "hero-word hero-word--grad" : "hero-word";
      for (const ch of text) {
        const c = document.createElement("span");
        c.className = "hero-char";
        c.style.setProperty("--i", index++);
        c.textContent = ch;
        word.appendChild(c);
      }
      return word;
    };

    const frag = document.createDocumentFragment();
    Array.from(title.childNodes).forEach((node) => {
      const isGrad =
        node.nodeType === Node.ELEMENT_NODE &&
        node.classList.contains("text-gradient");
      (node.textContent || "").split(/(\s+)/).forEach((part) => {
        if (part === "") return;
        if (/^\s+$/.test(part)) frag.appendChild(document.createTextNode(part));
        else frag.appendChild(buildWord(part, isGrad));
      });
    });

    title.textContent = "";
    title.appendChild(frag);
  };

  // Scroll reveal card lists (projects, skills). Cards fade & zoom & unblur as they enter view. Reveal only happens on the way DOWN and is NOT removed
  // when a card leaves, so scrolling up partway never re-triggers it. The reveal is re-armed only when the user returns to the very top (see resetOnTop).
  const REVEAL_SELECTOR =
    ".project-card-modern, .skill-card-v2, .quality-card";
  const revealObserver =
    "IntersectionObserver" in window
      ? new IntersectionObserver(
          (entries) => {
            entries.forEach((entry) => {
              if (entry.isIntersecting) entry.target.classList.add("in-view");
            });
          },
          { threshold: 0.18, rootMargin: "0px 0px -8% 0px" }
        )
      : null;

  const setupReveals = () => {
    if (!revealObserver) return;
    document.querySelectorAll(REVEAL_SELECTOR).forEach((el) => {
      if (el.dataset.reveal) return;
      el.dataset.reveal = "1";
      el.classList.add("reveal-item");
      
      const rect = el.getBoundingClientRect();
      if (rect.top < window.innerHeight && rect.bottom > 0) {
        el.classList.add("in-view");
      }
      const siblings = Array.from(el.parentElement.children).filter((c) =>
        c.matches(REVEAL_SELECTOR)
      );
      el.style.setProperty("--reveal-i", siblings.indexOf(el));
      revealObserver.observe(el);
    });
  };

  // Hero peek -> About SEAMLESS morph (hopefully). The three peek cards are absolutely positioned in <main> (document space). When the About section is about to be
  // seen, each peek card animates its exact box until it lands on its matching About card. then a crossfade hands off to the real card.
  // Because both live in document space, the morph stays locked to the cards even while scrolling. Resets at the very top.
  // ggs broder..
  const setupAboutMorph = () => {
    aboutMorph = null;
    const peek = document.querySelector(".scroll-peek");
    const about = document.querySelector("#about");
    const main = document.querySelector(".home-page");
    if (!peek || !about || !main) return;
    const squares = Array.from(peek.querySelectorAll(".peek-card"));
    const cards = Array.from(about.querySelectorAll(".bento-card"));
    if (!squares.length || squares.length !== cards.length) return;
    const cardEdge = document.querySelector(".hero-card-edge");

    let morphed = false;
    let revealTimer = null;
    let rests = [];

    const computeRest = () => {
      const mr = main.getBoundingClientRect();
      const mainTopDoc = mr.top + window.scrollY;
      const vw = main.clientWidth;
      const vh = window.innerHeight;
      const rem =
        parseFloat(getComputedStyle(document.documentElement).fontSize) || 16;
      const cx = vw / 2;

      // Fraction of the card shown above the fold; the rest peeks below it. The
      // deck is ALWAYS clipped by the bottom edge - it never shows in full.
      const PEEK = 0.7;

      // Bottom of the hero CTAs, via layout offsets (robust while the hero
      // entrance still has the CTAs transformed).
      const cta = document.querySelector(".hero-ctas");
      let ctaBottomDoc;
      if (cta) {
        let y = 0;
        let node = cta;
        while (node && node !== main) {
          y += node.offsetTop;
          node = node.offsetParent;
        }
        ctaBottomDoc = mainTopDoc + y + cta.offsetHeight;
      } else {
        ctaBottomDoc = mainTopDoc + vh * 0.72;
      }

      // Clear gap kept between the CTAs and the top of the visible deck, and capped
      // gap between the fanned deck and the screen's side edges.
      const topGap = Math.round(Math.min(Math.max(rem * 1.1, vh * 0.025), rem * 3.5));
      const sideGap = Math.round(Math.min(Math.max(vw * 0.05, 16), 96));

      // Adaptive height: scales with viewport height, width AND font size.
      let h = Math.min(vh * 0.26, vw * 0.18, rem * 13, 230);
      // The VISIBLE part (PEEK*h) must fit between the CTAs (+gap) and the fold
      // Cap the height to guarantee it - on short screens the deck simply shrinks.
      const roomAboveFold = vh - ctaBottomDoc - topGap; // fold at doc y = vh (scrollY 0)
      if (roomAboveFold > 0) h = Math.min(h, roomAboveFold / PEEK);
      h = Math.max(60, h);

      // Width from the aspect ratio, then shrink the WHOLE fan if needed so it
      // keeps the side-edge gaps (rule 2). 0.62 of w covers the rotation overhang.
      let w = h / 0.62;
      // Fan spread grows with screen width (so the trio doesn't look lost in the
      // middle of wide screens), but is shrunk below to honour the side gaps.
      let spread = Math.max(w * 0.58, Math.min(vw * 0.14, 260));
      const maxHalf = vw / 2 - sideGap;
      const deckHalf = spread + w * 0.62;
      if (deckHalf > maxHalf && deckHalf > 0) {
        const k = maxHalf / deckHalf;
        w *= k;
        spread *= k;
        h = w * 0.62;
      }
      w = Math.round(w);
      h = Math.round(h);
      spread = Math.round(spread);
      const drop = Math.round(h * 0.08);

      // Anchor at the bottom edge of the screen: PEEK of the (centre) card shows
      // above the fold, the remainder spills below it. Clamp so the top always
      // stays below the CTAs, even on very short screens
      let topDoc = vh - PEEK * h;
      topDoc = Math.max(topDoc, ctaBottomDoc + topGap);
      const top = topDoc - mainTopDoc;

      rests = [
        { left: cx - w / 2 - spread, top: top + drop, w, h, rot: -11 },
        { left: cx - w / 2, top: top, w, h, rot: 0 },
        { left: cx - w / 2 + spread, top: top + drop, w, h, rot: 11 },
      ];
    };

    const applyRest = (animate) => {
      squares.forEach((s, i) => {
        const r = rests[i];
        if (!r) return;
        if (!animate) s.style.transition = "none";
        s.style.left = `${r.left}px`;
        s.style.top = `${r.top}px`;
        s.style.width = `${r.w}px`;
        s.style.height = `${r.h}px`;
        s.style.transform = `rotate(${r.rot}deg)`;
        s.style.opacity = "";
        if (!animate) {
          void s.offsetWidth;
          s.style.transition = "";
        }
      });
    };

    // Lock each peek card onto its matching About card's exact box. 
    // Used by the morph and, on resize, to keep the deck tracking the About cards in real time.
    const positionOnAbout = (animate) => {
      const mr = main.getBoundingClientRect();
      squares.forEach((s, i) => {
        const r = cards[i].getBoundingClientRect();
        if (!animate) s.style.transition = "none";
        s.style.left = `${r.left - mr.left}px`;
        s.style.top = `${r.top - mr.top}px`;
        s.style.width = `${r.width}px`;
        s.style.height = `${r.height}px`;
        s.style.transform = "rotate(0deg)";
        if (!animate) {
          void s.offsetWidth;
          s.style.transition = "";
        }
      });
    };

    let placed = false;
    // Seeded to setup time (not 0) so a stray early IntersectionObserver
    // callback during initial layout/font-swap right after a reload, before the page settled
    let lastResetT = performance.now();
    const measure = (animate) => {
      computeRest();
      if (morphed) {
        // Keep the deck glued to the (re-laid-out) About cards while resizing.
        positionOnAbout(animate);
      } else {
        applyRest(animate && placed);
        placed = true;
      }
    };

    const doMorph = () => {
      if (morphed) return;
      if (window.scrollY < 40 && performance.now() - lastResetT < 900) return;
      morphed = true;
      cardEdge?.classList.add("expanded");
      peek.classList.add("peek-morphing");
      positionOnAbout(true);
      if (!suppressAboutSnap && scrollVelocity < FAST_SCROLL_PX_PER_MS) {
        setTimeout(() => {
          // Re-check right before snapping: if the user has since scrolled past About entirely, leave them alone instead of yanking them back.
          if (about.getBoundingClientRect().bottom < 0) return;
          about.scrollIntoView({ behavior: "smooth", block: "center" });
        }, 250);
      }
      clearTimeout(revealTimer);

      revealTimer = setTimeout(() => {
        about.classList.add("about-revealed");
        peek.classList.add("peek-gone");
      }, 700);
    };

    // Reverse morph (on returning to the very top): the About cards fade out and
    // the peek cards fade back in over their boxes, then glide back to the resting fanned peek.
    const reset = () => {
      if (!morphed) return;
      morphed = false;
      cardEdge?.classList.remove("expanded");
      lastResetT = performance.now();
      clearTimeout(revealTimer);
      about.classList.remove("about-revealed");
      peek.classList.remove("peek-gone");
      applyRest(true);

      setTimeout(() => {
        if (!morphed) peek.classList.remove("peek-morphing");
      }, 700);
    };

    measure();
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) doMorph();
        });
      },
      { threshold: 0, rootMargin: "0px 0px -20% 0px" }
    );
    io.observe(about);

    aboutMorph = { reset, measure };
  };

  /* ==================== Page features (re-bound after each swap) ==================== */
  const initPageFeatures = () => {
    splitHeroTitle();
    setupReveals();
    setupAboutMorph();

    // Category filter bars (projects + skills)
    document.querySelectorAll("[data-filter-bar]").forEach((bar) => {
      if (bar.dataset.bound) return;
      bar.dataset.bound = "1";
      const targetSelector = bar.getAttribute("data-filter-target");
      if (!targetSelector) return;

      const items = Array.from(document.querySelectorAll(targetSelector));
      const groups = Array.from(document.querySelectorAll("[data-filter-group]"));

      bar.addEventListener("click", (event) => {
        const btn = event.target.closest(".filter-btn");
        if (!btn) return;

        bar
          .querySelectorAll(".filter-btn")
          .forEach((b) => b.classList.remove("active"));
        btn.classList.add("active");

        const filter = btn.dataset.filter;
        items.forEach((item) => {
          const cats = (item.dataset.category || "").split(/\s+/);
          const show = filter === "all" || cats.includes(filter);
          item.classList.toggle("filtered-hidden", !show);
        });

        groups.forEach((group) => {
          const hasVisible = group.querySelector(
            `${targetSelector}:not(.filtered-hidden)`
          );
          group.classList.toggle("filtered-hidden", !hasVisible);
        });
      });
    });

    // SPM subject breakdown dropdown: animate open AND close every time it's toggled
    document.querySelectorAll(".spm-details").forEach((details) => {
      if (details.dataset.bound) return;
      details.dataset.bound = "1";
      const summary = details.querySelector(".spm-summary");
      const content = details.querySelector(".spm-content");
      if (!summary || !content) return;

      summary.addEventListener("click", (event) => {
        if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
        event.preventDefault();
        if (content.dataset.animating) return;

        const play = (name, dur) => {
          content.style.animation = "none";
          void content.offsetWidth; // restart the animation
          content.style.animation = `${name} ${dur}`;
        };

        if (details.open) {
          content.dataset.animating = "1";
          play("spmCollapse", "0.35s ease forwards");
          content.addEventListener(
            "animationend",
            () => {
              details.open = false;
              content.style.animation = "";
              delete content.dataset.animating;
            },
            { once: true }
          );
        } else {
          details.open = true;
          play("spmExpand", "0.45s cubic-bezier(0.22, 1, 0.36, 1)");
          content.addEventListener(
            "animationend",
            () => {
              content.style.animation = "";
            },
            { once: true }
          );
        }
      });
    });

    // CGPA bars (education)
    document.querySelectorAll(".cgpa-bar-wrap").forEach((bar) => {
      if (bar.dataset.bound) return;
      bar.dataset.bound = "1";
      bar.addEventListener("click", () => {
        bar.classList.remove("clicked");
        void bar.offsetWidth;
        bar.classList.add("clicked");
      });
      bar.addEventListener("animationend", () => bar.classList.remove("clicked"));
    });

    // Print buttons
    document.querySelectorAll("[data-print]").forEach((button) => {
      if (button.dataset.bound) return;
      button.dataset.bound = "1";
      button.addEventListener("click", () => window.print());
    });

    // Certificate links: reveal the arrow icon only if the file actually exists
    // Upload a file matching data-cert and it shows automatically, otherwise icon stays hidden.
    document.querySelectorAll(".milestone-cert[data-cert]").forEach((link) => {
      if (link.dataset.checked) return;
      link.dataset.checked = "1";
      const url = new URL(link.dataset.cert, document.baseURI).href;
      fetch(url, { method: "HEAD" })
        .then((res) => {
          if (res.ok) {
            link.href = url;
            link.classList.add("is-available");
          }
        })
        .catch(() => {});
    });

    // Easter egg 2 implementation
    const coinPhoto = document.querySelector(".profile-photo.coin-photo");
    if (coinPhoto && !coinPhoto.dataset.bound) {
      coinPhoto.dataset.bound = "1";
      const inner = coinPhoto.querySelector(".coin-inner");
      let turns = 0;
      let onBack = false;
      let returnTimer = null;
      const reveal = () => {
        if (!onBack) {
          turns += 1; // always spin forward
          inner.style.transform = `rotateY(${turns * 180}deg)`;
          onBack = true;
        }
        clearTimeout(returnTimer);
        returnTimer = setTimeout(() => {
          turns += 1;
          inner.style.transform = `rotateY(${turns * 180}deg)`;
          onBack = false;
        }, 3000);
      };
      coinPhoto.addEventListener("click", reveal);
      coinPhoto.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          reveal();
        }
      });
    }

    // Contact form
    const contactForm = document.getElementById("contact-form");
    if (contactForm && !contactForm.dataset.bound) {
      contactForm.dataset.bound = "1";
      const statusEl = document.getElementById("form-status");
      const showStatus = (msg, state) => {
        if (!statusEl) return;
        statusEl.textContent = msg;
        statusEl.dataset.state = state;
        statusEl.classList.add("is-visible");
      };

      const emailInput = document.getElementById("email");
      const messageInput = document.getElementById("message");
      const emailError = document.getElementById("email-error");
      const messageError = document.getElementById("message-error");

      const setFieldError = (input, errorEl, msg) => {
        input.classList.toggle("is-invalid", !!msg);
        if (!errorEl) return;
        errorEl.textContent = msg || "";
        errorEl.classList.toggle("is-visible", !!msg);
      };

      // `touched` gates the "required" message so we don't scold an empty field
      // the user hasn't left yet - format errors still show live as they type.
      const validateEmail = (touched) => {
        const val = emailInput.value.trim();
        if (!val) {
          setFieldError(emailInput, emailError, touched ? "Email is required." : "");
          return false;
        }
        const ok = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val);
        setFieldError(emailInput, emailError, ok ? "" : "Enter a valid email address.");
        return ok;
      };

      const validateMessage = (touched) => {
        const val = messageInput.value.trim();
        if (!val) {
          setFieldError(messageInput, messageError, touched ? "Message is required." : "");
          return false;
        }
        const ok = val.length >= 10;
        setFieldError(
          messageInput,
          messageError,
          ok ? "" : `Message should be at least 10 characters (${val.length}/10).`
        );
        return ok;
      };

      emailInput.addEventListener("input", () => validateEmail(false));
      emailInput.addEventListener("blur", () => validateEmail(true));
      messageInput.addEventListener("input", () => validateMessage(false));
      messageInput.addEventListener("blur", () => validateMessage(true));

      const btn = contactForm.querySelector(".contact-submit");
      const btnLabel = btn.querySelector(".btn-label");
      const origLabel = btnLabel.textContent;

      const setButtonState = (state, label) => {
        btn.classList.remove("is-loading", "is-success", "is-error");
        if (state) btn.classList.add(state);
        btnLabel.textContent = label;
      };

      // Briefly flashes the error state without touching `disabled`, so the
      // user can immediately correct the field and resubmit.
      const flashFieldsError = () => {
        setButtonState("is-error", "Check Fields");
        setTimeout(() => setButtonState(null, origLabel), 2000);
      };

      contactForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        const nameInput = document.getElementById("name");
        const name = nameInput.value.trim();
        const email = emailInput.value.trim();
        const message = messageInput.value.trim();
        const emailOk = validateEmail(true);
        const messageOk = validateMessage(true);

        if (!name) {
          showStatus("Please fill in all fields before sending.", "error");
          nameInput.focus();
          flashFieldsError();
          return;
        }
        if (!emailOk) {
          showStatus("Please fix the highlighted fields before sending.", "error");
          emailInput.focus();
          flashFieldsError();
          return;
        }
        if (!messageOk) {
          showStatus("Please fix the highlighted fields before sending.", "error");
          messageInput.focus();
          flashFieldsError();
          return;
        }

        setButtonState("is-loading", "Sending…");
        btn.disabled = true;

        try {
          const token = await new Promise((resolve) =>
            grecaptcha.ready(() =>
              grecaptcha.execute("6Ld0pDotAAAAAN1yRtyUR2TKwSqODxk665vKhQJ2", { action: "contact" }).then(resolve)
            )
          );
          const res = await fetch("https://formspree.io/f/xojowbpl", {
            method: "POST",
            headers: { "Content-Type": "application/json", Accept: "application/json" },
            body: JSON.stringify({ name, email, message, "g-recaptcha-response": token }),
          });
          if (res.ok) {
            showStatus("Message sent! I'll get back to you soon.", "success");
            contactForm.reset();
            setFieldError(emailInput, emailError, "");
            setFieldError(messageInput, messageError, "");
            setButtonState("is-success", "Message Sent!");
          } else {
            const data = await res.json().catch(() => ({}));
            showStatus(data.error || "Something went wrong. Try emailing shamsulzire@gmail.com directly.", "error");
            setButtonState("is-error", "Failed to Send");
          }
        } catch {
          showStatus("Network error. Try to reload the page or email shamsulzire@gmail.com directly.", "error");
          setButtonState("is-error", "Failed to Send");
        } finally {
          setTimeout(() => {
            setButtonState(null, origLabel);
            btn.disabled = false;
          }, 2200);
        }
      });
    }

    // Media screenshot lightbox bindings
    const mediaImgs = [...document.querySelectorAll(".media-platform img")];
    if (mediaImgs.length && typeof openLightbox === "function") {
      mediaImgs.forEach((img, i) => {
        img.addEventListener("click", () => openLightbox(mediaImgs, i));
      });
    }
  };

  // Expanded footer. Built once, the footer lives outside <main>, so its consistent across SPA navigations.
  const setupFooter = () => {
    const footer = document.querySelector("footer");
    if (!footer || footer.dataset.expanded) return;
    footer.dataset.expanded = "1";
    footer.classList.add("site-footer");

    const logoHref = document.querySelector(".logo a")?.href || location.href;
    const u = (rel) => new URL(rel, logoHref).href;
    const links = [
      ["Home", "index.html"],
      ["About Me", "about.html"],
      ["Education", "education.html"],
      ["Projects", "projects.html"],
      ["Skills", "qualities.html"],
      ["Contact", "contact.html"],
    ]
      .map(([label, rel]) => `<li><a href="${u(rel)}">${label}</a></li>`)
      .join("");

    const catSvg = `
      <svg class="footer-cat-svg" viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
        <path class="cat-fur" d="M24 40 L17 15 L43 30 Z"/>
        <path class="cat-fur" d="M76 40 L83 15 L57 30 Z"/>
        <path class="cat-ear-in" d="M26 38 L21 21 L39 31 Z"/>
        <path class="cat-ear-in" d="M74 38 L79 21 L61 31 Z"/>
        <ellipse class="cat-fur cat-head" cx="50" cy="58" rx="35" ry="31"/>
        <g class="cat-whiskers">
          <line class="cat-whisker" x1="7" y1="56" x2="26" y2="58"/>
          <line class="cat-whisker" x1="7" y1="64" x2="26" y2="63"/>
          <line class="cat-whisker" x1="93" y1="56" x2="74" y2="58"/>
          <line class="cat-whisker" x1="93" y1="64" x2="74" y2="63"/>
        </g>
        <circle class="cat-blush" cx="28" cy="64" r="5.5"/>
        <circle class="cat-blush" cx="72" cy="64" r="5.5"/>
        <g class="cat-eye cat-eye-l">
          <ellipse class="cat-eye-white" cx="37" cy="54" rx="8" ry="10"/>
          <g class="cat-pupil-wrap"><circle class="cat-pupil" cx="37" cy="55" r="4"/></g>
        </g>
        <g class="cat-eye cat-eye-r">
          <ellipse class="cat-eye-white" cx="63" cy="54" rx="8" ry="10"/>
          <g class="cat-pupil-wrap"><circle class="cat-pupil" cx="63" cy="55" r="4"/></g>
        </g>
        <path class="cat-eye-happy" d="M30 56 q7 -8 14 0"/>
        <path class="cat-eye-happy" d="M56 56 q7 -8 14 0"/>
        <path class="cat-brow" d="M28 41 L45 47"/>
        <path class="cat-brow" d="M72 41 L55 47"/>
        <path class="cat-nose" d="M46 62 H54 L50 67 Z"/>
        <path class="cat-mouth mouth-neutral" d="M50 67 q-5 5 -10 1 M50 67 q5 5 10 1"/>
        <path class="cat-mouth mouth-happy" d="M38 68 q12 11 24 0"/>
        <path class="cat-mouth mouth-angry" d="M40 74 q10 -7 20 0"/>
        <path class="cat-mouth mouth-annoyed" d="M 42 70 L 58 70" />
        <path class="cat-sweat" d="M84 39 q-4 7 0 11 q4 -4 0 -11 Z"/>
      </svg>`;

    footer.innerHTML = `
      <div class="footer-inner">
        <div class="footer-col footer-about">
          <h4>zaiki's Portfolio</h4>
          <p>A personal portfolio showcasing my projects, skills, and journey as
          an aspiring web &amp; app developer - built with a focus on clean,
          minimalist, and functional UI/UX.</p>
          <p class="footer-credit">Designed &amp; built by Muhd Uzair (zaiki).</p>
        </div>
        <div class="footer-col footer-links-col">
          <h4>Quick Links</h4>
          <ul class="footer-links">${links}</ul>
        </div>
        <div class="footer-col footer-cat-col">
          <button class="footer-cat" type="button" aria-label="Pet the cat" title="Don't click me!"
          >${catSvg}</button>
          <span class="footer-cat-hint">
            Meow!
          </span>
        </div>
      </div>
      <div class="footer-bottom"><small>&copy; 2026 zaiki. All Rights Reserved.</small></div>`;

    const cat = footer.querySelector(".footer-cat");
    const pupils = footer.querySelectorAll(".cat-pupil");
    let catTimer = null;

    const tempMood = (cls, ms) => {
      cat.classList.remove("cat--happy", "cat--angry", "cat--annoyed", "cat--react");
      cat.classList.add(cls);
      clearTimeout(catTimer);
      catTimer = setTimeout(() => {
        cat.classList.remove(cls);
        if (cat.matches(":hover")) cat.classList.add("cat--happy");
      }, ms);
    };

    cat.addEventListener("mouseenter", () => {
      if (!cat.classList.contains("cat--angry") && !cat.classList.contains("cat--annoyed")) {
        cat.classList.add("cat--happy");
      }
    });
    cat.addEventListener("mouseleave", () => cat.classList.remove("cat--happy"));

    // Spam-click -> cat rages and flee off the screen, then bounces back.
    let clickTimes = [];
    let fleeing = false;
    const fleeCat = () => {
      if (fleeing) return;
      fleeing = true;
      clearTimeout(catTimer);
      cat.classList.remove(
        "cat--happy",
        "cat--angry",
        "cat--annoyed",
        "cat--react",
        "ui-clicked"
      );
      cat.classList.add("cat--rage", "cat--flee");
      const onDone = (e) => {
        if (e.animationName !== "catFlee") return;
        cat.removeEventListener("animationend", onDone);
        cat.classList.remove("cat--rage", "cat--flee");
        fleeing = false;
      };
      cat.addEventListener("animationend", onDone);
    };

    cat.addEventListener("click", (event) => {
      event.preventDefault();
      if (fleeing) return;
      const now = Date.now();
      clickTimes = clickTimes.filter((t) => now - t < 1400);
      clickTimes.push(now);
      if (clickTimes.length >= 5) {
        clickTimes = [];
        fleeCat();
        return;
      }
      tempMood(Math.random() < 0.5 ? "cat--angry" : "cat--annoyed", 1200);
    });

    // Reacting to any footer link click
    footer.addEventListener("click", (event) => {
      if (event.target.closest("a")) tempMood("cat--react", 750);
    });

    // Eyes track the cursor
    document.addEventListener(
      "mousemove",
      (event) => {
        const r = cat.getBoundingClientRect();
        if (!r.width) return;
        const cx = r.left + r.width / 2;
        const cy = r.top + r.height / 2;
        
        let dx, dy;
        if (cat.classList.contains("cat--annoyed")) {
          // Bombastic side-eye
          const isRight = event.clientX > cx;
          dx = isRight ? 4.5 : -4.5;
          dy = 0;
        } else {
          const ang = Math.atan2(event.clientY - cy, event.clientX - cx);
          const dist = Math.min(3.2, Math.hypot(event.clientX - cx, event.clientY - cy) / 50);
          dx = (Math.cos(ang) * dist).toFixed(2);
          dy = (Math.sin(ang) * dist).toFixed(2);
        }
        
        pupils.forEach((p) => p.setAttribute("transform", `translate(${dx} ${dy})`));
      },
      { passive: true }
    );

    const scheduleBlink = () => {
      // Random interval between 2.5s and 6.5s
      const delay = 2500 + Math.random() * 4000;
      setTimeout(() => {
        if (fleeing || cat.classList.contains("cat--happy")) {
          scheduleBlink();
          return;
        }
        
        cat.classList.add("cat--blink");
        setTimeout(() => {
          cat.classList.remove("cat--blink");
          scheduleBlink();
        }, 150);
      }, delay);
    };
    scheduleBlink();
  };

  /* ==================== SPA router ==================== */
  let currentPath = normPath(location.href);
  let navToken = 0;

  const scrollToHashOrTop = (href) => {
    const hash = new URL(href, location.href).hash;
    if (hash) {
      const el = document.querySelector(hash);
      if (el) {
        el.scrollIntoView({ behavior: "smooth" });
        return;
      }
    }
    window.scrollTo(0, 0);
  };

  const navigateTo = async (href, push) => {
    const token = ++navToken;

    let html;
    let res;
    try {
      res = await fetch(href);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      html = await res.text();
    } catch (err) {
      window.location.href = href;
      return;
    }
    if (token !== navToken) return;

    const doc = new DOMParser().parseFromString(html, "text/html");
    const incoming = doc.querySelector("main");
    const current = document.querySelector("main");
    if (!incoming || !current) {
      window.location.href = href;
      return;
    }

    absolutize(incoming, href);
    const imported = document.importNode(incoming, true);
    current.replaceWith(imported);

    document.title = doc.title || document.title;
    const finalHref = res.url || href;
    if (push) history.pushState({ spa: true }, "", finalHref);
    currentPath = normPath(finalHref);

    setActiveNav(finalHref);
    updatePill(true);
    initPageFeatures();
    closeMenu();
    scrollToHashOrTop(href);
    requestAnimationFrame(updateHeaderContrast);
  };

  document.addEventListener("click", (event) => {
    if (
      event.defaultPrevented ||
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey
    ) {
      return;
    }

    const link = event.target.closest("a");
    if (!link || link.target === "_blank" || link.hasAttribute("download")) return;

    const url = new URL(link.href, location.href);
    if (url.origin !== location.origin) return;

    const lastSeg = url.pathname.split("/").pop();
    const isHtml = url.pathname.endsWith("/") || lastSeg.endsWith(".html") || !lastSeg.includes(".");
    if (!isHtml) return;

    if (normPath(url.href) === currentPath && url.hash) return;

    event.preventDefault();

    if (normPath(url.href) === currentPath && !url.hash) return;

    navigateTo(url.href, true);
  });

  window.addEventListener("popstate", () => {
    if (normPath(location.href) === currentPath) {
      scrollToHashOrTop(location.href);
    } else {
      navigateTo(location.href, false);
    }
  });

  /* ==================== Image lightbox (built once) ==================== */
  const _lb = document.createElement("div");
  _lb.id = "img-lightbox";
  _lb.className = "img-lightbox";
  _lb.setAttribute("role", "dialog");
  _lb.setAttribute("aria-modal", "true");
  _lb.setAttribute("aria-label", "Image viewer");
  _lb.innerHTML = `
    <div class="lightbox-backdrop"></div>
    <div class="lightbox-nav-row">
      <button class="lightbox-prev" aria-label="Previous image">&#8249;</button>
      <div class="lightbox-content"><img class="lightbox-img" src="" alt="" /></div>
      <button class="lightbox-next" aria-label="Next image">&#8250;</button>
    </div>
    <button class="lightbox-close" aria-label="Close image viewer">✕</button>
  `;
  document.body.appendChild(_lb);

  let _lbImgs = [], _lbIdx = 0, _lbTrigger = null;
  const _lbImg   = _lb.querySelector(".lightbox-img");
  const _lbClose = _lb.querySelector(".lightbox-close");
  const _lbPrev  = _lb.querySelector(".lightbox-prev");
  const _lbNext  = _lb.querySelector(".lightbox-next");

  const openLightbox = (imgs, idx) => {
    _lbImgs = imgs;
    _lbIdx  = idx;
    _lbTrigger = document.activeElement;
    _lbImg.src = imgs[idx].src;
    _lbImg.alt = imgs[idx].alt;
    _lbPrev.hidden = imgs.length <= 1;
    _lbNext.hidden = imgs.length <= 1;
    _lb.classList.add("is-open");
    document.body.style.overflow = "hidden";
    _lbClose.focus();
  };

  const closeLightbox = () => {
    _lb.classList.remove("is-open");
    document.body.style.overflow = "";
    if (_lbTrigger) _lbTrigger.focus();
  };

  const navigateLightbox = (dir) => {
    _lbIdx = (_lbIdx + dir + _lbImgs.length) % _lbImgs.length;
    _lbImg.src = _lbImgs[_lbIdx].src;
    _lbImg.alt = _lbImgs[_lbIdx].alt;
  };

  _lb.querySelector(".lightbox-backdrop").addEventListener("click", closeLightbox);
  _lbClose.addEventListener("click", closeLightbox);
  _lbPrev.addEventListener("click", () => navigateLightbox(-1));
  _lbNext.addEventListener("click", () => navigateLightbox(1));

  document.addEventListener("keydown", (e) => {
    if (!_lb.classList.contains("is-open")) return;
    if (e.key === "Escape") { e.preventDefault(); closeLightbox(); }
    if (e.key === "ArrowLeft")  navigateLightbox(-1);
    if (e.key === "ArrowRight") navigateLightbox(1);
  });

  /* ==================== Initial page setup ==================== */
  setActiveNav(location.href);
  setupFooter();

  const afterLayout = () => {
    updatePill(false);
    if (aboutMorph) aboutMorph.measure();
    updateHeaderContrast();
  };
  window.addEventListener("resize", () => {
    if (aboutMorph) aboutMorph.measure(true);
  });

  initPageFeatures();

  if (document.fonts && document.fonts.status !== "loaded") {
    document.fonts.ready.then(afterLayout);
  } else {
    afterLayout();
  }
  window.addEventListener("load", afterLayout);
});

window.addEventListener("pageshow", () => {
  document.body.classList.remove("exit");
});
