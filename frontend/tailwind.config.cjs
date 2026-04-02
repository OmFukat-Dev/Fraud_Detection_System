module.exports = {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        display: ["Space Grotesk", "sans-serif"],
        mono: ["IBM Plex Mono", "monospace"]
      },
      colors: {
        ink: "#0d1117",
        cloud: "#f5f7fb",
        electric: "#3f8cff",
        ember: "#ff6b6b",
        moss: "#2dd4bf",
        graphite: "#1f2937"
      },
      boxShadow: {
        glow: "0 0 40px rgba(63, 140, 255, 0.25)"
      }
    }
  },
  plugins: []
}
