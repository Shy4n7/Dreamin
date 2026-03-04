# ROADMAP: EDEN - Plant Care Companion App

**Created:** 2026-03-04  
**Granularity:** Standard  
**Phases:** 3

## Overview

EDEN is a privacy-first plant care companion app where users identify plants via AI, add them to a personal collection, chat with them based on customizable personalities, and track care to build emotional connection. All data stays local.

## Phases

- [ ] **Phase 1: Foundation** - Plant identification, collection management, and core navigation
- [ ] **Phase 2: Emotional Connection** - Personality system, plant chat, care tracking, and health diagnosis
- [ ] **Phase 3: Polish** - Data export, offline refinement, and UI finalization

---

## Phase Details

### Phase 1: Foundation
**Goal:** Users can identify plants and manage their personal collection with basic navigation

**Depends on:** Nothing (first phase)

**Requirements:** ID-01, ID-02, ID-03, ID-04, ID-05, ID-06, COL-01, COL-02, COL-04, COL-05, COL-06, NAV-01, NAV-02, NAV-03, NAV-04, NAV-06, PROF-01, PROF-02, PROF-03, PROF-04

**Success Criteria** (what must be TRUE):
1. User can take a photo of a plant using in-app camera
2. App identifies plant with TensorFlow Lite and displays confidence percentage
3. If on-device fails, app queries PlantNet API as fallback
4. User can confirm/reject identification or manually add plant
5. User can add confirmed plant to personal collection
6. Each plant has default Neutral personality on creation
7. User can view, edit details, and delete plants from collection
8. Bottom navigation works with 5 tabs: Home, Identify, My Plants, Chat, Profile
9. Home tab shows overview of all plants
10. Identify tab opens camera for plant identification
11. My Plants tab shows collection list
12. Profile tab shows settings with JSON export/import

**Plans:** TBD

---

### Phase 2: Emotional Connection
**Goal:** Users can personalize plants with personalities, chat with them, track care, and diagnose health issues

**Depends on:** Phase 1

**Requirements:** COL-03, CHAT-01, CHAT-02, CHAT-03, CHAT-04, CHAT-05, CHAT-06, CARE-01, CARE-02, CARE-03, CARE-04, CARE-05, CARE-06, CARE-07, DIAG-01, DIAG-02, DIAG-03, DIAG-04, DIAG-05, DIAG-06, NAV-05

**Success Criteria** (what must be TRUE):
1. User can change plant personality to any of 8 types (Neutral, Cheerful, Sarcastic, Dramatic, Stoic, Anxious, Wise, Needy)
2. User can select a plant and chat with it
3. Plant responds based on its personality type with context-aware responses
4. Chat history persists locally for each plant
5. User can clear chat history for a plant
6. User can log watering, fertilizing, and "checking on" actions
7. Plant mood updates based on care actions
8. App displays health metrics (water%, light%, nutrition%)
9. App tracks and displays care streaks per plant
10. User can view care history per plant
11. User can upload photo of plant problem for diagnosis
12. AI provides diagnosis with confidence percentage and severity level
13. App shows step-by-step treatment plan for diagnosed issues
14. Chat tab opens chat with selected plant

**Plans:** TBD

---

### Phase 3: Polish
**Goal:** App is production-ready with refined UI and full offline capability

**Depends on:** Phase 2

**Requirements:** (All v1 requirements complete, no new requirements)

**Success Criteria** (what must be TRUE):
1. All core features work without internet connection
2. JSON export generates valid, re-importable data file
3. Green monochromatic UI is consistent across all screens
4. App handles edge cases gracefully (no crashes on empty states, invalid input)
5. Local notifications fire reliably for care reminders
6. App performs smoothly (no lag on collection with 50+ plants)

**Plans:** TBD

---

## Coverage Map

| Phase | Requirements | Count |
|-------|--------------|-------|
| 1 - Foundation | ID-01 through ID-06, COL-01, COL-02, COL-04, COL-05, COL-06, NAV-01, NAV-02, NAV-03, NAV-04, NAV-06, PROF-01, PROF-02, PROF-03, PROF-04 | 20 |
| 2 - Emotional Connection | COL-03, CHAT-01 through CHAT-06, CARE-01 through CARE-07, DIAG-01 through DIAG-06, NAV-05 | 20 |
| 3 - Polish | (Validation phase) | - |

**Total Coverage:** 40/40 requirements mapped ✓

---

## Dependencies

```
Phase 1 (Foundation)
    ↓
Phase 2 (Emotional Connection)
    ↓
Phase 3 (Polish)
```

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation | 0/0 | Not started | - |
| 2. Emotional Connection | 0/0 | Not started | - |
| 3. Polish | 0/0 | Not started | - |

---

*Generated: 2026-03-04*
