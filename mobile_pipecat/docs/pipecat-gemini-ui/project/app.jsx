// app.jsx — Pipecat voice assistant UI
// Original design — not a recreation of Gemini. Deep vermilion accent.

const { useState, useEffect, useRef } = React;

// ─── ICONS ────────────────────────────────────────────────────────────
const Icon = {
  Mic: ({ size = 20, color = 'currentColor' }) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <rect x="9" y="3" width="6" height="12" rx="3" fill={color}/>
      <path d="M5 11a7 7 0 0 0 14 0M12 18v3" stroke={color} strokeWidth="2" strokeLinecap="round"/>
    </svg>
  ),
  Wave: ({ size = 20, color = 'currentColor' }) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M4 12v0M8 8v8M12 4v16M16 8v8M20 12v0" stroke={color} strokeWidth="2.2" strokeLinecap="round"/>
    </svg>
  ),
  Plus: ({ size = 20, color = 'currentColor' }) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M12 5v14M5 12h14" stroke={color} strokeWidth="2" strokeLinecap="round"/>
    </svg>
  ),
  Stop: ({ size = 16, color = '#fff' }) => (
    <svg width={size} height={size} viewBox="0 0 16 16" fill={color}>
      <rect x="4" y="4" width="8" height="8" rx="1.5"/>
    </svg>
  ),
  Settings: ({ size = 22, color = 'currentColor' }) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="3" stroke={color} strokeWidth="1.8"/>
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.6 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9c.36.15.68.4.92.72.24.32.4.7.45 1.09L20.91 11A2 2 0 0 1 21 13h-.09a1.65 1.65 0 0 0-1.51 1z" stroke={color} strokeWidth="1.6"/>
    </svg>
  ),
  History: ({ size = 22, color = 'currentColor' }) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M3 12a9 9 0 1 0 3-6.7" stroke={color} strokeWidth="1.8" strokeLinecap="round"/>
      <path d="M3 4v5h5M12 7v5l3 2" stroke={color} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  ),
  Sparkle: ({ size = 14, color = 'currentColor' }) => (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none">
      <path d="M8 1.5l1.3 4.2 4.2 1.3-4.2 1.3L8 12.5 6.7 8.3 2.5 7l4.2-1.3L8 1.5z" fill={color}/>
    </svg>
  ),
  Arrow: ({ size = 18, color = 'currentColor' }) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M5 12h14M13 6l6 6-6 6" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    </svg>
  ),
};

// ─── PIPECAT WORDMARK ─────────────────────────────────────────────────
function Wordmark({ accent = '#C73E1D', mono = '#0E0B0A' }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <div style={{
        width: 22, height: 22, position: 'relative', flexShrink: 0,
      }}>
        <svg width="22" height="22" viewBox="0 0 22 22">
          <rect x="0" y="0" width="22" height="22" rx="5" fill={accent}/>
          <rect x="5" y="5" width="4" height="12" fill="#fff"/>
          <rect x="13" y="5" width="4" height="6" fill="#fff"/>
        </svg>
      </div>
      <span style={{
        fontFamily: 'Geist, ui-sans-serif, system-ui', fontWeight: 600,
        fontSize: 17, letterSpacing: '-0.01em', color: mono,
      }}>pipecat</span>
    </div>
  );
}

// ─── COLOR THEMES ─────────────────────────────────────────────────────
const themes = {
  dark: {
    bg: '#0E0B0A',
    surface: '#1A1413',
    surfaceElev: '#241B19',
    border: 'rgba(255,255,255,0.08)',
    text: '#F7F1ED',
    textDim: 'rgba(247,241,237,0.6)',
    textFaint: 'rgba(247,241,237,0.36)',
    inputBg: '#1F1816',
    chipBg: 'rgba(255,255,255,0.04)',
    chipBorder: 'rgba(255,255,255,0.08)',
    statusBar: '#fff',
  },
  light: {
    bg: '#FAF6F2',
    surface: '#FFFFFF',
    surfaceElev: '#FFFFFF',
    border: 'rgba(14,11,10,0.08)',
    text: '#0E0B0A',
    textDim: 'rgba(14,11,10,0.6)',
    textFaint: 'rgba(14,11,10,0.36)',
    inputBg: '#FFFFFF',
    chipBg: '#FFFFFF',
    chipBorder: 'rgba(14,11,10,0.1)',
    statusBar: '#0E0B0A',
  },
};

// ─── VOICE VISUALIZATIONS ─────────────────────────────────────────────

// 1. BARS — Classic audio waveform
function VizBars({ accent, active = true, size = 220 }) {
  const N = 32;
  const [t, setT] = useState(0);
  useEffect(() => {
    if (!active) return;
    let raf;
    const loop = () => { setT(performance.now() / 1000); raf = requestAnimationFrame(loop); };
    loop();
    return () => cancelAnimationFrame(raf);
  }, [active]);
  const bars = Array.from({ length: N }, (_, i) => {
    const phase = i / N;
    const base = 0.18 + 0.5 * Math.abs(Math.sin(phase * 5 + t * 3.5));
    const env = Math.sin(phase * Math.PI) ** 1.4;
    const h = active ? base * env : 0.05;
    return h;
  });
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 4, height: size,
      width: '100%', justifyContent: 'center',
    }}>
      {bars.map((h, i) => (
        <div key={i} style={{
          width: 5, height: `${Math.max(4, h * size)}px`, borderRadius: 2,
          background: accent, opacity: 0.6 + h * 0.4,
          transition: 'height 80ms linear',
        }}/>
      ))}
    </div>
  );
}

// 2. ORB — Pulsing concentric ring
function VizOrb({ accent, active = true, size = 220 }) {
  const [t, setT] = useState(0);
  useEffect(() => {
    if (!active) return;
    let raf;
    const loop = () => { setT(performance.now() / 1000); raf = requestAnimationFrame(loop); };
    loop();
    return () => cancelAnimationFrame(raf);
  }, [active]);
  const pulse1 = 0.5 + 0.5 * Math.sin(t * 2.2);
  const pulse2 = 0.5 + 0.5 * Math.sin(t * 2.2 + Math.PI / 2);
  const pulse3 = 0.5 + 0.5 * Math.sin(t * 2.2 + Math.PI);
  return (
    <div style={{
      width: size, height: size, position: 'relative',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      {[pulse3, pulse2, pulse1].map((p, i) => (
        <div key={i} style={{
          position: 'absolute',
          width: `${50 + p * 50}%`, height: `${50 + p * 50}%`,
          borderRadius: '50%',
          border: `${2 - i * 0.5}px solid ${accent}`,
          opacity: (1 - p) * 0.7,
        }}/>
      ))}
      <div style={{
        width: '38%', height: '38%', borderRadius: '50%',
        background: accent,
        boxShadow: `0 0 ${20 + pulse1 * 60}px ${accent}`,
        transform: `scale(${0.9 + pulse1 * 0.15})`,
      }}/>
    </div>
  );
}

// 3. PARTICLES — Dot grid that breathes
function VizParticles({ accent, active = true, size = 220 }) {
  const [t, setT] = useState(0);
  useEffect(() => {
    if (!active) return;
    let raf;
    const loop = () => { setT(performance.now() / 1000); raf = requestAnimationFrame(loop); };
    loop();
    return () => cancelAnimationFrame(raf);
  }, [active]);
  const N = 11;
  const dots = [];
  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      const dx = x - (N - 1) / 2;
      const dy = y - (N - 1) / 2;
      const dist = Math.sqrt(dx * dx + dy * dy);
      const wave = Math.sin(dist * 0.9 - t * 3);
      const scale = active ? 0.3 + Math.max(0, wave) * 1.1 : 0.3;
      const op = active ? 0.2 + Math.max(0, wave) * 0.8 : 0.2;
      dots.push({ x, y, scale, op });
    }
  }
  const cell = size / N;
  return (
    <div style={{
      width: size, height: size, position: 'relative',
    }}>
      {dots.map((d, i) => (
        <div key={i} style={{
          position: 'absolute',
          left: d.x * cell + cell / 2 - 4,
          top: d.y * cell + cell / 2 - 4,
          width: 8, height: 8, borderRadius: '50%',
          background: accent, opacity: d.op,
          transform: `scale(${d.scale})`,
          transition: 'transform 80ms linear, opacity 80ms linear',
        }}/>
      ))}
    </div>
  );
}

// ─── STATUS PILL ──────────────────────────────────────────────────────
function StatusPill({ theme, accent, state, latency }) {
  const label = { idle: 'ready', listening: 'listening', thinking: 'thinking', speaking: 'speaking' }[state];
  const dotColor = state === 'idle' ? '#4ADE80' : accent;
  return (
    <div style={{
      display: 'inline-flex', alignItems: 'center', gap: 6,
      padding: '4px 10px 4px 8px', borderRadius: 999,
      background: theme.chipBg, border: `1px solid ${theme.chipBorder}`,
      fontFamily: 'Geist Mono, ui-monospace, monospace',
      fontSize: 10.5, color: theme.textDim, letterSpacing: '0.02em',
    }}>
      <span style={{
        width: 6, height: 6, borderRadius: '50%', background: dotColor,
        boxShadow: state !== 'idle' ? `0 0 8px ${dotColor}` : 'none',
        animation: state !== 'idle' ? 'pulseDot 1.2s ease-in-out infinite' : 'none',
      }}/>
      {label}
      <span style={{ color: theme.textFaint, marginLeft: 4 }}>· {latency}ms</span>
    </div>
  );
}

// ─── APP BAR ──────────────────────────────────────────────────────────
function AppBar({ theme, accent, state }) {
  return (
    <div style={{
      padding: '14px 18px 12px', display: 'flex',
      alignItems: 'center', justifyContent: 'space-between',
      borderBottom: `1px solid ${theme.border}`,
    }}>
      <Wordmark accent={accent} mono={theme.text}/>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <StatusPill theme={theme} accent={accent} state={state} latency={state === 'idle' ? 142 : 218}/>
        <button style={iconBtn(theme)}><Icon.History size={18} color={theme.text}/></button>
      </div>
    </div>
  );
}

const iconBtn = (theme) => ({
  width: 32, height: 32, borderRadius: 10, border: 'none',
  background: theme.chipBg, display: 'flex', alignItems: 'center', justifyContent: 'center',
  color: theme.text, cursor: 'pointer',
});

// ─── ASK BAR (bottom input) ───────────────────────────────────────────
function AskBar({ theme, accent, state, onMicTap, placeholder = 'Ask anything' }) {
  const listening = state === 'listening';
  return (
    <div style={{ padding: '0 14px 14px' }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 10,
        padding: '8px 8px 8px 14px', borderRadius: 28,
        background: theme.inputBg,
        border: `1.5px solid ${listening ? accent : theme.border}`,
        boxShadow: listening
          ? `0 0 0 4px ${accent}22, 0 8px 24px rgba(0,0,0,0.18)`
          : '0 4px 16px rgba(0,0,0,0.12)',
        transition: 'border-color 200ms, box-shadow 200ms',
      }}>
        <Icon.Plus size={20} color={theme.textDim}/>
        <span style={{
          flex: 1, color: theme.textFaint, fontSize: 15,
          fontFamily: 'Geist, ui-sans-serif, system-ui',
        }}>
          {listening ? <span style={{ color: theme.text }}>listening<MicCaret accent={accent}/></span> : placeholder}
        </span>
        <button onClick={onMicTap} style={{
          width: 38, height: 38, borderRadius: '50%', border: 'none',
          background: listening ? accent : theme.chipBg,
          color: listening ? '#fff' : theme.text,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          cursor: 'pointer', transition: 'background 200ms',
          boxShadow: listening ? `0 6px 18px ${accent}66` : 'none',
        }}>
          {listening ? <Icon.Stop/> : <Icon.Mic size={18}/>}
        </button>
        <button style={{
          width: 38, height: 38, borderRadius: '50%', border: 'none',
          background: theme.chipBg, color: theme.text,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          cursor: 'pointer',
        }}>
          <Icon.Wave size={18}/>
        </button>
      </div>
    </div>
  );
}

function MicCaret({ accent }) {
  return (
    <span style={{
      display: 'inline-block', width: 2, height: 14, marginLeft: 2,
      background: accent, verticalAlign: '-2px',
      animation: 'blinkCaret 1s steps(2) infinite',
    }}/>
  );
}

// ─── SUGGESTIONS (Idle) ───────────────────────────────────────────────
function SuggestionGrid({ theme, accent, onPick }) {
  const cards = [
    { tag: 'code', title: 'Explain this Python\ntraceback', icon: '⌘' },
    { tag: 'plan',  title: 'Draft a 5-step\nlaunch checklist' },
    { tag: 'voice', title: 'Practice my\nstandup intro', icon: '◉' },
    { tag: 'data',  title: 'Summarize the\nattached CSV' },
  ];
  return (
    <div style={{
      display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10,
      padding: '0 18px',
    }}>
      {cards.map((c, i) => (
        <button key={i} onClick={() => onPick && onPick(c.title)} style={{
          textAlign: 'left', padding: '14px 14px 16px', borderRadius: 16,
          background: theme.surface, border: `1px solid ${theme.border}`,
          cursor: 'pointer', display: 'flex', flexDirection: 'column',
          gap: 16, minHeight: 110,
        }}>
          <span style={{
            alignSelf: 'flex-start',
            fontFamily: 'Geist Mono, ui-monospace, monospace',
            fontSize: 9.5, letterSpacing: '0.08em', textTransform: 'uppercase',
            padding: '3px 7px', borderRadius: 4,
            background: i === 0 ? accent : theme.chipBg,
            color: i === 0 ? '#fff' : theme.textDim,
          }}>{c.tag}</span>
          <span style={{
            fontSize: 14, lineHeight: 1.35, color: theme.text,
            fontWeight: 500, whiteSpace: 'pre-line',
          }}>{c.title}</span>
        </button>
      ))}
    </div>
  );
}

// ─── CONVERSATION BUBBLES ─────────────────────────────────────────────
function Conversation({ theme, accent }) {
  return (
    <div style={{
      padding: '8px 18px 20px', display: 'flex', flexDirection: 'column', gap: 16,
    }}>
      {/* User bubble */}
      <div style={{ alignSelf: 'flex-end', maxWidth: '82%' }}>
        <div style={{
          padding: '10px 14px', borderRadius: '18px 18px 4px 18px',
          background: accent, color: '#fff', fontSize: 14.5, lineHeight: 1.45,
        }}>
          What's a good way to debounce a websocket reconnect loop in Python?
        </div>
        <div style={{
          textAlign: 'right', marginTop: 4,
          fontFamily: 'Geist Mono, ui-monospace, monospace',
          fontSize: 10, color: theme.textFaint,
        }}>you · 09:32</div>
      </div>

      {/* AI bubble */}
      <div style={{ alignSelf: 'flex-start', maxWidth: '88%' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
          <Icon.Sparkle size={12} color={accent}/>
          <span style={{
            fontFamily: 'Geist Mono, ui-monospace, monospace',
            fontSize: 10, color: theme.textDim, letterSpacing: '0.04em',
          }}>pipecat · gemma-3-27b</span>
        </div>
        <div style={{
          padding: '12px 14px', borderRadius: '18px 18px 18px 4px',
          background: theme.surface, border: `1px solid ${theme.border}`,
          color: theme.text, fontSize: 14.5, lineHeight: 1.5,
        }}>
          Use exponential backoff with jitter. Start at <span style={{
            fontFamily: 'Geist Mono, ui-monospace, monospace', fontSize: 13,
            padding: '1px 5px', borderRadius: 4, background: theme.chipBg,
          }}>1s</span>, double on each failure, cap at ~30s, and add random jitter so reconnecting clients don't thunder the server in lockstep.
        </div>
        {/* Code block */}
        <div style={{
          marginTop: 8, padding: '10px 12px', borderRadius: 12,
          background: theme.bg === '#0E0B0A' ? '#000' : '#0E0B0A',
          fontFamily: 'Geist Mono, ui-monospace, monospace', fontSize: 11.5,
          lineHeight: 1.55, color: '#E5DDD8', overflowX: 'auto',
        }}>
          <div><span style={{ color: '#9AA' }}>delay</span> = <span style={{ color: accent }}>min</span>(cap, base * 2**n)</div>
          <div><span style={{ color: '#9AA' }}>delay</span> += <span style={{ color: accent }}>random</span>.uniform(0, delay)</div>
        </div>
        <div style={{ display: 'flex', gap: 6, marginTop: 10 }}>
          {['Show in editor', 'Explain jitter', 'Try aiohttp variant'].map((s, i) => (
            <span key={i} style={{
              padding: '5px 10px', borderRadius: 999, fontSize: 11.5,
              background: theme.chipBg, border: `1px solid ${theme.chipBorder}`,
              color: theme.textDim, fontFamily: 'Geist, ui-sans-serif, system-ui',
            }}>{s}</span>
          ))}
        </div>
      </div>

      {/* Streaming bubble */}
      <div style={{ alignSelf: 'flex-start', maxWidth: '88%' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
          <Icon.Sparkle size={12} color={accent}/>
          <span style={{
            fontFamily: 'Geist Mono, ui-monospace, monospace',
            fontSize: 10, color: theme.textDim,
          }}>typing<TypingDots accent={accent}/></span>
        </div>
      </div>
    </div>
  );
}

function TypingDots({ accent }) {
  return (
    <span style={{ display: 'inline-flex', gap: 3, marginLeft: 4, verticalAlign: '-1px' }}>
      {[0, 1, 2].map(i => (
        <span key={i} style={{
          width: 4, height: 4, borderRadius: '50%', background: accent,
          animation: `typingBounce 1.1s ease-in-out ${i * 0.15}s infinite`,
        }}/>
      ))}
    </span>
  );
}

// ─── SCREEN: IDLE ─────────────────────────────────────────────────────
function ScreenIdle({ theme, accent }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <AppBar theme={theme} accent={accent} state="idle"/>
      <div style={{ flex: 1, padding: '24px 18px 18px', overflow: 'auto' }}>
        <div style={{
          fontFamily: 'Geist, ui-sans-serif, system-ui',
          fontSize: 28, lineHeight: 1.15, letterSpacing: '-0.02em',
          color: theme.text, fontWeight: 500,
        }}>
          <span style={{ color: theme.textDim }}>Good morning,</span><br/>
          how can I help <span style={{
            background: `linear-gradient(180deg, transparent 60%, ${accent}33 60%)`,
          }}>today</span>?
        </div>
        <div style={{
          marginTop: 8, fontFamily: 'Geist Mono, ui-monospace, monospace',
          fontSize: 10.5, color: theme.textFaint, letterSpacing: '0.04em',
        }}>SESSION · {new Date().toISOString().slice(0, 10).replace(/-/g, '·')}</div>
        <div style={{ marginTop: 22 }}>
          <div style={{
            padding: '0 18px 12px', display: 'flex', alignItems: 'center',
            justifyContent: 'space-between',
          }}>
            <span style={{
              fontFamily: 'Geist Mono, ui-monospace, monospace',
              fontSize: 10.5, letterSpacing: '0.08em',
              textTransform: 'uppercase', color: theme.textFaint,
            }}>Suggestions</span>
            <span style={{
              fontSize: 11.5, color: accent,
              fontFamily: 'Geist, ui-sans-serif, system-ui', fontWeight: 500,
            }}>see all →</span>
          </div>
        </div>
        <div style={{ marginLeft: -18, marginRight: -18 }}>
          <SuggestionGrid theme={theme} accent={accent}/>
        </div>
      </div>
      <AskBar theme={theme} accent={accent} state="idle"/>
    </div>
  );
}

// ─── SCREEN: LISTENING ────────────────────────────────────────────────
function ScreenListening({ theme, accent, vizStyle }) {
  const Viz = { bars: VizBars, orb: VizOrb, particles: VizParticles }[vizStyle] || VizBars;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <AppBar theme={theme} accent={accent} state="listening"/>
      <div style={{
        flex: 1, display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center', padding: '20px',
        position: 'relative',
      }}>
        {/* radial glow */}
        <div style={{
          position: 'absolute', inset: 0,
          background: `radial-gradient(circle at 50% 55%, ${accent}26 0%, transparent 55%)`,
          pointerEvents: 'none',
        }}/>
        <Viz accent={accent} active={true} size={240}/>
        <div style={{
          marginTop: 36, fontFamily: 'Geist, ui-sans-serif, system-ui',
          fontSize: 22, fontWeight: 500, color: theme.text, textAlign: 'center',
          lineHeight: 1.3, letterSpacing: '-0.01em', maxWidth: 280,
        }}>
          "How do I deploy this<br/>to a Raspberry Pi?"
        </div>
        <div style={{
          marginTop: 16, display: 'flex', alignItems: 'center', gap: 8,
          padding: '6px 12px', borderRadius: 999,
          background: theme.chipBg, border: `1px solid ${theme.chipBorder}`,
          fontFamily: 'Geist Mono, ui-monospace, monospace',
          fontSize: 10.5, color: theme.textDim,
        }}>
          <span style={{
            width: 6, height: 6, borderRadius: '50%', background: accent,
            animation: 'pulseDot 1.2s ease-in-out infinite',
          }}/>
          microphone active · whisper-large-v3
        </div>
      </div>
      <AskBar theme={theme} accent={accent} state="listening"/>
    </div>
  );
}

// ─── SCREEN: CONVERSATION ─────────────────────────────────────────────
function ScreenConversation({ theme, accent }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <AppBar theme={theme} accent={accent} state="thinking"/>
      <div style={{ flex: 1, overflow: 'auto' }}>
        <Conversation theme={theme} accent={accent}/>
      </div>
      <AskBar theme={theme} accent={accent} state="idle" placeholder="Reply or ask follow-up"/>
    </div>
  );
}

// ─── FAKE BACKGROUND APP (host app behind overlay) ────────────────────
// A generic "Notes" looking app — placeholder so the overlay has something
// believable to sit on top of. NOT a recreation of any branded app.
function FakeHostApp({ theme, accent }) {
  return (
    <div style={{
      flex: 1, display: 'flex', flexDirection: 'column',
      background: theme.bg, color: theme.text,
      fontFamily: 'Geist, ui-sans-serif, system-ui',
    }}>
      {/* host app header */}
      <div style={{
        padding: '14px 18px 12px',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        borderBottom: `1px solid ${theme.border}`,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{
            width: 26, height: 26, borderRadius: 7, background: '#2E7D6B',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: '#fff', fontSize: 13, fontWeight: 700,
            fontFamily: 'Geist, ui-sans-serif',
          }}>N</div>
          <span style={{ fontSize: 15, fontWeight: 600, color: theme.text }}>Notes</span>
        </div>
        <span style={{ fontSize: 22, color: theme.textDim }}>+</span>
      </div>

      {/* host content — note item */}
      <div style={{ padding: '18px 18px 0', flex: 1, overflow: 'hidden' }}>
        <div style={{
          fontFamily: 'Geist Mono, ui-monospace, monospace',
          fontSize: 10, letterSpacing: '0.08em',
          color: theme.textFaint, textTransform: 'uppercase',
        }}>FRI · 09:31</div>
        <div style={{
          marginTop: 6, fontSize: 22, fontWeight: 600,
          letterSpacing: '-0.01em', color: theme.text, lineHeight: 1.2,
        }}>Pi deployment checklist</div>
        <div style={{
          marginTop: 18, display: 'flex', flexDirection: 'column', gap: 14,
          fontSize: 14, lineHeight: 1.55, color: theme.textDim,
        }}>
          {[
            ['1.', 'Flash latest 64-bit Pi OS to SD card'],
            ['2.', 'Enable SSH + I²S audio in raspi-config'],
            ['3.', 'Install pipecat-ai + uvloop'],
            ['4.', 'Mount mic array, run device probe'],
            ['5.', 'Set systemd unit, point at WebSocket URL'],
            ['6.', 'Test wake-word in noisy room'],
          ].map(([n, txt], i) => (
            <div key={i} style={{ display: 'flex', gap: 12 }}>
              <span style={{
                fontFamily: 'Geist Mono, ui-monospace, monospace',
                color: theme.textFaint, width: 18,
              }}>{n}</span>
              <span style={{ flex: 1, color: i < 3 ? theme.textFaint : theme.text,
                             textDecoration: i < 3 ? 'line-through' : 'none' }}>{txt}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ─── OVERLAY LISTENING BAR ────────────────────────────────────────────
// Floats above a host app at the bottom — system-overlay style.
function OverlayBar({ theme, accent, vizStyle = 'bars' }) {
  // pick a thin viz suited for the bar
  const N = 22;
  const [tick, setTick] = useState(0);
  useEffect(() => {
    let raf;
    const loop = () => { setTick(performance.now() / 1000); raf = requestAnimationFrame(loop); };
    loop();
    return () => cancelAnimationFrame(raf);
  }, []);
  const bars = Array.from({ length: N }, (_, i) => {
    const phase = i / N;
    const base = 0.2 + 0.55 * Math.abs(Math.sin(phase * 5.5 + tick * 4));
    const env = Math.sin(phase * Math.PI) ** 1.2;
    return base * env;
  });

  return (
    <div style={{
      position: 'absolute', left: 12, right: 12, bottom: 12,
      borderRadius: 26, padding: 4,
      background: `${accent}22`,
      boxShadow: `0 0 0 1.5px ${accent}, 0 20px 50px rgba(0,0,0,0.4), 0 6px 16px ${accent}55`,
      backdropFilter: 'blur(20px)',
      WebkitBackdropFilter: 'blur(20px)',
    }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 10,
        padding: '8px 8px 8px 14px', borderRadius: 22,
        background: theme.bg === '#0E0B0A' ? 'rgba(20,16,15,0.92)' : 'rgba(255,255,255,0.92)',
      }}>
        {/* pipecat mark */}
        <div style={{ flexShrink: 0 }}>
          <svg width="22" height="22" viewBox="0 0 22 22">
            <rect x="0" y="0" width="22" height="22" rx="5" fill={accent}/>
            <rect x="5" y="5" width="4" height="12" fill="#fff"/>
            <rect x="13" y="5" width="4" height="6" fill="#fff"/>
          </svg>
        </div>

        {/* mini viz */}
        <div style={{
          flex: 1, display: 'flex', alignItems: 'center', gap: 2.5,
          height: 28, minWidth: 0,
        }}>
          {bars.map((h, i) => (
            <div key={i} style={{
              width: 3, height: `${Math.max(4, h * 28)}px`, borderRadius: 1.5,
              background: accent, opacity: 0.55 + h * 0.45,
              transition: 'height 70ms linear',
              flexShrink: 0,
            }}/>
          ))}
        </div>

        {/* listening label */}
        <span style={{
          fontFamily: 'Geist Mono, ui-monospace, monospace',
          fontSize: 10.5, color: theme.textDim, letterSpacing: '0.04em',
          whiteSpace: 'nowrap',
        }}>listening</span>

        {/* stop button */}
        <button style={{
          width: 36, height: 36, borderRadius: '50%', border: 'none',
          background: accent, color: '#fff', cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          boxShadow: `0 4px 12px ${accent}88`, flexShrink: 0,
        }}>
          <Icon.Stop/>
        </button>
      </div>
    </div>
  );
}

// ─── SCREEN: OVERLAY ──────────────────────────────────────────────────
function ScreenOverlay({ theme, accent, vizStyle }) {
  return (
    <div style={{ position: 'relative', flex: 1, display: 'flex', flexDirection: 'column' }}>
      <FakeHostApp theme={theme} accent={accent}/>
      <OverlayBar theme={theme} accent={accent} vizStyle={vizStyle}/>
    </div>
  );
}

// ─── BUBBLE (floating mini icon) ──────────────────────────────────────
function FloatingBubble({ accent }) {
  const [t, setT] = useState(0);
  useEffect(() => {
    let raf;
    const loop = () => { setT(performance.now() / 1000); raf = requestAnimationFrame(loop); };
    loop();
    return () => cancelAnimationFrame(raf);
  }, []);
  const pulse = 0.5 + 0.5 * Math.sin(t * 2.2);
  return (
    <div style={{
      position: 'absolute', left: 16, bottom: 16,
      width: 56, height: 56,
    }}>
      {/* halo */}
      <div style={{
        position: 'absolute', inset: 0, borderRadius: '50%',
        background: accent, opacity: 0.2 + pulse * 0.15,
        transform: `scale(${1 + pulse * 0.35})`,
        filter: 'blur(1px)',
        pointerEvents: 'none',
      }}/>
      <div style={{
        position: 'absolute', inset: 0, borderRadius: '50%',
        boxShadow: `0 0 0 ${2 + pulse * 6}px ${accent}33`,
        transition: 'none',
      }}/>
      {/* core */}
      <div style={{
        position: 'absolute', inset: 0, borderRadius: '50%',
        background: accent,
        boxShadow: `0 8px 22px ${accent}66, 0 2px 6px rgba(0,0,0,0.3)`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <svg width="26" height="26" viewBox="0 0 22 22">
          <rect x="5" y="5" width="4" height="12" fill="#fff"/>
          <rect x="13" y="5" width="4" height="6" fill="#fff"/>
        </svg>
      </div>
      {/* badge mic-active dot */}
      <div style={{
        position: 'absolute', top: -2, right: -2,
        width: 14, height: 14, borderRadius: '50%',
        background: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center',
        boxShadow: '0 2px 4px rgba(0,0,0,0.25)',
      }}>
        <div style={{
          width: 8, height: 8, borderRadius: '50%', background: accent,
          animation: 'pulseDot 1.2s ease-in-out infinite',
        }}/>
      </div>
    </div>
  );
}

// ─── SCREEN: BUBBLE ───────────────────────────────────────────────────
function ScreenBubble({ theme, accent }) {
  return (
    <div style={{ position: 'relative', flex: 1, display: 'flex', flexDirection: 'column' }}>
      <FakeHostApp theme={theme} accent={accent}/>
      <FloatingBubble accent={accent}/>
    </div>
  );
}

// ─── ROOT EXPORT ──────────────────────────────────────────────────────
Object.assign(window, {
  ScreenIdle, ScreenListening, ScreenConversation,
  ScreenOverlay, ScreenBubble,
  themes,
});
