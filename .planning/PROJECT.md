# EDEN

## What This Is

A plant care companion app where users identify plants via AI, add them to a personal collection, chat with them based on customizable personalities, and track care to build emotional connection. Completely private—no accounts, no cloud sync, all data local.

## Core Value

Users build real emotional connection with virtual plant companions through personalized conversations and consistent care tracking.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Plant identification via camera (TensorFlow Lite on-device + PlantNet API fallback)
- [ ] Plant collection management with customizable 8-type personality system
- [ ] Chat system with context-aware responses based on personality
- [ ] Care tracking (watering, fertilizing, checking) with mood/metrics updates
- [ ] Plant health diagnosis via photo analysis with treatment plans
- [ ] Local-only storage with optional JSON export for sharing
- [ ] Green monochromatic minimalist UI with 5-tab bottom navigation

### Out of Scope

- Cloud sync — all data stays on device
- User accounts/authentication
- Monetization/in-app purchases
- Real-time notifications (local reminders only)
- Social features beyond optional JSON export

## Context

- Platform: Mobile app (start with single platform, likely iOS or Android)
- AI: On-device TensorFlow Lite for identification and diagnosis
- API: PlantNet API for cloud fallback identification
- Storage: Local device only (SQLite or similar)
- Design: Green monochromatic, no emoji, minimalist typography-driven

## Constraints

- **Privacy**: No cloud storage, no accounts, no data leaves device unless explicitly exported
- **Offline**: Core features work without internet (PlantNet fallback requires connectivity)
- **Design**: Green-only palette (shades of green, white, gray), no emoji

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Local-only storage | Privacy-first, no server costs, offline-first | — Pending |
| 8 personality types | Provides variety without overwhelming complexity | — Pending |
| Template-based chat | Simpler than LLM, predictable personality responses | — Pending |

---
*Last updated: 2026-03-04 after initialization*
