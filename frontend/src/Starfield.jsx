import { useEffect, useRef } from 'react';

/*
 * The background: a galaxy of stars, and every few seconds a streak of light travels from one star to another,
 * money moving somewhere in the universe. When the user actually moves money, call celebrateTransfer() and a
 * brighter golden streak crosses the sky.
 *
 * Canvas, not DOM: a few hundred stars redraw cheaply. Pauses when the tab is hidden; with reduced motion the
 * sky is drawn once and nothing moves.
 */

const EVENT = 'smart-wallet:money-moved';

export function celebrateTransfer() {
  window.dispatchEvent(new Event(EVENT));
}

const STAR_TINTS = ['255,255,255', '214,226,255', '255,236,214', '196,210,255'];
const STREAK = '160,200,255';
const GOLD = '255,206,110';

function random(min, max) {
  return min + Math.random() * (max - min);
}

function gaussian() {
  return (Math.random() + Math.random() + Math.random() + Math.random() - 2) / 2;   // roughly -1..1, peaked at 0
}

/*
 * Scattered stars everywhere, plus a dense band running corner to corner: the galaxy's disc seen edge-on.
 * Band stars are small and many, so the band reads as a glowing river of light rather than separate dots.
 */
function makeStars(width, height) {
  const area = width * height;
  const scattered = Math.min(700, Math.round(area / 3000));
  const band = Math.min(1600, Math.round(area / 900));
  const stars = [];
  const star = (x, y, depth, inBand) => ({
    x, y,
    r: depth > 0.985 ? random(1.3, 2.1) : 0.3 + depth * (inBand ? 0.6 : 0.9),
    base: inBand ? 0.18 + depth * 0.55 : 0.25 + depth * 0.6,
    twinkle: random(0.6, 2.2),
    phase: Math.random() * Math.PI * 2,
    drift: inBand ? 0 : 0.004 + depth * 0.014,
    tint: STAR_TINTS[Math.floor(Math.random() * STAR_TINTS.length)],
  });

  for (let i = 0; i < scattered; i++) {
    stars.push(star(Math.random() * width, Math.random() * height, Math.random(), false));
  }

  // the band: from lower left to upper right
  const x0 = -0.1 * width, y0 = 0.92 * height;
  const x1 = 1.1 * width, y1 = 0.08 * height;
  const length = Math.hypot(x1 - x0, y1 - y0);
  const nx = -(y1 - y0) / length, ny = (x1 - x0) / length;   // perpendicular
  const spread = Math.min(width, height) * 0.16;
  for (let i = 0; i < band; i++) {
    const t = Math.random();
    const off = gaussian() * spread * (0.6 + 0.4 * Math.sin(t * Math.PI));   // fuller in the middle
    stars.push(star(x0 + (x1 - x0) * t + nx * off, y0 + (y1 - y0) * t + ny * off, Math.random() ** 1.6, true));
  }
  return stars;
}

function makeStreak(stars, width, height, golden) {
  const bright = stars.filter((s) => s.r > 0.9);
  const pool = bright.length > 10 ? bright : stars;
  const from = pool[Math.floor(Math.random() * pool.length)];
  let to = pool[Math.floor(Math.random() * pool.length)];
  for (let tries = 0; tries < 20 && Math.hypot(to.x - from.x, to.y - from.y) < Math.min(width, height) * 0.35; tries++) {
    to = pool[Math.floor(Math.random() * pool.length)];
  }
  const mx = (from.x + to.x) / 2;
  const my = (from.y + to.y) / 2;
  const bend = Math.hypot(to.x - from.x, to.y - from.y) * random(0.15, 0.35) * (Math.random() < 0.5 ? -1 : 1);
  const angle = Math.atan2(to.y - from.y, to.x - from.x) + Math.PI / 2;
  return {
    from, to,
    cx: mx + Math.cos(angle) * bend,
    cy: my + Math.sin(angle) * bend,
    t: 0,
    speed: golden ? 0.011 : random(0.006, 0.01),
    color: golden ? GOLD : STREAK,
    width: golden ? 2.6 : 1.6,
    trail: golden ? 0.34 : 0.22,
    arrived: 0,
  };
}

function pointOn(s, t) {
  const u = 1 - t;
  return {
    x: u * u * s.from.x + 2 * u * t * s.cx + t * t * s.to.x,
    y: u * u * s.from.y + 2 * u * t * s.cy + t * t * s.to.y,
  };
}

export default function Starfield() {
  const canvas = useRef(null);

  useEffect(() => {
    const el = canvas.current;
    const ctx = el.getContext('2d');
    const still = window.matchMedia('(prefers-reduced-motion: reduce)');
    let width = 0;
    let height = 0;
    let stars = [];
    let streaks = [];
    let frame = 0;
    let last = performance.now();
    let nextStreak = last + 1500;

    function resize() {
      const ratio = Math.min(window.devicePixelRatio || 1, 2);
      width = window.innerWidth;
      height = window.innerHeight;
      el.width = Math.round(width * ratio);
      el.height = Math.round(height * ratio);
      el.style.width = `${width}px`;
      el.style.height = `${height}px`;
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
      stars = makeStars(width, height);
      streaks = [];
      draw(performance.now(), 0);
    }

    function drawStreak(s) {
      const head = pointOn(s, Math.min(s.t, 1));
      const tailT = Math.max(0, s.t - s.trail);
      const steps = 24;
      for (let i = 0; i < steps; i++) {
        const a = tailT + ((Math.min(s.t, 1) - tailT) * i) / steps;
        const b = tailT + ((Math.min(s.t, 1) - tailT) * (i + 1)) / steps;
        const p = pointOn(s, a);
        const q = pointOn(s, b);
        const fade = s.arrived > 0 ? Math.max(0, 1 - s.arrived) : 1;
        ctx.strokeStyle = `rgba(${s.color},${((i + 1) / steps) * 0.95 * fade})`;
        ctx.lineWidth = s.width * ((i + 1) / steps);
        ctx.beginPath();
        ctx.moveTo(p.x, p.y);
        ctx.lineTo(q.x, q.y);
        ctx.stroke();
      }
      if (s.t < 1) {
        const glow = ctx.createRadialGradient(head.x, head.y, 0, head.x, head.y, s.width * 5);
        glow.addColorStop(0, `rgba(${s.color},0.95)`);
        glow.addColorStop(1, `rgba(${s.color},0)`);
        ctx.fillStyle = glow;
        ctx.beginPath();
        ctx.arc(head.x, head.y, s.width * 5, 0, Math.PI * 2);
        ctx.fill();
      } else {
        // arrival: the receiving star flares once
        const r = s.width * (3 + s.arrived * 10);
        const flare = ctx.createRadialGradient(s.to.x, s.to.y, 0, s.to.x, s.to.y, r);
        flare.addColorStop(0, `rgba(${s.color},${0.8 * (1 - s.arrived)})`);
        flare.addColorStop(1, `rgba(${s.color},0)`);
        ctx.fillStyle = flare;
        ctx.beginPath();
        ctx.arc(s.to.x, s.to.y, r, 0, Math.PI * 2);
        ctx.fill();
      }
    }

    function draw(now, dt) {
      ctx.clearRect(0, 0, width, height);
      const seconds = now / 1000;
      for (const s of stars) {
        if (dt) {
          s.x -= s.drift * dt * 0.06;
          if (s.x < -2) s.x = width + 2;
        }
        const alpha = still.matches ? s.base : s.base * (0.7 + 0.3 * Math.sin(seconds * s.twinkle + s.phase));
        ctx.fillStyle = `rgba(${s.tint},${alpha})`;
        ctx.beginPath();
        ctx.arc(s.x, s.y, s.r, 0, Math.PI * 2);
        ctx.fill();
        if (s.r > 1.2) {
          const halo = ctx.createRadialGradient(s.x, s.y, 0, s.x, s.y, s.r * 5);
          halo.addColorStop(0, `rgba(${s.tint},${alpha * 0.35})`);
          halo.addColorStop(1, `rgba(${s.tint},0)`);
          ctx.fillStyle = halo;
          ctx.beginPath();
          ctx.arc(s.x, s.y, s.r * 5, 0, Math.PI * 2);
          ctx.fill();
        }
      }
      for (const s of streaks) drawStreak(s);
    }

    function tick(now) {
      const dt = Math.min(now - last, 50);
      last = now;
      if (now > nextStreak && streaks.length < 3) {
        streaks.push(makeStreak(stars, width, height, false));
        nextStreak = now + random(2600, 5200);
      }
      for (const s of streaks) {
        if (s.t < 1) s.t = Math.min(1, s.t + s.speed * (dt / 16.7));
        else s.arrived += 0.02 * (dt / 16.7);
      }
      streaks = streaks.filter((s) => s.arrived < 1);
      draw(now, dt);
      frame = requestAnimationFrame(tick);
    }

    function start() {
      cancelAnimationFrame(frame);
      if (still.matches || document.hidden) {
        draw(performance.now(), 0);
        return;
      }
      last = performance.now();
      frame = requestAnimationFrame(tick);
    }

    function onMoneyMoved() {
      if (still.matches) return;
      streaks.push(makeStreak(stars, width, height, true));
    }

    resize();
    start();
    window.addEventListener('resize', resize);
    document.addEventListener('visibilitychange', start);
    still.addEventListener?.('change', start);
    window.addEventListener(EVENT, onMoneyMoved);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('resize', resize);
      document.removeEventListener('visibilitychange', start);
      still.removeEventListener?.('change', start);
      window.removeEventListener(EVENT, onMoneyMoved);
    };
  }, []);

  return (
    <div className="sky" aria-hidden="true">
      <div className="nebula" />
      <div className="milky-way" />
      <canvas ref={canvas} />
    </div>
  );
}
