# 05: Descent Window, sealed Stairwell and collapse

**What to build:** A stub **Stairwell** is visibly sealed and shows an "opens in mm:ss" hint until the **Descent Window** (last ten minutes) opens, when **The Host** announces it. Using it moves the **Crawler** to a stub next **Floor**. At zero the **Floor** collapses and kills everyone still on it. The next **Floor**'s timer starts at that collapse.

**Blocked by:** 04

**Status:** ready-for-agent

- [ ] Stairwell rejects use before the window
- [ ] Hint and Host announcement appear
- [ ] Descending in the window moves the Crawler and they survive the collapse
- [ ] Those left behind die at the collapse
- [ ] Next Floor's timer starts at the previous collapse, not on arrival
