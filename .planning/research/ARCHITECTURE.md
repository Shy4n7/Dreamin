# Architecture Research

**Domain:** Mobile Plant Care Companion App
**Researched:** 2026-03-04
**Confidence:** MEDIUM

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     PRESENTATION LAYER                        │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │  Home    │  │ Identify │  │  Chat    │  │  Care    │   │
│  │  Screen  │  │  Screen  │  │  Screen  │  │  Screen  │   │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘   │
│       │              │             │              │          │
├───────┴──────────────┴─────────────┴──────────────┴─────────┤
│                     VIEWMODEL LAYER                           │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  ViewModels manage UI state, coordinate with use cases │    │
│  └─────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────┤
│                      DOMAIN LAYER                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ Plant    │  │ Identify │  │  Chat    │  │  Care    │   │
│  │ UseCases │  │ UseCases │  │ UseCases │  │ UseCases │   │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘   │
├───────┴──────────────┴─────────────┴──────────────┴─────────┤
│                       DATA LAYER                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │PlantRepo │  │TFLite    │  │ Chat     │  │Schedule  │   │
│  │(SQLite)  │  │Service   │  │Template  │  │Service   │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
│                                                               │
│  ┌──────────────────────────────────────────────────┐      │
│  │              PlantNet API Client                   │      │
│  └──────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| **Presentation Layer** | UI screens, user interactions | React Native / Flutter / Jetpack Compose |
| **ViewModels** | UI state, business logic orchestration | MVVM pattern with state management |
| **Domain/Use Cases** | Business rules, entity definitions | Clean domain layer |
| **PlantRepository** | CRUD for plants, persistence | Repository pattern + SQLite |
| **TFLite Service** | On-device ML inference | TensorFlow Lite interpreter |
| **PlantNet Client** | Cloud fallback identification | REST API client |
| **Chat Engine** | Template-based response generation | Personality-driven templates |
| **Schedule Service** | Care reminders, timing logic | Local notifications |

## Recommended Project Structure

```
src/
├── domain/                    # Business logic & entities
│   ├── entities/              # Plant, CareLog, ChatMessage, Personality
│   ├── usecases/              # AddPlant, IdentifyPlant, LogCare, GetChatResponse
│   └── repositories/          # Repository interfaces
├── data/                      # Data layer implementation
│   ├── repositories/         # Repository implementations
│   ├── datasources/
│   │   ├── local/            # SQLite database, TFLite models
│   │   └── remote/           # PlantNet API client
│   └── models/               # Data transfer objects
├── presentation/             # UI layer
│   ├── screens/             # Screen components
│   ├── components/          # Reusable UI components
│   ├── viewmodels/          # State management
│   └── navigation/          # Navigation configuration
├── services/                 # External services
│   ├── ml/                  # TensorFlow Lite wrapper
│   ├── chat/                # Chat engine & templates
│   └── notifications/       # Local reminder service
└── core/                     # Shared utilities
    ├── constants/           # App constants, personality types
    ├── utils/               # Helper functions
    └── theme/               # Green monochromatic theme
```

### Structure Rationale

- **domain/**: Pure business logic, no framework dependencies. Easy to test and reuse.
- **data/**: Implements repository interfaces, handles SQLite and API calls. Isolates data sources.
- **presentation/**: UI only. ViewModels bridge domain and UI. Keeps screens thin.
- **services/**: Complex external integrations (ML, chat, notifications) in dedicated modules.
- **core/**: Shared code across all layers (constants, theme, utilities).

## Architectural Patterns

### Pattern 1: Clean Architecture with MVVM

**What:** Three-layer architecture (Presentation → Domain → Data) combined with MVVM for UI layer
**When to use:** Most mobile apps, especially with complex business logic
**Trade-offs:**
- Pros: Testable, maintainable, clear separation of concerns
- Cons: More boilerplate initially, steep learning curve for small features

**Example:**
```typescript
// Domain layer - Use case
class IdentifyPlantUseCase {
  constructor(
    private tfliteService: TFLiteService,
    private plantNetClient: PlantNetClient,
    private plantRepository: PlantRepository
  ) {}

  async execute(imageData: Uint8Array): Promise<Plant> {
    // 1. Try on-device identification first
    const localResult = await this.tfliteService.identify(imageData);
    
    if (localResult.confidence > 0.8) {
      return this.plantRepository.save(localResult.plant);
    }
    
    // 2. Fall back to cloud API
    const cloudResult = await this.plantNetClient.identify(imageData);
    return this.plantRepository.save(cloudResult);
  }
}

// Presentation layer - ViewModel
class IdentifyViewModel {
  @observable isLoading = false;
  @observable result: Plant | null = null;
  @observable error: string | null = null;

  constructor(private identifyUseCase: IdentifyPlantUseCase) {}

  @action
  async identifyPlant(imageData: Uint8Array) {
    this.isLoading = true;
    try {
      this.result = await this.identifyUseCase.execute(imageData);
    } catch (e) {
      this.error = e.message;
    } finally {
      this.isLoading = false;
    }
  }
}
```

### Pattern 2: Repository Pattern

**What:** Single source of truth that abstracts data sources
**When to use:** Any app with local + optional remote data
**Trade-offs:**
- Pros: Clean API, easy to swap data sources, supports offline-first
- Cons: Extra abstraction layer

**Example:**
```typescript
interface PlantRepository {
  getAll(): Promise<Plant[]>;
  getById(id: string): Promise<Plant | null>;
  save(plant: Plant): Promise<Plant>;
  delete(id: string): Promise<void>;
  exportAll(): Promise<string>; // JSON export
}

class PlantRepositoryImpl implements PlantRepository {
  constructor(
    private database: SQLiteDatabase,
    private plantNetClient: PlantNetClient
  ) {}

  async getAll(): Promise<Plant[]> {
    return this.database.plants.getAll();
  }

  async save(plant: Plant): Promise<Plant> {
    const saved = await this.database.plants.insert(plant);
    return saved;
  }
}
```

### Pattern 3: Offline-First with Local Database

**What:** Local SQLite is primary source, network is optimization
**When to use:** Privacy-focused, offline-first apps like EDEN
**Trade-offs:**
- Pros: Works without internet, privacy by design, fast UX
- Cons: No cross-device sync, data lives only on device

**Example:**
```typescript
class OfflineFirstPlantRepository implements PlantRepository {
  async getAll(): Promise<Plant[]> {
    // Always return local data first
    const localPlants = await this.database.query('SELECT * FROM plants');
    
    // Optionally sync with cloud in background (not for EDEN - local only)
    return localPlants;
  }

  async save(plant: Plant): Promise<Plant> {
    // Write to local DB immediately
    const saved = await this.database.insert('plants', plant);
    
    // Trigger any background sync if needed
    // (EDEN doesn't need this - all local)
    
    return saved;
  }
}
```

## Data Flow

### Plant Identification Flow

```
[Camera Capture]
       ↓
[Image Preprocessing] → Resize to model input, normalize
       ↓
[TFLite Inference] → On-device model (primary)
       ↓
[Confidence Check]
  ├─ > 80% → [Save to Local DB] → [Display Result]
  └─ < 80% → [PlantNet API] → [Save to Local DB] → [Display Result]
```

### Chat Flow

```
[User Message]
       ↓
[ChatViewModel] → [GetChatResponseUseCase]
       ↓
[Load Plant Context] → Plant type, personality, recent care history
       ↓
[ChatEngine] → Select template based on personality, inject context
       ↓
[Template Processor] → Replace placeholders, format response
       ↓
[Display Response] → Save to chat history
```

### Care Tracking Flow

```
[User Action: "Water Plant"]
       ↓
[CareViewModel] → [LogCareUseCase]
       ↓
[Update Plant Record] → Last watered date, health score
       ↓
[Calculate Next Care Date] → Based on plant type
       ↓
[Schedule Reminder] → Local notification
       ↓
[Update UI] → Show updated care schedule
```

### Key Data Flows

1. **Plant Identification:** Camera → TFLite/PlantNet → Repository → UI
2. **Chat Generation:** User Input + Plant Context → Chat Engine → UI + Save History
3. **Care Logging:** User Action → Update Plant + Schedule Reminder → UI
4. **Health Diagnosis:** Camera → TFLite (disease model) → Treatment Plan → UI

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 0-100 plants | Single SQLite database, basic queries fine |
| 100-1,000 plants | Add database indexes, consider pagination |
| 1,000+ plants | Archive old care logs, optimize image storage |

### Scaling Priorities

1. **First bottleneck:** Large plant collection images. Fix: Store thumbnails, load full images on demand.
2. **Second bottleneck:** Chat history growth. Fix: Paginate chat messages, archive old conversations.

## Anti-Patterns

### Anti-Pattern 1: Putting Business Logic in UI Components

**What people do:** Write use case logic directly in React/Compose components
**Why it's wrong:** Hard to test, mixes concerns, breaks when UI framework changes
**Do this instead:** Use ViewModels that delegate to domain use cases

### Anti-Pattern 2: Direct Database Calls from Screens

**What people do:** Screens query SQLite directly
**Why it's wrong:** Breaks separation of concerns, makes testing impossible
**Do this instead:** Screens call ViewModels, ViewModels call use cases, use cases use repositories

### Anti-Pattern 3: Monolithic "God" ViewModels

**What people do:** One ViewModel handling all features
**Why it's wrong:** Unmaintainable, hard to test, creates merge conflicts
**Do this instead:** One ViewModel per screen or feature area

### Anti-Pattern 4: Ignoring Offline State

**What people do:** Assume network always available
**Why it's wrong:** App breaks in poor connectivity, poor user experience
**Do this instead:** Design offline-first, cache aggressively, show offline indicators

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| **PlantNet API** | REST client with retry logic | Fallback when TFLite confidence < 80% |
| **TensorFlow Lite** | On-device inference | Load model on app start, run in background thread |
| **Local Notifications** | OS notification scheduler | Schedule care reminders |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| UI ↔ ViewModel | State/Events | ViewModel exposes observable state, UI observes |
| ViewModel ↔ Use Cases | Method calls | Use cases are injected into ViewModels |
| Use Cases ↔ Repositories | Interface calls | Depend on abstractions, not implementations |
| Repositories ↔ Data Sources | Repository pattern | Abstracts SQLite and API clients |

## Build Order Recommendations

Based on dependency analysis, build in this order:

```
1. Core & Theme
   └── Constants, theme configuration, utility functions
   
2. Domain Layer  
   └── Entities, repository interfaces, use case definitions
   
3. Data Layer
   └── SQLite setup, repository implementations, API client
   
4. Services
   └── TFLite service, chat engine, notification service
   
5. Presentation - Core
   └── Navigation, shared components
   
6. Feature: Plant Collection
   └── Home screen, plant list, plant detail
   
7. Feature: Plant Identification
   └── Camera integration, identification flow
   
8. Feature: Chat
   └── Chat screen, message list, input
   
9. Feature: Care Tracking
   └── Care logging, reminders, schedules
   
10. Feature: Health Diagnosis
    └── Camera integration, diagnosis flow, treatment display
```

### Rationale

- **Foundation first:** Core utilities and domain entities have no dependencies
- **Data before UI:** Can't display plants without data layer working
- **Services need data:** TFLite and chat need plant data context
- **Features last:** Each feature builds on all supporting layers

## Sources

- [Clean Architecture for Mobile Apps (2025)](https://nextnative.dev/blog/mobile-app-architecture-best-practices)
- [Flutter Clean Architecture MVVM](https://www.brahimoubbad.com/2025/06/jetpack-compose-clean-architecture.html)
- [TensorFlow Lite Mobile Integration](https://medium.com/@mahmuthanb/building-a-flutter-app-with-machine-learning-using-tensorflow-lite-474fd03a30fe)
- [Flutter Offline-First Architecture](https://docs.flutter.dev/app-architecture/design-patterns/offline-first)
- [Local-First Database Layer](https://www.welcomedeveloper.com/posts/local-first-architecture-3-database-layer/)
- [AgroAId: Plant Species Classification with TFLite](https://mdpi-res.com/d_attachment/informatics/informatics-09-00055/article_deploy/informatics-09-00055-v3.pdf)

---

*Architecture research for: Mobile Plant Care Companion App*
*Researched: 2026-03-04*
