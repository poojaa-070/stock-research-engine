import { useEffect, useState } from "react";

const SIZE = 168;
const STROKE = 10;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

function colorFor(score: number) {
  if (score >= 80) return "#3FA96B";
  if (score >= 65) return "#3FA96B";
  if (score >= 50) return "#D0A526";
  if (score >= 35) return "#C9622E";
  return "#B4452F";
}

export default function ScoreRing({ score }: { score: number }) {
  const clamped = Math.max(0, Math.min(100, score));
  const [animated, setAnimated] = useState(0);

  // One deliberate entrance animation on load, not a hover effect —
  // the ring sweeps to the real value once, on mount.
  useEffect(() => {
    const raf = requestAnimationFrame(() => setAnimated(clamped));
    return () => cancelAnimationFrame(raf);
  }, [clamped]);

  const offset = CIRCUMFERENCE - (animated / 100) * CIRCUMFERENCE;
  const color = colorFor(clamped);

  return (
    <div className="relative shrink-0" style={{ width: SIZE, height: SIZE }}>
      <svg width={SIZE} height={SIZE} className="-rotate-90">
        <circle
          cx={SIZE / 2}
          cy={SIZE / 2}
          r={RADIUS}
          fill="none"
          stroke="#2A3B4E"
          strokeWidth={STROKE}
        />
        <circle
          cx={SIZE / 2}
          cy={SIZE / 2}
          r={RADIUS}
          fill="none"
          stroke={color}
          strokeWidth={STROKE}
          strokeLinecap="round"
          strokeDasharray={CIRCUMFERENCE}
          strokeDashoffset={offset}
          style={{ transition: "stroke-dashoffset 1.1s cubic-bezier(0.4, 0, 0.2, 1)" }}
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="font-display text-5xl font-medium tabular leading-none">
          {clamped.toFixed(0)}
        </span>
        <span className="text-ledger-faint text-xs mt-1">out of 100</span>
      </div>
    </div>
  );
}
