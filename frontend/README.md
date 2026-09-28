# Electrónica Rojas frontend

React + TypeScript + Vite single-page application. See the repository root `README.md` for setup.

```
src/
  app/                       # router, layout, navigation, dashboard, TanStack Query client
  features/<feature>/        # one folder per business feature (auth, inventory, repairs, service, ...)
  shared/api/httpClient.ts   # single fetch wrapper (session cookie, CSRF header, ProblemDetail errors)
  shared/i18n/               # Spanish labels and translations of stable server error codes
  shared/ui/                 # shared components of the design system (docs/design-system.md)
  styles/                    # tokens.css and style layers
```

- `npm run dev` – dev server on http://localhost:5173, proxies `/api` to Spring Boot (`BACKEND_URL`, default `http://localhost:8080`).
- `npm test` – Vitest + Testing Library.
- `npm run lint` – oxlint.
- `npm run check:contrast` – WCAG 2.2 AA contrast of every palette.
- `npm run build` – type-check (`tsc -b`) and production build to `dist/`.
