# 10. The Frontend — React App

## The Simple Version

The frontend is everything you SEE and CLICK in the banking app. It runs entirely in your browser.

Think of it like an app installed on your phone, except it is delivered to your browser from the internet. After it loads, most interactions happen locally — only actual data fetches go to the server.

---

## Technology Stack

| Technology | What It Does | Simple Analogy |
|---|---|---|
| React 18 | Builds the UI | Like LEGO — compose pieces (components) into pages |
| TypeScript | Adds type safety to JavaScript | Like spell-check, but for code logic |
| Vite | Builds and serves the app | Like a compiler + local web server |
| Redux Toolkit | Global state management | Like a shared whiteboard all components can read/write |
| RTK Query | API calls + caching | Like a smart assistant that fetches and caches data |
| React Router v6 | Navigation between pages | Like browser tabs for a single page |
| Tailwind CSS | Styling | Predefined CSS classes you compose |
| React Hook Form + Zod | Forms + validation | Forms with automatic error detection |
| Recharts | Charts and graphs | Plug-in data, get a graph |
| Axios | HTTP client | The thing that actually calls the API |

---

## Project Structure

```
src/
├── main.tsx              ← Entry point. Wraps everything in Redux Provider + Router
├── App.tsx               ← Route definitions. Lazy-loads each page.
├── index.css             ← Tailwind + CSS variables (light/dark mode)
│
├── store/                ← All global state
│   ├── store.ts          ← Redux store configuration
│   ├── api/
│   │   ├── apiSlice.ts   ← RTK Query base configuration
│   │   ├── axiosBaseQuery.ts ← Custom Axios wrapper (JWT injection, refresh)
│   │   ├── accountsApi.ts    ← Account API endpoints
│   │   ├── transactionsApi.ts
│   │   ├── paymentsApi.ts
│   │   ├── cardsApi.ts
│   │   └── loansApi.ts
│   └── slices/
│       ├── authSlice.ts          ← Login state, user info, roles
│       └── notificationSlice.ts  ← In-app notifications
│
├── components/           ← Reusable UI pieces
│   ├── auth/
│   │   └── ProtectedRoute.tsx    ← Redirect to /login if not authenticated
│   ├── layout/
│   │   ├── Layout.tsx    ← Sidebar + Header wrapper
│   │   ├── Sidebar.tsx   ← Navigation menu
│   │   └── Header.tsx    ← Top bar with logout, notifications bell
│   └── ui/
│       └── LoadingSpinner.tsx
│
└── pages/                ← One folder per page
    ├── auth/LoginPage.tsx
    ├── dashboard/DashboardPage.tsx
    ├── accounts/AccountsPage.tsx
    ├── transactions/TransactionsPage.tsx
    ├── transfers/TransfersPage.tsx
    ├── payments/PaymentsPage.tsx
    ├── cards/CardsPage.tsx
    ├── loans/LoansPage.tsx
    ├── notifications/NotificationsPage.tsx
    ├── profile/ProfilePage.tsx
    ├── reports/ReportsPage.tsx
    └── admin/AdminPage.tsx
```

---

## State Management

### What Is State?

State is data that can change and needs to be remembered. Examples:
- "Is the user logged in?"
- "What are this user's accounts?"
- "How many unread notifications?"

### Local State vs Global State

**Local state** lives inside one component. When the component is unmounted (removed from the page), the state is gone.

```typescript
const [showForm, setShowForm] = useState(false) // only this component cares
```

**Global state** lives in Redux and is accessible from any component anywhere in the app.

```typescript
// In any component:
const { isAuthenticated, roles } = useSelector((state: RootState) => state.auth)
```

### Our Redux Slices

**authSlice:** Stores login status, userId, email, roles, accessToken. Also syncs to `localStorage` so you stay logged in after refreshing the page.

**notificationSlice:** Stores the list of notifications and unread count. The sidebar bell badge reads from here.

---

## RTK Query — Smart Data Fetching

**Simple explanation:** Instead of writing "fetch data, show loading spinner, handle errors, cache the result, refetch if stale" every time, RTK Query does it automatically.

```typescript
// Define the endpoint ONCE:
getMyAccounts: builder.query<Account[], string>({
  query: (userId) => ({ url: `/accounts/user/${userId}`, method: 'GET' }),
  providesTags: ['Account'],
})

// Use it in any component:
const { data: accounts, isLoading, isError } = useGetMyAccountsQuery(userId)
```

**Automatic caching:** The result is cached by default for 60 seconds. If two components ask for the same data, only ONE network request is made.

**Automatic refetching:** When you create a new account (`invalidatesTags: ['Account']`), RTK Query automatically refetches all queries tagged with `'Account'`. The account list updates instantly without you writing any refresh logic.

**Loading/error states are built in:** `isLoading`, `isError`, `data` — no need to manually manage these with `useState`.

---

## JWT Injection and Refresh (Axios Interceptors)

Every API call needs the JWT token. Instead of adding it manually every time, Axios interceptors handle it automatically.

```typescript
// REQUEST interceptor — runs before every API call
axiosInstance.interceptors.request.use((config) => {
    const token = localStorage.getItem('accessToken')
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

// RESPONSE interceptor — runs after every API response
axiosInstance.interceptors.response.use(
    (response) => response, // success — just return it
    async (error) => {
        if (error.response?.status === 401) {
            // Token expired — automatically refresh it
            const refreshToken = localStorage.getItem('refreshToken')
            const { data } = await axios.post('/api/v1/auth/refresh', null, {
                headers: { 'X-Refresh-Token': refreshToken }
            })
            // Save new tokens
            localStorage.setItem('accessToken', data.data.accessToken)
            // Retry the original failed request with new token
            error.config.headers.Authorization = `Bearer ${data.data.accessToken}`
            return axiosInstance(error.config)
        }
    }
)
```

This means: when the 15-minute access token expires, the user never notices. The interceptor automatically refreshes it and retries the request — invisible to the user.

---

## Routing and Protected Routes

```typescript
// App.tsx
<Routes>
    <Route path="/login" element={<LoginPage />} />          // public

    <Route element={<ProtectedRoute />}>                      // checks auth
        <Route element={<Layout />}>                          // sidebar + header
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/accounts" element={<AccountsPage />} />
            // ... etc
        </Route>
    </Route>
</Routes>
```

`ProtectedRoute` checks Redux for `isAuthenticated`. If false → redirect to `/login`.

Pages are loaded with `React.lazy()` — the Dashboard page code is only downloaded when the user navigates to it. This makes the initial load faster.

---

## How the Frontend Talks to the Backend

In development: Vite runs a proxy server. Requests to `/api/...` are forwarded to `localhost:8080` (the API Gateway). This avoids CORS issues in development.

In production: nginx forwards `/api/...` to the API Gateway. The React app is served as static files from nginx.

```
[Browser] → /api/v1/accounts → [nginx or Vite proxy] → [API Gateway:8080] → [account-service:8083]
```

The browser never makes a direct request to any microservice.
