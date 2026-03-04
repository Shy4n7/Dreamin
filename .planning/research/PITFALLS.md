# Domain Pitfalls: Mobile Plant Care Apps

**Domain:** Mobile plant care companion apps
**Researched:** 2026-03-04
**Project:** EDEN - Plant care companion with AI identification, personalities, and chat

---

## Critical Pitfalls

Mistakes that cause rewrites, user trust destruction, or plant death.

### Pitfall 1: Overtrusting AI Plant Identification Accuracy

**What goes wrong:** Plant identification apps can be as little as 4% accurate in some studies. Users rely on identification to get care instructions, and wrong IDs lead to wrong care advice.

**Why it happens:**
- AI models trained on limited datasets
- Users take poor-quality photos (blurry, wrong angle, not enough of plant)
- Similar-looking species confuse even good models
- Confidence scores not communicated to users

**Consequences:**
- User trusts app, follows wrong care instructions
- Plant dies due to incorrect water/light/fertilizer recommendations
- User loses trust in entire app
- Potential liability if user follows toxic plant identification advice

**Prevention:**
- Always show confidence scores with identifications
- Provide top 3 possible matches, not just one
- Include "Not sure" / "Try again" option prominently
- Add clear disclaimers: "AI identification may be inaccurate"
- Require multiple angles before confirming identification
- Offer PlantNet API fallback for uncertain identifications
- Test model extensively on common houseplants before launch

**Detection:**
- Track user "retry" rates after identifications
- Monitor if users add plants manually after AI identification attempts
- Collect feedback on identification accuracy

**Phase:** Foundation (identification is core feature - must get right early)

---

### Pitfall 2: Template-Based Chat Personality Drift

**What goes wrong:** Template-based chat systems lose personality consistency over time. Users report chatbots "repeat themselves," "lose unique tone," and "forget important details."

**Why it happens:**
- Limited response templates exhaust quickly
- No context memory between sessions
- Personality definitions in description fields only, not behavior
- No escalation path when chat fails

**Consequences:**
- Users get bored or frustrated with repetitive responses
- Emotional connection breaks (core value proposition fails)
- "Uncanny valley" effect - nearly-human but not quite, causes unease
- Users abandon the "companion" concept

**Prevention:**
- Design extensive response pools per personality type (100+ variations minimum)
- Implement context memory (what did we talk about last session?)
- Add personality-consistent error messages, not generic ones
- Include "safe topics" vs "boundary topics" per personality
- Build in explicit personality refresh points
- Test with users for 30+ message conversations before launch
- Allow users to "reset" chat if it goes off-character

**Phase:** Feature Implementation (chat personality is differentiator - needs depth)

---

### Pitfall 3: No User Data Backup/Export Path

**What goes wrong:** Local-only storage means if user loses phone, reinstalls app, or gets new device, all plant data is gone. Users report this as top frustration.

**Why it happens:**
- Privacy-first design prioritizes local storage
- Export features deferred as "nice to have"
- No consideration for device migration scenarios

**Consequences:**
- Users lose months/years of plant collection and care history
- After device loss, users must start from zero
- Users may reluctantly switch to cloud-based alternatives
- Negative reviews: "Lost all my plants when I got a new phone"

**Prevention:**
- Build JSON export from day one (in requirements)
- Implement scheduled local backups to device files
- Add "backup reminder" notifications periodically
- Consider iCloud/Google Drive backup integration (opt-in, not required)
- Make export/import trivial: single tap, clear progress

**Phase:** Foundation (data ownership is core to privacy promise)

---

### Pitfall 4: Misleading Plant Health Diagnosis

**What goes wrong:** AI diagnosis of plant diseases from photos often wrong. Users desperate for help follow bad advice and kill plants.

**Why it happens:**
- Disease identification is harder than species identification
- Multiple diseases can present similarly
- Lighting/photo quality affects analysis
- Training data biased toward specific diseases

**Consequences:**
- User follows wrong treatment, plant dies
- Legal liability in extreme cases
- Trust destruction spreads by word-of-mouth
- App perceived as dangerous, not helpful

**Prevention:**
- ALWAYS frame as "possible issues" not definitive diagnosis
- Require multiple photos (whole plant, close-up of affected area, soil)
- Present differential diagnoses: "Could be A, B, or C"
- Include explicit "Please consult a local nursery" disclaimers
- Never diagnose rare/advanced diseases - stick to common issues
- Build confidence thresholds - don't show results below threshold
- Limit diagnosis to actionable, common problems

**Phase:** Feature Implementation (if included in scope)

---

## Moderate Pitfalls

Mistakes that cause user frustration, abandonment, or poor reviews.

### Pitfall 5: Local Notifications Not Firing

**What goes wrong:** Care reminder notifications don't fire when app is closed/killed, or are significantly delayed (5-30+ minutes).

**Why it happens:**
- OS battery optimization kills background processes
- Android/iOS notification scheduling limitations
- Device restart clears scheduled notifications in some implementations
- User disabled notifications at OS level

**Consequences:**
- Users miss watering/fertilizing reminders
- Plants suffer, defeating app's core purpose
- Users disable all notifications or uninstall
- Reviews: "Reminders don't work"

**Prevention:**
- Test notifications extensively on target OS versions
- Implement notification chains (remind again if not dismissed)
- Add "test notification" feature so users can verify it works
- Educate users on allowing background processes
- Consider repeating notifications for critical care
- Re-schedule all notifications on app launch

**Phase:** Feature Implementation (notifications are core care tracking feature)

---

### Pitfall 6: Can't Add Plant Without Photo

**What goes wrong:** Users report frustration when they can't add a plant to collection without taking a photo immediately (no default images, can't skip).

**Why it happens:**
- UX assumes user always has plant in front of them
- Photo-first design doesn't account for:
  - Adding plants from memory
  - Plant ID failed but still want to track
  - Want to use library photo later

**Consequences:**
- User blocks at onboarding: "I just want to add my plants"
- Negative reviews: "Can't add plants without photo"
- Users abandon instead of using app

**Prevention:**
- Allow adding plant with name only
- Provide placeholder/default plant images
- Support adding photos later
- Let users search and add from plant database without photo

**Phase:** UX/UI Design (Foundation)

---

### Pitfall 7: Inadequate Plant Database Coverage

**What goes wrong:** App can't recognize or provide care for common plants users own.

**Why it happens:**
- Database limited to certain regions or plant types
- No user-contributed plant additions
- API-only data with rate limits or gaps

**Consequences:**
- "Plant not found" repeatedly
- Users manually enter every plant
- Core value proposition (care guides) fails

**Prevention:**
- Allow user-created plant profiles with custom care schedules
- Seed database with 100+ common houseplants
- Enable community contributions (with moderation)
- Graceful fallback: generic care schedule when specific unavailable

**Phase:** Foundation (data is infrastructure)

---

### Pitfall 8: Chatbot Fails to Maintain Context

**What goes wrong:** User explains plant problem over multiple messages, bot "forgets" and asks them to repeat.

**Why it happens:**
- No conversation history storage
- Each message treated independently
- Session reset on app close

**Consequences:**
- User frustration explaining situation multiple times
- Feeling of talking to a dumb machine, not a companion
- Abandonment of chat feature

**Prevention:**
- Persist conversation history locally
- Load context when starting new chat session
- Summarize previous conversation if context gets long
- Test: 20-message conversation should maintain coherence

**Phase:** Feature Implementation

---

## Minor Pitfalls

Small issues that reduce polish and user satisfaction.

### Pitfall 9: Non-Minimalist Design Creep

**What goes wrong:** App adds emoji, flashy colors, complex animations that contradict "green monochromatic minimalist" promise.

**Why it happens:**
- Feature requests add visual noise
- Design system not strictly enforced
- Unclear guidelines on "minimalist"

**Consequences:**
- App feels inconsistent
- Violates stated design philosophy
- Users who wanted simplicity leave

**Prevention:**
- Document design constraints: no emoji, green-only palette
- Code review includes design compliance
- User testing with minimalist-design-sensitive users

**Phase:** UX/UI Design

---

### Pitfall 10: Complex Navigation - Can't Go Back

**What goes wrong:** Users report difficulty navigating "back" after starting plant add flow or identification.

**Why it happens:**
- Modal flows without clear exit
- No back gesture support
- Deep navigation stacks without breadcrumbs

**Consequences:**
- User stuck in flows
- Frustration, app force-quit
- Negative reviews about navigation

**Prevention:**
- Support system back gesture
- Clear "X" / "Cancel" buttons on all modals
- Test with users unfamiliar with app

**Phase:** UX/UI Design

---

### Pitfall 11: Freemium Trap

**What goes wrong:** Core features locked behind paywall after limited free use. Users feel "trapped."

**Why it happens:**
- Business model pressure
- Free tier too restrictive
- Paywall walls appear immediately

**Consequences:**
- Negative reviews about "bait and switch"
- Users leave for fully-free alternatives
- Trust damage

**Prevention:**
- If using freemium, make core features free forever
- Be transparent about what's paid
- Test: would a user who never pays still have value?

**Note:** EDEN is explicitly out of scope for monetization, so this is lower risk.

**Phase:** Business Model (if applicable)

---

## Phase-Specific Warnings

| Phase | Likely Pitfall | Mitigation |
|-------|---------------|------------|
| Foundation | AI identification accuracy issues | Extensive testing, confidence scores, fallbacks |
| Foundation | Can't add plant without photo | Allow manual entry with defaults |
| Foundation | No data export/backup | Build export early, test restore |
| Feature Implementation | Chat personality goes flat | Large response pools, context memory |
| Feature Implementation | Notifications don't fire | Test on real devices, re-schedule on launch |
| Feature Implementation | Diagnosis gives wrong advice | Always show uncertainty, disclaimers |

---

## Sources

- New Scientist: "Apps that identify plants can be as little as 4 per cent accurate" (2023)
- Fylora: "AI Plant ID Mistakes to Avoid" (2026)
- MakeUseOf: "Here's Why Plant Identifier Apps Can Be Inaccurate" (2023)
- DEV Community: "My AI Chatbot Failed. Here's What I Missed" (2026)
- Character AI user reports on repetitive responses
- RxDB: "Downsides of Local First / Offline First"
- Medium: "I Tried Making My App 100% Offline-First. The Sync Issues Nearly Destroyed the Project" (2025)
- App Store reviews: PlantLush, Plant Daddy, Plant Identifier apps
- Trustpilot: PlantIn reviews (2025)

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| AI identification pitfalls | HIGH | Multiple studies and user reports confirm accuracy issues |
| Chat personality pitfalls | HIGH | Well-documented chatbot failure modes |
| Local storage pitfalls | HIGH | Multiple developer post-mortems available |
| Notification pitfalls | MEDIUM | Platform-specific issues, but common patterns |
| UX pitfalls | MEDIUM | App store reviews, but may be specific to certain apps |
