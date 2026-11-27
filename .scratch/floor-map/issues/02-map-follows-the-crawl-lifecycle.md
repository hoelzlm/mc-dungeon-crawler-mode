# 02: Map follows the Crawl's lifecycle

**What to build:** A Crawler has one **Floor Map** per **Floor**. It survives rejoining the server, is hidden inside the **Safe Room** and is shown unchanged on return, starts fresh on each new **Floor**, is deleted when its **Floor** collapses and when a fresh **Crawl** starts. An eliminated **Crawler** loses the overlay entirely. The **Floor Timer** is unaffected.

**Blocked by:** 01

**Status:** in-review

- [ ] Rejoining keeps the current Floor's map
- [ ] The overlay is hidden in the Safe Room, and the same map is shown on returning to that Floor
- [x] Descending to a new Floor starts an empty map
- [x] A collapsed Floor's maps are deleted with it, and a fresh Crawl starts with none
- [ ] An eliminated Crawler sees no map overlay
- [x] Engine tests cover each rule through the Crawl engine seam

**Note (review):** Maps live in `CrawlEngine` memory. Rejoining within a server session keeps them; a server restart loses them, as does every other piece of Crawl state today (no persistence mechanism exists yet, eliminated/winners/Floor of each Crawler are also in memory only). Persisting the Crawl is a separate feature and the maps should join it then.
