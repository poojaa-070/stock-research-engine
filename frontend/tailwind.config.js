/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        ledger: {
          bg: "#0F1B2A",
          panel: "#152538",
          line: "#2A3B4E",
          ink: "#E7EAEE",
          faint: "#8FA1B3",
          gold: "#C9A227",
        },
        signal: {
          strong: "#3FA96B",
          watch: "#D0A526",
          risk: "#C9622E",
          weak: "#B4452F",
        },
      },
      fontFamily: {
        display: ["'Spectral'", "serif"],
        body: ["'IBM Plex Sans'", "sans-serif"],
        mono: ["'IBM Plex Mono'", "monospace"],
      },
    },
  },
  plugins: [],
};
