# 02: Map follows the Crawl's lifecycle

**What to build:** A Crawler has one **Floor Map** per **Floor**. It survives rejoining the server, is hidden inside the **Safe Room** and is shown unchanged on return, starts fresh on each new **Floor**, is deleted when its **Floor** collapses and when a fresh **Crawl** starts. An eliminated **Crawler** loses the overlay entirely. The **Floor Timer** is unaffected.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] Rejoining keeps the current Floor's map
- [ ] The overlay is hidden in the Safe Room, and the same map is shown on returning to that Floor
- [ ] Descending to a new Floor starts an empty map
- [ ] A collapsed Floor's maps are deleted with it, and a fresh Crawl starts with none
- [ ] An eliminated Crawler sees no map overlay
- [ ] Engine tests cover each rule through the Crawl engine seam
