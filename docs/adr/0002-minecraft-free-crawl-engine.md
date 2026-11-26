# Minecraft-free Crawl engine behind a single test seam

The **Crawl** rules (**Apocalypse**, **Floor Timer**, **Descent Window**, **Elimination**, **Achievements**, **Loot Boxes**) live in a core module with no Minecraft dependency, driven by ticks and events and emitting effects, with a thin NeoForge adapter around it. We chose this over testing everything through GameTest because 90-minute timers and the ten-minute window are impractical to test in a live server. The cost is an adapter layer that must stay thin.
