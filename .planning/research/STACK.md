# Technology Stack

**Project:** EDEN - Plant Care Companion App  
**Researched:** 2026-03-04  
**Confidence:** HIGH

## Recommended Stack

### Core Framework

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **React Native** | 0.76+ (New Architecture) | Cross-platform mobile framework | Powers Instagram, Discord, Microsoft Teams. 95% code sharing between iOS/Android. New Architecture (Fabric + TurboModules) provides near-native performance. |
| **Expo** | SDK 52+ | Development platform & managed workflow | Fastest path to production. Built-in OTA updates, EAS Build. Simplified native module access via config plugins. 5-min setup vs 30+ min for bare CLI. |
| **TypeScript** | 5.x | Type safety | 78%+ React Native projects adopt TypeScript. Catches bugs early, better IDE support, improved DX. |

### Navigation

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **React Navigation** | 7.x | Screen routing & navigation | Industry standard. Supports bottom tabs (5-tab requirement), native stack, deep linking. Strong TypeScript support. |

### State Management

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **Zustand** | 5.x | Global client state | Lightweight (1KB), simple API, no boilerplate. Perfect for plant collection, personality settings, chat state. Avoid Redux complexity for this scope. |
| **TanStack Query** | 5.x | Server state / caching | Automatic caching for API responses. Optimistic updates for smooth UX. Standard for server state in RN 2025. |

### Local Storage

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **SQLite (expo-sqlite)** | Latest | Structured relational data | Plant collection, care logs, chat history require relational queries. ACID-compliant, works offline, handles complex relationships. |
| **MMKV** | 3.x | Fast key-value storage | 30x faster than AsyncStorage. Use for: auth tokens, user preferences, UI state, caching. Written in C++ with JSI for zero-copy access. |

### AI & Machine Learning

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **TensorFlow Lite** | Latest | On-device ML inference | Project requirement. Use `react-native-fast-tflite` (v2.0+) - JSI-powered, GPU delegates (CoreML/Metal), zero-copy ArrayBuffers. |
| **PlantNet API** | v2 | Cloud plant identification fallback | Official API with 30K+ species. Requires internet but provides higher accuracy than local model alone. Free tier available. |

### Camera & Image

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **react-native-vision-camera** | 4.x | High-performance camera | Required for real-time AI inference on camera frames. Supports frame processors, QR/barcode scanning, HDR. Powers Snapchat-quality camera features. |
| **expo-image-picker** | Latest | Gallery image selection | Fallback for users who want to identify existing photos. Simpler API than vision-camera for single image capture. |

### UI & Styling

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **NativeWind** | 4.x | Tailwind CSS for RN | Green monochromatic design system. Utility-first, small bundle size, easy theming. Use instead of full Tailwind - RN-specific. |
| **React Native Reanimated** | 3.x | Animations | Smooth 60fps animations for plant mood transitions, care tracking animations. Worklet-based, doesn't block JS thread. |

### Testing

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| **Jest** | Latest | Unit testing | Built-in with Expo. |
| **React Native Testing Library** | Latest | Component testing | Tests behavior, not implementation. |
| **Detox** | Latest | E2E testing | Industry standard for RN E2E. |

---

## Installation

```bash
# Core
npx create-expo-app@latest eden --template blank-typescript

# Navigation
npx expo install @react-navigation/native @react-navigation/bottom-tabs @react-navigation/native-stack
npx expo install react-native-screens react-native-safe-area-context

# State
npm install zustand @tanstack/react-query

# Storage
npx expo install expo-sqlite
npm install react-native-mmkv

# Camera & ML
npm install react-native-vision-camera react-native-fast-tflite
npx expo install expo-image-picker

# UI & Animation
npm install nativewind react-native-reanimated
npx expo install react-native-gesture-handler

# Testing
npm install --save-dev @testing-library/react-native jest @types/jest

# Form handling (for personality settings, plant details)
npm install react-hook-form zod @hookform/resolvers
```

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Framework | Expo + RN | Flutter | Flutter has excellent UI, but RN's JS ecosystem, TensorFlow.js integration, and existing plant-app references (PlantSnap, PlantNet clones) make it lower-risk. |
| State | Zustand | Redux Toolkit | Redux adds unnecessary boilerplate for app this size. Zustand handles all state needs with <100 lines. |
| Storage | SQLite + MMKV | Realm | Realm is powerful but larger bundle size (3MB+). SQLite + MMKV combo provides equivalent functionality with smaller footprint. |
| Camera | Vision Camera | expo-camera | expo-camera insufficient for real-time ML frame processing. VisionCamera's frame processor required for on-device TensorFlow inference at reasonable speed. |
| ML Runtime | TF Lite | Core ML only | TensorFlow Lite provides cross-platform models. Core ML is iOS-only, would require maintaining two model formats. |
| Styling | NativeWind | React Native Paper | Paper enforces Material Design. Green monochromatic minimalist design requires custom styling. NativeWind provides Tailwind DX with RN optimization. |

---

## Project Structure

```
eden/
├── src/
│   ├── app/                    # App entry, providers
│   │   ├── App.tsx
│   │   └── providers.tsx
│   ├── components/             # Shared UI components
│   │   ├── common/             # Buttons, inputs, cards
│   │   └── plant/              # Plant-specific components
│   ├── screens/                # Screen components (5 tabs)
│   │   ├── HomeScreen.tsx
│   │   ├── IdentifyScreen.tsx
│   │   ├── CollectionScreen.tsx
│   │   ├── ChatScreen.tsx
│   │   └── CareScreen.tsx
│   ├── navigation/             # React Navigation config
│   │   └── RootNavigator.tsx
│   ├── store/                  # Zustand stores
│   │   ├── plantStore.ts
│   │   ├── chatStore.ts
│   │   └── settingsStore.ts
│   ├── services/               # External services
│   │   ├── plantnet.ts         # PlantNet API client
│   │   ├── tflite.ts          # TensorFlow Lite service
│   │   └── storage.ts         # SQLite + MMKV wrapper
│   ├── hooks/                  # Custom hooks
│   ├── models/                 # TypeScript types
│   ├── utils/                  # Helpers
│   └── assets/                 # Images, fonts, TFLite models
│       └── models/
│           └── plant-id.tflite
├── android/
├── ios/
├── app.json
├── babel.config.js
├── nativewind.config.ts
├── metro.config.js
└── package.json
```

---

## Why This Stack

### 1. **Expo for Speed + Flexibility**
- Greenfield project benefits from Expo's fast iteration
- Can eject to bare workflow if custom native modules needed (likely for advanced ML)
- Project scope (camera, ML, local storage) is well-supported by Expo

### 2. **Local-First Architecture**
- MMKV for instant access to preferences, tokens, cached data
- SQLite for structured plant collection, care logs, chat history
- This aligns with privacy requirement: "no cloud sync, all data local"

### 3. **On-Device ML Priority**
- TensorFlow Lite via react-native-fast-tflite (JSI-powered)
- VisionCamera frame processor enables real-time inference
- PlantNet API provides cloud fallback when offline identification insufficient

### 4. **Minimalist UI Approach**
- NativeWind enables green monochromatic design system without fighting Material Design
- Reanimated for subtle plant mood animations without bloat
- Avoids heavy UI libraries that conflict with minimalist aesthetic

---

## Sources

- **React Native 2025 Best Practices:** [React Native Example](https://reactnativeexample.com/best-practices-react-native-development-2025-guide/), [Rajan's Bytes](https://mrajan.com.np/2025-11-04-react-native-2025/)
- **State Management:** [DEV Community - Zustand](https://dev.to/martygo/react-native-kit-updates-topics-you-must-know-57bi)
- **Storage Performance:** [DEV Community - MMKV](https://dev.to/gabrpimenta/supercharging-react-native-performance-a-comprehensive-guide-to-frontend-databases-and-mmkv-3lc3), [PowerSync](https://www.powersync.com/blog/react-native-local-database-options)
- **PlantNet API:** [Official Documentation](https://my.plantnet.org/doc/api/openapi)
- **TensorFlow Lite RN:** [react-native-fast-tflite](https://registry.npmjs.org/react-native-fast-tflite), [TensorFlow.js React Native](https://www.tensorflow.org/js/tutorials/applications/react_native)
- **Existing Plant Apps:** [PlantSnap RN](https://github.com/dinhvan2310/plantsnap_mobile_app), [PlantoScope](https://github.com/ShubhamPaliwal03/PlantoScope), [Crop Disease Detection](https://github.com/ziegler121/cropdiseasedetection)
- **Vision Camera:** [GitHub](https://github.com/mrousavy/react-native-vision-camera), [Comparison](https://blog.patrickskinner.tech/react-native-camera-expo-vs-visioncamera-what-you-need-to-know)
