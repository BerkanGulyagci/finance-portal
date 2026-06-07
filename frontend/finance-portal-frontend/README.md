# Finance Portal — Frontend

**English** · [Türkçe](README.md)

A **feature-based** single-page application (SPA) built on React 19 + Vite.

> This document covers frontend-specific technical details. For the project overview, setup and running, see the [main README](../../README.en.md).

## Architectural Approach

The application is organized by functional **feature areas**, not by technical type. All data is fetched from the backend via `/api/v1/*`.

```
src/
├── features/        # Feature areas (9 areas, 47 pages)
│   ├── dashboard/   # Customizable dashboard (drag-and-drop)
│   ├── market/      # 10 asset types (stocks, crypto, FX, funds, bonds, VIOP, commodities, gold, indices, economy)
│   ├── portfolio/   # Portfolio, holdings, AI analysis
│   ├── alarms/      # Alarm management
│   ├── news/        # News list/detail
│   ├── notifications/ # Notification center
│   ├── profile/     # Profile and preferences
│   ├── admin/       # Admin panels
│   └── auth/        # Login, registration, email verification
├── app/             # Application shell (AppLayout, Header, Footer, ProtectedRoute)
├── components/      # Shared UI components
├── context/         # Global state (7 React Contexts)
├── api/             # Backend REST call layer (Axios)
├── router/          # Page routing (ProtectedRoute / AdminRoute)
├── hooks/           # Custom React hooks
├── i18n/            # Multilingual text (TR / EN, 14 namespaces)
├── lib/             # HTTP client (token interceptor)
└── utils/           # Helper functions, formatters
```

## Main Features

- **3 access tiers** — Public (guest), Protected (registered user), Admin — via `ProtectedRoute` / `AdminRoute`.
- **State management** — 7 React Contexts (identity, theme, language, preferences, watchlist, notifications, confirmation). No Redux.
- **Identity** — Keycloak OIDC (PKCE / S256), proactive + single-flight token refresh.
- **Charts** — klinecharts (detail candlesticks + MA/RSI/MACD/Bollinger + drawing), ECharts (comparison), Recharts (analysis / allocation).
- **Theme & language** — Light / dark theme (CSS variables, FOUC prevention), TR / EN i18n, multi-currency display.
- **Export** — Excel (xlsx), PDF (jsPDF), chart image (PNG / html-to-image).
- **Cross-device sync** — User preferences (dashboard layout, theme, language, chart drawings) are stored on the server.

## Technologies

React 19, Vite, React Router 7, Tailwind CSS, Axios; klinecharts, ECharts, Recharts; react-grid-layout (drag-and-drop); html-to-image, jsPDF, xlsx (export); lucide-react (icons).

## Local Development

> Prerequisite: **Node.js 20+**. The backend must be running (via Docker or separately). For the full stack, see the [main README](../../README.en.md).

```bash
# Install dependencies
npm install

# Development server (HMR) — http://localhost:5173
npm run dev

# Production build — to the dist/ folder
npm run build

# Preview the build
npm run preview
```

## Testing & Quality

```bash
npm run test            # Vitest tests (one-shot)
npm run test:watch      # in watch mode
npm run test:coverage   # coverage report (v8)
npm run lint            # ESLint
```

- **~2,500 tests** (Vitest + React Testing Library + jsdom).

## Configuration

Environment variables are in the `.env` file (copied from `.env.example`). Key variables:

| Variable | Purpose |
|---|---|
| `VITE_KEYCLOAK_URL` | Keycloak server address (OIDC) |
| `VITE_KEYCLOAK_REALM` | Keycloak realm name |

> These values are embedded into the image at production build time (build-arg). If not provided, the local defaults in the code are used.

> For detailed design (component architecture, state management, chart strategy, i18n), see the **Technical Design Document**.
