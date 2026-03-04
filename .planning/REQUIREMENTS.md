# Requirements: EDEN

**Defined:** 2026-03-04
**Core Value:** Users build real emotional connection with virtual plant companions through personalized conversations and consistent care tracking.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Plant Identification

- [ ] **ID-01**: User can take a photo of a plant using the camera
- [ ] **ID-02**: App identifies plant using TensorFlow Lite on-device ML model
- [ ] **ID-03**: App displays identification confidence percentage
- [ ] **ID-04**: If on-device fails, app queries PlantNet API as cloud fallback
- [ ] **ID-05**: User can confirm or reject the identification result
- [ ] **ID-06**: User can manually add a plant without photo (with defaults)

### Plant Collection

- [ ] **COL-01**: User can add confirmed plant to their personal collection
- [ ] **COL-02**: Each plant has a default Neutral personality on creation
- [ ] **COL-03**: User can change plant personality to one of 8 types (Neutral, Cheerful, Sarcastic, Dramatic, Stoic, Anxious, Wise, Needy)
- [ ] **COL-04**: User can view list of all plants in collection
- [ ] **COL-05**: User can edit plant details (name, species, personality)
- [ ] **COL-06**: User can delete a plant from collection

### Plant Chat

- [ ] **CHAT-01**: User can select a plant to chat with
- [ ] **CHAT-02**: User can type and send messages to the plant
- [ ] **CHAT-03**: Plant responds based on its personality type
- [ ] **CHAT-04**: Responses are context-aware (greeting, care action, health question)
- [ ] **CHAT-05**: Chat history persists locally for each plant
- [ ] **CHAT-06**: User can clear chat history for a plant

### Care Tracking

- [ ] **CARE-01**: User can log watering action for a plant
- [ ] **CARE-02**: User can log fertilizing action for a plant
- [ ] **CARE-03**: User can log "checking on" action for a plant
- [ ] **CARE-04**: Plant mood updates based on care actions
- [ ] **CARE-05**: App displays health metrics (water%, light%, nutrition%)
- [ ] **CARE-06**: App tracks care streaks per plant
- [ ] **CARE-07**: User can view care history per plant

### Health Diagnosis

- [ ] **DIAG-01**: User can upload photo of plant problem
- [ ] **DIAG-02**: AI analyzes photo and provides diagnosis
- [ ] **DIAG-03**: App shows confidence percentage for diagnosis
- [ ] **DIAG-04**: App shows severity level (low/medium/high)
- [ ] **DIAG-05**: App provides step-by-step treatment plan
- [ ] **DIAG-06**: App shows technical observations about the issue

### Profile & Settings

- [ ] **PROF-01**: User can view app settings
- [ ] **PROF-02**: User can export all data as JSON file
- [ ] **PROF-03**: User can import data from JSON file
- [ ] **PROF-04**: All data stored locally (no accounts, no cloud sync)

### Navigation

- [ ] **NAV-01**: Bottom navigation with 5 tabs: Home, Identify, My Plants, Chat, Profile
- [ ] **NAV-02**: Home tab shows overview of all plants
- [ ] **NAV-03**: Identify tab opens camera for plant identification
- [ ] **NAV-04**: My Plants tab shows collection list
- [ ] **NAV-05**: Chat tab opens chat with selected plant
- [ ] **NAV-06**: Profile tab shows settings

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Advanced Features

- **DIAG-02**: Advanced diagnosis with multiple possible conditions
- **CHAT-07**: Voice input for chat messages
- **CARE-08**: Photo-based care logging (show plant growth over time)
- **COL-07**: Plant groups/rooms for organizing collection

### Notifications

- **NOTF-01**: Local push notifications for care reminders
- **NOTF-02**: Streak reminder notifications

### Social

- **SOC-01**: Share plant profile as image
- **SOC-02**: Export plant story as shareable format

## Out of Scope

| Feature | Reason |
|---------|--------|
| Cloud sync | Privacy-first, local-only by design |
| User accounts/authentication | Privacy-first, no server needed |
| Monetization/in-app purchases | v1 focused on core experience |
| Real-time notifications | Only local reminders in v1 |
| Social features beyond JSON export | v1 focused on personal experience |
| Multiple device support | Local-only means single device |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| ID-01 | Phase 1 | Pending |
| ID-02 | Phase 1 | Pending |
| ID-03 | Phase 1 | Pending |
| ID-04 | Phase 1 | Pending |
| ID-05 | Phase 1 | Pending |
| ID-06 | Phase 1 | Pending |
| COL-01 | Phase 1 | Pending |
| COL-02 | Phase 1 | Pending |
| COL-03 | Phase 2 | Pending |
| COL-04 | Phase 1 | Pending |
| COL-05 | Phase 1 | Pending |
| COL-06 | Phase 1 | Pending |
| CHAT-01 | Phase 2 | Pending |
| CHAT-02 | Phase 2 | Pending |
| CHAT-03 | Phase 2 | Pending |
| CHAT-04 | Phase 2 | Pending |
| CHAT-05 | Phase 2 | Pending |
| CHAT-06 | Phase 2 | Pending |
| CARE-01 | Phase 2 | Pending |
| CARE-02 | Phase 2 | Pending |
| CARE-03 | Phase 2 | Pending |
| CARE-04 | Phase 2 | Pending |
| CARE-05 | Phase 2 | Pending |
| CARE-06 | Phase 2 | Pending |
| CARE-07 | Phase 2 | Pending |
| DIAG-01 | Phase 2 | Pending |
| DIAG-02 | Phase 2 | Pending |
| DIAG-03 | Phase 2 | Pending |
| DIAG-04 | Phase 2 | Pending |
| DIAG-05 | Phase 2 | Pending |
| DIAG-06 | Phase 2 | Pending |
| PROF-01 | Phase 1 | Pending |
| PROF-02 | Phase 1 | Pending |
| PROF-03 | Phase 1 | Pending |
| PROF-04 | Phase 1 | Pending |
| NAV-01 | Phase 1 | Pending |
| NAV-02 | Phase 1 | Pending |
| NAV-03 | Phase 1 | Pending |
| NAV-04 | Phase 1 | Pending |
| NAV-05 | Phase 2 | Pending |
| NAV-06 | Phase 1 | Pending |

**Coverage:**
- v1 requirements: 40 total
- Mapped to phases: 40
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-04*
*Last updated: 2026-03-04 after initial definition*
