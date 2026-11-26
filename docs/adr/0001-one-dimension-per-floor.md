# One generated dimension per Floor

Each **Floor** is its own custom dimension, generated once and shared by every **Crawler** on it. We chose this over placing floors in the overworld or as structures because it isolates the **Crawl** from vanilla terrain, lets us set per-floor rules (sealed world, timers, collapse) and makes it easy to delete a **Floor** on collapse. The cost is heavier dimension management and chunked generation work.
