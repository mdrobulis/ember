/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{cljs,cljc}", "./public/index.html"],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        ink: {
          950: "#07090e",
          900: "#0b0f17",
          850: "#101622",
          800: "#151d2b",
          700: "#202b3d",
          600: "#2d3a50",
        },
        ember: {
          100: "#fef3c7",
          200: "#fde68a",
          300: "#fcd34d",
          400: "#fbbf24",
          500: "#f97316",
          600: "#ea580c",
          700: "#c2410c",
        },
        spark: "#a3e635",
        frost: "#67e8f9",
        rose: "#fb7185",
      },
      fontFamily: {
        display: ['"Space Grotesk"', "ui-sans-serif", "system-ui", "sans-serif"],
        sans: ['"DM Sans"', "ui-sans-serif", "system-ui", "-apple-system", "sans-serif"],
      },
      boxShadow: {
        panel: "0 24px 70px -30px rgba(0, 0, 0, 0.9)",
        glow: "0 0 48px -12px rgba(249, 115, 22, 0.55)",
        "glow-lime": "0 0 40px -12px rgba(163, 230, 53, 0.4)",
        insetline: "inset 0 1px 0 0 rgba(255, 255, 255, 0.06)",
      },
      backgroundImage: {
        "radial-ember":
          "radial-gradient(circle at 30% 0%, rgba(249,115,22,0.2), transparent 55%)",
        "grid-faint":
          "radial-gradient(circle at center, rgba(255,255,255,0.085) 1px, transparent 1px)",
      },
      keyframes: {
        flicker: {
          "0%, 100%": { transform: "translateY(0) scale(1)", opacity: "1" },
          "35%": { transform: "translateY(-1px) scale(1.04, 0.98)", opacity: "0.92" },
          "70%": { transform: "translateY(1px) scale(0.98, 1.03)", opacity: "0.98" },
        },
        float: {
          "0%, 100%": { transform: "translateY(0)" },
          "50%": { transform: "translateY(-5px)" },
        },
        "pop-in": {
          "0%": { transform: "scale(0.94)", opacity: "0" },
          "70%": { transform: "scale(1.02)", opacity: "1" },
          "100%": { transform: "scale(1)", opacity: "1" },
        },
        "slide-up": {
          "0%": { transform: "translateY(14px)", opacity: "0" },
          "100%": { transform: "translateY(0)", opacity: "1" },
        },
        "pulse-ring": {
          "0%": { boxShadow: "0 0 0 0 rgba(249,115,22,0.45)" },
          "70%": { boxShadow: "0 0 0 14px rgba(249,115,22,0)" },
          "100%": { boxShadow: "0 0 0 0 rgba(249,115,22,0)" },
        },
        shimmer: {
          "0%": { backgroundPosition: "200% 0" },
          "100%": { backgroundPosition: "-200% 0" },
        },
        confetti: {
          "0%": { transform: "translate3d(0,0,0) rotate(0deg)", opacity: "1" },
          "100%": { transform: "translate3d(var(--tx), 150px, 0) rotate(var(--rot))", opacity: "0" },
        },
      },
      animation: {
        flicker: "flicker 2.8s ease-in-out infinite",
        float: "float 5s ease-in-out infinite",
        "pop-in": "pop-in 260ms cubic-bezier(0.2, 0.9, 0.3, 1.2) both",
        "slide-up": "slide-up 380ms cubic-bezier(0.2, 0.8, 0.2, 1) both",
        "pulse-ring": "pulse-ring 1.4s ease-out 2",
        shimmer: "shimmer 3s linear infinite",
        confetti: "confetti 900ms ease-out forwards",
      },
    },
  },
  plugins: [],
};
