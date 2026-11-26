# 06: Collapse while inside the Safe Room

**What to build:** The Floor Timer keeps running while a Crawler is in the Safe Room, and the Safe Room counts as still being on the Floor they entered from. If that Floor collapses while they are inside, they die. A Crawler who leaves and uses a Stairwell inside the Descent Window survives.

**Blocked by:** 02

**Status:** ready-for-agent

- [x] A Crawler inside at collapse is Eliminated like anyone left on the Floor
- [x] Time spent inside does not pause or reset the Floor Timer
- [x] A Crawler who leaves and descends before the collapse survives
- [x] Engine tests cover collapse and Descent Window scenarios with a fake clock
