# Project Research Summary

**Project:** EDEN - Plant Care Companion App
**Domain:** Mobile Plant Care Companion (AI-powered plant identification, care tracking, personality-based chat)
**Researched:** 2026-03-04
**Confidence:** HIGH

## Executive Summary

EDEN is a privacy-first mobile plant care companion app that differentiates through AI-powered plant identification, personality-based plant "companions" with chat functionality, and offline-first architecture. The recommended technology stack uses React Native with Expo for cross-platform mobile development, SQLite + MMKV for local-first storage, TensorFlow Lite for on-device ML inference, and React Navigation for screen management. Key differentiators include a unique 8-type personality system, plant chat with contextual responses, and privacy-first design requiring no accounts or cloud sync.

The primary risks identified are AI identification accuracy (as low as 4% in some studies), template-based chat personality drift, and data loss from local-only storage. Mitigation strategies include confidence scores with identifications, extensive response pools per personality type, and JSON export functionality from day one. The research strongly supports a three-phase roadmap: Foundation (identification, collection, reminders), Emotional Connection (personality, chat, care tracking), and Polish (export, offline refinement).

## Key Findings

### Recommended Stack

**Summary:** React Native with Expo SDK 52+ provides the fastest path to production with managed OTA updates and easy native module access. TypeScript ensures type safety. Zustand handles global client state with minimal boilerplate, while TanStack Query manages server state. Local storage uses SQLite for structured relational data (plants, care logs, chat history) and MMKV for fast key-value storage (preferences, tokens). TensorFlow Lite via react-native-fast-tflite enables on-device ML inference with JSI for near-native performance.

**Core technologies:**
- **React Native 0.76+ (Expo SDK 52+):** Cross-platform framework — 95% code sharing, New Architecture for performance
- **Zustand 5.x:** Lightweight global state — 1KB, simple API, perfect for plant collection
- **SQLite + MMKV:** Local-first storage — ACID-compliant relational + 30x faster key-value
- **TensorFlow Lite:** On-device ML — JSI-powered, zero-copy ArrayBuffers
- **React Navigation 7.x:** Industry-standard routing with bottom tabs support

### Expected Features

**Summary:** The plant care app market serves 68%+ of US households with plants, where 43% lose plants within 6 months due to misinformed care. Table stakes include plant identification, care reminders, collection management, care database, disease diagnosis, and watering schedules. EDEN's key differentiators are the personality system (8 types), plant chat, privacy-first/local-only approach, and offline-first architecture.

**Must have (table stakes):**
- **Plant Identification** — Core discovery via ML model (TFLite) with PlantNet API fallback
- **Plant Collection Management** — CRUD operations, photo storage, naming, organization
- **Care Reminders** — Push notifications for watering, fertilizing schedules
- **Care Information Database** — Species-specific water, light, soil requirements

**Should have (competitive):**
- **Personality System** — 8 personality types assigned to plants, unique market positioning
- **Plant Chat** — Template-based conversational responses with personality context
- **Care Tracking** — Log activities, visual growth timeline
- **Disease Diagnosis** — Photo-based issue identification with confidence scores
- **JSON Export** — Privacy-respecting data portability

**Defer (v2+):**
- **Community Q&A** — Requires social infrastructure, scope creep
- **Light Meter** — Hardware-dependent, niche feature
- **Pot Size Calculator** — Nice-to-have, not core

### Architecture Approach

**Summary:** Clean Architecture with MVVM provides the recommended structure: Presentation Layer (screens, components), ViewModel Layer (UI state management), Domain Layer (use cases, entities), and Data Layer (repositories, SQLite, API clients). The offline-first pattern makes local SQLite the primary data source with PlantNet API as optional fallback. Key patterns include Repository Pattern for data abstraction and use cases for business logic isolation.

**Major components:**
1. **PlantRepository (SQLite):** CRUD operations, local persistence, single source of truth
2. **TFLite Service:** On-device ML inference for identification and disease detection
3. **Chat Engine:** Template-based response generation with personality-driven context
4. **Schedule Service:** Local notifications for care reminders

### Critical Pitfalls

1. **AI Identification Accuracy** — Can be as low as 4% in studies. Always show confidence scores, top 3 matches, include "Not sure" option, add disclaimers, use PlantNet fallback for uncertain identifications.

2. **Template-Based Chat Personality Drift** — Users experience repetitive responses quickly. Design extensive response pools (100+ per personality), implement context memory, add personality-consistent error messages, test 30+ message conversations.

3. **No User Data Backup/Export** — Local-only means data loss on device change. Build JSON export from day one, implement scheduled local backups, add backup reminder notifications.

4. **Misleading Plant Health Diagnosis** — Wrong diagnoses lead to plant death. Always frame as "possible issues," present differential diagnoses, include "consult local nursery" disclaimers, build confidence thresholds.

5. **Local Notifications Not Firing** — OS battery optimization kills background processes. Test extensively, implement notification chains, add test notification feature, re-schedule on app launch.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Foundation - Core Experience
**Rationale:** Validates core value proposition. Identification and collection management are table stakes; users won't engage with personality/chat features without plants to manage.

**Delivers:** Plant identification (TFLite + PlantNet fallback), plant collection CRUD, basic care reminders with local notifications, care database display, SQLite + MMKV storage layer.

**Addresses:** FEATURES.md table stakes — identification, collection, reminders, database

**Avoids:** PITFALLS.md Pitfall 1 (AI accuracy - implement confidence scores, fallbacks), Pitfall 3 (no export - add JSON export early), Pitfall 6 (can't add without photo - allow manual entry)

### Phase 2: Emotional Connection - Differentiators
**Rationale:** EDEN's unique value. Personality system and plant chat create the emotional connection that competitors lack. Depends on Phase 1 collection infrastructure.

**Delivers:** 8-type personality assignment when adding plants, template-based plant chat with context memory, care tracking (log activities, update mood), health diagnosis with confidence thresholds.

**Addresses:** FEATURES.md differentiators — personality system, plant chat, care tracking, disease diagnosis

**Avoids:** PITFALLS.md Pitfall 2 (personality drift - extensive response pools), Pitfall 4 (misleading diagnosis - confidence thresholds, disclaimers), Pitfall 5 (notifications not firing - test, re-schedule), Pitfall 8 (chat loses context - persist history)

### Phase 3: Polish & Platform
**Rationale:** Refines UX, ensures privacy promise is fulfilled, prepares for production. All core features validated in earlier phases.

**Delivers:** JSON export functionality, enhanced offline operation (all features work without network), green monochromatic typography-driven UI refinement, design system enforcement.

**Addresses:** FEATURES.md export feature, privacy-first principle, offline-first architecture

**Avoids:** PITFALLS.md Pitfall 9 (design creep - enforce minimalist constraints), Pitfall 10 (navigation issues - test, support back gesture)

### Phase Ordering Rationale

- **Dependencies drive order:** Can't have plant chat without plants in collection; can't track care without reminders scheduled
- **Core value first:** Identification + collection validates market need before investing in differentiators
- **Differentiation second:** Personality and chat are unique to EDEN — need working collection to attach personalities to
- **Pitfall avoidance built-in:** Each phase explicitly addresses relevant pitfalls to prevent rework

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1:** TensorFlow Lite model integration — need to source/validate plant identification model
- **Phase 2:** Chat template development — personality system needs extensive UX testing

Phases with standard patterns (skip research-phase):
- **Phase 1:** SQLite/React Navigation — well-documented, standard React Native patterns
- **Phase 3:** JSON export, notifications — established APIs, no major unknowns

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | React Native + Expo + SQLite is well-established stack. Multiple official docs and community resources confirm best practices. |
| Features | HIGH | Market research based on App Store analysis, competitor feature sets, and user review patterns. Clear differentiation strategy. |
| Architecture | MEDIUM | Clean Architecture well-documented but React Native-specific implementations vary. Offline-first pattern has known trade-offs. |
| Pitfalls | HIGH | Multiple sources confirm AI accuracy issues, chatbot failure modes, and local storage pitfalls. Real-world user reports validate severity. |

**Overall confidence:** HIGH

### Gaps to Address

- **TensorFlow Lite Model:** Need to source or train plant identification model. Research didn't identify a specific pre-trained model to use — this needs validation during Phase 1 planning.
- **Chat Response Templates:** 100+ responses per personality type is substantial. Need to estimate development effort and potentially reduce scope.
- **Plant Database Coverage:** How many species to seed initially? Need to determine minimum viable database size for launch.

## Sources

### Primary (HIGH confidence)
- React Native 2025 Best Practices (reactnativeexample.com, Rajan's Bytes)
- TensorFlow Lite React Native integration (TensorFlow official docs)
- Offline-first architecture patterns (PowerSync, RxDB articles)
- AI plant identification accuracy studies (New Scientist)

### Secondary (MEDIUM confidence)
- Plant care app market analysis (App Store listings 2026: PictureThis, PlantIn, Planta)
- Chatbot personality drift research (DEV Community, Character AI user reports)
- Clean Architecture mobile implementation (NextNative, Flutter docs)

### Tertiary (LOW confidence)
- Specific TensorFlow Lite model recommendations — needs validation
- Plant database coverage requirements — needs market analysis

---
*Research completed: 2026-03-04*
*Ready for roadmap: yes*
