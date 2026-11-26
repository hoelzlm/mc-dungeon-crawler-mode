# 04: Safe Room Doors on every Floor

**What to build:** Every Floor (1 to 3) gets at least four visible Safe Room Doors, spread apart across the Floor and never in or near a Boss area. Being near a Stairwell is allowed. Doors stay usable during the Descent Window. This replaces the placeholder door from ticket 02.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] The floor generation planner places at least four doors per Floor, for all three Floors
- [ ] Doors keep a minimum spacing and are spread rather than clustered
- [ ] No door sits in or near a Boss area
- [ ] Doors work throughout the Floor Timer, including the Descent Window
- [ ] The placeholder door is gone
- [ ] Planner tests cover count, spread and Boss exclusion
