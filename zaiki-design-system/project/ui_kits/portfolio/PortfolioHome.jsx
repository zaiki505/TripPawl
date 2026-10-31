/* Portfolio homepage UI kit — composes the design-system components from the
   compiled bundle (window.ZaikiDesignSystem_9f1839). Loaded as a plain babel
   script with global React (no module imports). */
const { useState, useEffect } = React;
const { NavPill, Button, BentoCard, ProjectCard, CyberCat } = window.ZaikiDesignSystem_9f1839;

const PROJECTS = [
  {
    title: "Attention Monitoring Detector",
    description: "A machine learning-based solution to monitor user attention and provide actionable insights.",
    tags: [{ label: "Web Development", category: "web" }],
    image: "../../assets/attention-detector/ai-mockup.png",
    gradient: 4,
  },
  {
    title: "House Rental Management System",
    description: "A comprehensive rental property management system developed using C++ and MySQL.",
    tags: [{ label: "System Design", category: "system" }, { label: "Personal Project", category: "personal" }],
    image: "../../assets/HRMS/w1-mockup.jpg",
    gradient: 1,
  },
  {
    title: "CineTrack: A Smart Movie Tracker",
    description: "A command-line movie tracker managing data dynamically using linked lists, with searching and sorting.",
    tags: [{ label: "System Design", category: "system" }],
    image: "../../assets/CineTrack/cli-app-mockup.jpg",
    gradient: 3,
  },
];

function Aurora() {
  const blob = (bg, x, y, s, d) => ({
    position: "absolute", width: s, height: s, borderRadius: "50%",
    background: bg, filter: "blur(70px)", left: x, top: y,
    animation: `heroAurora ${d}s ease-in-out infinite alternate`,
  });
  return (
    <div aria-hidden="true" style={{ position: "absolute", inset: "-20% 0 0", zIndex: 0, pointerEvents: "none" }}>
      <div style={blob("var(--aurora-purple)", "12%", "0%", "42vw", 7)}></div>
      <div style={blob("var(--aurora-pink)", "58%", "10%", "34vw", 9)}></div>
      <div style={blob("var(--aurora-blue)", "38%", "30%", "38vw", 8)}></div>
    </div>
  );
}

function PortfolioHome() {
  const [page, setPage] = useState("Home");
  const [theme, setTheme] = useState("dark");

  useEffect(() => {
    document.body.classList.toggle("light", theme === "light");
    document.body.classList.add("theme-transition");
    const t = setTimeout(() => document.body.classList.remove("theme-transition"), 500);
    return () => clearTimeout(t);
  }, [theme]);

  const scrollTo = (sel) => {
    const el = document.querySelector(sel);
    if (el) window.scrollTo({ top: el.offsetTop - 90, behavior: "smooth" });
  };

  return (
    <div style={{ minHeight: "100vh", fontFamily: "var(--font-mono)" }}>
      <div style={{ position: "fixed", top: "1.4rem", left: "50%", transform: "translateX(-50%)", zIndex: 50, width: "min(94vw, 1160px)" }}>
        <NavPill
          logo="../../assets/logo.jpeg"
          logoText="zaiki's Portfolio"
          active={page === "Home" ? null : page}
          links={[
            { label: "About Me" }, { label: "Education" },
            { label: "Projects" }, { label: "Skills" }, { label: "Contact" },
          ]}
          onNavigate={(label) => {
            setPage(label);
            const map = { "About Me": "#about", Projects: "#projects", Contact: "#contact", Skills: "#about", Education: "#about" };
            if (map[label]) scrollTo(map[label]);
          }}
          onThemeToggle={() => setTheme((t) => (t === "dark" ? "light" : "dark"))}
        />
      </div>

      <main style={{ maxWidth: "min(1120px, 100% - 2rem)", margin: "0 auto", paddingTop: "8.5rem" }}>
        <section style={{ position: "relative", padding: "3rem 0 5rem", textAlign: "center", overflow: "hidden" }}>
          <Aurora />
          <div style={{ position: "relative", zIndex: 1 }}>
            <p style={{ color: "var(--text-muted)", fontSize: "1rem", margin: "0 0 0.5rem" }}>Hi there 👋, I'm</p>
            <h1 style={{ fontSize: "clamp(2.8rem, 8vw, 6.5rem)", fontWeight: 800, lineHeight: 1.02, letterSpacing: "-0.03em", margin: 0 }}>
              Muhd <span className="text-gradient-shimmer">Uzair</span>
            </h1>
            <p style={{ fontSize: "clamp(1rem, 2.5vw, 1.6rem)", fontWeight: 700, margin: "1rem 0 0.4rem" }}>
              Web &amp; App Developer · Designer · Creator
            </p>
            <p style={{ color: "var(--text-body)", maxWidth: "46ch", margin: "0 auto 2rem", lineHeight: 1.6 }}>
              Computing student with a thing for clean, functional interfaces and the code behind them.
            </p>
            <div style={{ display: "flex", gap: "0.9rem", justifyContent: "center", flexWrap: "wrap" }}>
              <Button variant="primary" onClick={() => scrollTo("#projects")}>View My Work</Button>
              <Button variant="secondary" onClick={() => scrollTo("#contact")}>Get in Touch</Button>
            </div>
            <div style={{ marginTop: "3rem", display: "flex", flexDirection: "column", alignItems: "center", gap: "0.4rem", color: "var(--text-faint)", fontSize: "0.72rem" }}>
              <span>Scroll Down</span>
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" style={{ animation: "scrollHintBounce 1.4s ease-in-out infinite" }}>
                <polyline points="6 9 12 15 18 9"></polyline>
              </svg>
            </div>
          </div>
        </section>

        <section id="about" style={{ padding: "3rem 0" }}>
          <h2 className="section-title" style={{ textAlign: "center" }}>About Me</h2>
          <div style={{ display: "grid", gridTemplateColumns: "1.6fr 1fr", gap: "1.25rem", marginTop: "1.5rem" }}>
            <BentoCard main title="Who I Am" linkText="Read full story →" style={{ gridRow: "span 2" }}>
              I'm a passionate developer who loves creating clean and simple designs with functionality in mind. Always eager to learn and work on exciting projects.
            </BentoCard>
            <BentoCard title="Core Tools" chips={["HTML", "CSS", "JavaScript", "C++", "Java", "Git", "MySQL"]} />
            <BentoCard title="Education">
              <strong style={{ color: "var(--text-primary)" }}>BSc. Computer Science</strong>
              <div style={{ color: "var(--text-muted)", marginTop: 2 }}>Interactive Media</div>
            </BentoCard>
          </div>
        </section>

        <section id="projects" style={{ padding: "3rem 0" }}>
          <h2 className="section-title" style={{ textAlign: "center" }}>Featured Projects</h2>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(260px, 1fr))", gap: "1.25rem", marginTop: "1.5rem" }}>
            {PROJECTS.map((p, i) => <ProjectCard key={i} {...p} />)}
          </div>
          <div style={{ textAlign: "center", marginTop: "2rem" }}>
            <Button variant="pill">View All Projects →</Button>
          </div>
        </section>

        <section id="contact" style={{ padding: "3rem 0 4rem" }}>
          <div style={{
            borderRadius: "var(--radius-banner)", padding: "clamp(2rem, 5vw, 3.5rem)", textAlign: "center",
            background: "radial-gradient(circle at 50% 0%, rgba(167,84,255,0.16), transparent 60%), var(--surface-glass-deep)",
            border: "1px solid var(--border-accent)", boxShadow: "var(--shadow-banner)",
            backdropFilter: "blur(12px)", WebkitBackdropFilter: "blur(12px)",
          }}>
            <h2 style={{ fontSize: "clamp(1.5rem, 3.5vw, 2.6rem)", fontWeight: 800, margin: "0 0 0.75rem", lineHeight: 1.15 }}>
              Got an idea or just want to talk tech? <span className="text-gradient">Give a heads up!</span>
            </h2>
            <p style={{ color: "var(--text-body)", maxWidth: "52ch", margin: "0 auto 1.75rem", lineHeight: 1.6 }}>
              Currently heads-down (pun intended) as a Computer Science student — but genuinely open to collaboration ideas and a good conversation.
            </p>
            <div style={{ display: "flex", gap: "0.75rem", justifyContent: "center", flexWrap: "wrap" }}>
              <Button variant="pill">Email Me</Button>
              <Button variant="pill">LinkedIn</Button>
              <Button variant="pill">GitHub</Button>
            </div>
          </div>
        </section>

        <footer style={{ padding: "2.5rem 0 4rem", display: "flex", flexDirection: "column", alignItems: "center", gap: "1.5rem", borderTop: "1px solid var(--border-hairline)" }}>
          <CyberCat size={110} hint="psst — don't click me too much" />
          <small style={{ color: "var(--text-faint)" }}>© 2026 zaiki. All Rights Reserved.</small>
        </footer>
      </main>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<PortfolioHome />);
