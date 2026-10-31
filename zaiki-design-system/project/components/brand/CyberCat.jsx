import React from "react";
const { useRef, useEffect, useState } = React;

/* The Zaiki interactive Cyber Cat mascot. SVG geometry lifted verbatim from
   the portfolio's footer cat. Eyes track the cursor; hover = happy, click
   cycles playful moods, spam-clicking makes it flee with squash & stretch.
   Styles are injected once (component is self-contained). */

const CAT_CSS = `
.zcat-btn{background:none;border:none;padding:0;cursor:pointer;line-height:0;width:var(--zcat-size,120px);transform-origin:bottom center;}
.zcat-svg{width:100%;height:auto;display:block;overflow:visible;transition:filter .3s ease;}
.zcat .cat-fur{fill:#423b4f;}
.zcat .cat-ear-in{fill:var(--highlight,#a754ff);}
.zcat .cat-eye-white{fill:#fff;}
.zcat .cat-pupil{fill:#1c1c1c;}
.zcat .cat-nose{fill:#db68d7;}
.zcat .cat-mouth{fill:none;stroke:rgba(255,255,255,.85);stroke-width:2;stroke-linecap:round;stroke-linejoin:round;}
.zcat .cat-brow{stroke:#fff;stroke-width:2.6;stroke-linecap:round;}
.zcat .cat-eye-happy{fill:none;stroke:#fff;stroke-width:3;stroke-linecap:round;}
.zcat .cat-blush{fill:rgba(255,130,180,.65);}
.zcat .cat-whisker{stroke:rgba(255,255,255,.3);stroke-width:1.2;stroke-linecap:round;}
.zcat .cat-sweat{fill:#7fd4ff;opacity:0;}
.zcat .cat-eye{transform-box:fill-box;transform-origin:center;transition:transform .22s cubic-bezier(.34,1.56,.64,1);}
.zcat .cat-eye-happy,.zcat .cat-brow,.zcat .cat-blush,.zcat .mouth-happy,.zcat .mouth-angry,.zcat .mouth-annoyed{opacity:0;transition:opacity .2s ease;}
.zcat .mouth-neutral{opacity:1;transition:opacity .2s ease;}
.zcat .cat-pupil-wrap{transform-box:fill-box;transform-origin:center;transform:scale(1.2);transition:transform .5s cubic-bezier(.34,1.56,.64,1);}
.zcat.cat--happy .cat-eye{opacity:0;}
.zcat.cat--happy .cat-eye-happy,.zcat.cat--happy .cat-blush{opacity:1;}
.zcat.cat--happy .mouth-neutral{opacity:0;}
.zcat.cat--happy .mouth-happy{opacity:1;}
.zcat.cat--angry .cat-brow{opacity:1;}
.zcat.cat--angry .cat-eye{transform:scaleY(.68);}
.zcat.cat--angry .mouth-neutral{opacity:0;}
.zcat.cat--angry .mouth-angry{opacity:1;}
.zcat.cat--annoyed .cat-eye{transform:scaleY(.5);}
.zcat.cat--annoyed .cat-pupil-wrap{transform:scaleX(.35) scaleY(1.1)!important;}
.zcat.cat--annoyed .mouth-neutral{opacity:0;}
.zcat.cat--annoyed .mouth-annoyed{opacity:1;}
.zcat.cat--react .cat-eye{transform:scale(1.16);}
.zcat.cat--react .cat-sweat{opacity:1;}
.zcat.cat--react .mouth-neutral{opacity:0;}
.zcat.cat--react .mouth-annoyed{opacity:1;}
.zcat.cat--flee{animation:zcatFlee 3.4s cubic-bezier(.45,0,.55,1) forwards!important;pointer-events:none;}
@keyframes zcatFlee{0%{transform:translate(0,0) scale(1,1);}6%{transform:translate(-3%,4%) scale(1.3,.7);}20%{transform:translate(22vw,0) scale(1.32,.68);}44%{transform:translate(122vw,-9vh) scale(.85,1.15);}72%{transform:translate(122vw,0) scale(1,1);}90%{transform:translate(14vw,-8vh) scale(.85,1.15);}100%{transform:translate(0,0) scale(1,1);}}
@media (prefers-reduced-motion: reduce){.zcat .cat-eye,.zcat .cat-pupil-wrap{transition:none;}.zcat.cat--flee{animation:none;}}
`;

function useInjectCss() {
  useEffect(() => {
    if (document.getElementById("zcat-styles")) return;
    const s = document.createElement("style");
    s.id = "zcat-styles";
    s.textContent = CAT_CSS;
    document.head.appendChild(s);
  }, []);
}

export function CyberCat({ size = 120, hint = "Meow!" }) {
  useInjectCss();
  const btnRef = useRef(null);
  const pupilRefs = useRef([]);
  const [mood, setMood] = useState("");
  const clicks = useRef(0);
  const moodTimer = useRef(null);
  const clickTimer = useRef(null);

  // eyes follow cursor
  useEffect(() => {
    const onMove = (e) => {
      const btn = btnRef.current;
      if (!btn || mood === "flee") return;
      const r = btn.getBoundingClientRect();
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height / 2;
      const ang = Math.atan2(e.clientY - cy, e.clientX - cx);
      const dx = Math.cos(ang) * 2.2;
      const dy = Math.sin(ang) * 2.2;
      pupilRefs.current.forEach((p) => { if (p) p.style.transform = `translate(${dx}px, ${dy}px)`; });
    };
    window.addEventListener("mousemove", onMove);
    return () => window.removeEventListener("mousemove", onMove);
  }, [mood]);

  const tempMood = (cls, ms) => {
    setMood(cls);
    clearTimeout(moodTimer.current);
    moodTimer.current = setTimeout(() => setMood((m) => (m === cls ? "" : m)), ms);
  };

  const onEnter = () => { if (mood !== "flee") setMood("happy"); };
  const onLeave = () => { if (mood === "happy") setMood(""); };

  const onClick = () => {
    if (mood === "flee") return;
    clicks.current += 1;
    clearTimeout(clickTimer.current);
    clickTimer.current = setTimeout(() => { clicks.current = 0; }, 900);
    if (clicks.current >= 4) {
      clicks.current = 0;
      setMood("flee");
      setTimeout(() => setMood(""), 3400);
      return;
    }
    const moods = ["angry", "annoyed", "react"];
    tempMood(moods[Math.floor(Math.random() * moods.length)], 700);
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "0.55rem" }}>
      <button
        ref={btnRef}
        className={`footer-cat zcat zcat-btn${mood ? " cat--" + mood : ""}`}
        style={{ "--zcat-size": size + "px" }}
        type="button"
        aria-label="Pet the cat"
        title="Don't click me!"
        onMouseEnter={onEnter}
        onMouseLeave={onLeave}
        onClick={onClick}
      >
        <svg className="footer-cat-svg zcat-svg" viewBox="0 0 100 100" aria-hidden="true">
          <path className="cat-fur" d="M24 40 L17 15 L43 30 Z" />
          <path className="cat-fur" d="M76 40 L83 15 L57 30 Z" />
          <path className="cat-ear-in" d="M26 38 L21 21 L39 31 Z" />
          <path className="cat-ear-in" d="M74 38 L79 21 L61 31 Z" />
          <ellipse className="cat-fur cat-head" cx="50" cy="58" rx="35" ry="31" />
          <g className="cat-whiskers">
            <line className="cat-whisker" x1="7" y1="56" x2="26" y2="58" />
            <line className="cat-whisker" x1="7" y1="64" x2="26" y2="63" />
            <line className="cat-whisker" x1="93" y1="56" x2="74" y2="58" />
            <line className="cat-whisker" x1="93" y1="64" x2="74" y2="63" />
          </g>
          <circle className="cat-blush" cx="28" cy="64" r="5.5" />
          <circle className="cat-blush" cx="72" cy="64" r="5.5" />
          <g className="cat-eye cat-eye-l">
            <ellipse className="cat-eye-white" cx="37" cy="54" rx="8" ry="10" />
            <g className="cat-pupil-wrap"><circle ref={(el) => (pupilRefs.current[0] = el)} className="cat-pupil" cx="37" cy="55" r="4" /></g>
          </g>
          <g className="cat-eye cat-eye-r">
            <ellipse className="cat-eye-white" cx="63" cy="54" rx="8" ry="10" />
            <g className="cat-pupil-wrap"><circle ref={(el) => (pupilRefs.current[1] = el)} className="cat-pupil" cx="63" cy="55" r="4" /></g>
          </g>
          <path className="cat-eye-happy" d="M30 56 q7 -8 14 0" />
          <path className="cat-eye-happy" d="M56 56 q7 -8 14 0" />
          <path className="cat-brow" d="M28 41 L45 47" />
          <path className="cat-brow" d="M72 41 L55 47" />
          <path className="cat-nose" d="M46 62 H54 L50 67 Z" />
          <path className="cat-mouth mouth-neutral" d="M50 67 q-5 5 -10 1 M50 67 q5 5 10 1" />
          <path className="cat-mouth mouth-happy" d="M38 68 q12 11 24 0" />
          <path className="cat-mouth mouth-angry" d="M40 74 q10 -7 20 0" />
          <path className="cat-mouth mouth-annoyed" d="M 42 70 L 58 70" />
          <path className="cat-sweat" d="M84 39 q-4 7 0 11 q4 -4 0 -11 Z" />
        </svg>
      </button>
      {hint && <span style={{ fontSize: "0.72rem", color: "var(--text-faint)", letterSpacing: "0.05em" }}>{hint}</span>}
    </div>
  );
}
