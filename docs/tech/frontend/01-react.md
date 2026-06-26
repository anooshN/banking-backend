# React 18

## What Is React?

React is a JavaScript library for building user interfaces. It lets you describe what the UI should look like and React updates the actual DOM efficiently when data changes.

**Simple explanation:** Instead of manually updating HTML when data changes, you tell React "when this data is X, the UI should look like Y" and React handles the actual DOM updates automatically.

---

## Core Concepts Used in This Project

### Components

Every piece of UI is a component — a JavaScript function that returns JSX (HTML-like syntax):

```tsx
// src/components/layout/Header.tsx
const Header: React.FC = () => {
  const dispatch = useDispatch()
  const { email } = useSelector((state: RootState) => state.auth)
  const { unreadCount } = useSelector((state: RootState) => state.notifications)

  const handleLogout = () => {
    dispatch(logout())
    navigate('/login')
  }

  // JSX — looks like HTML but is JavaScript
  return (
    <header className="bg-white border-b border-gray-200 px-6 py-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-gray-500">{email}</p>
        {/* JavaScript expression in JSX: { } */}
        
        <button onClick={() => navigate('/notifications')}>
          <Bell size={20} />
          {unreadCount > 0 && (
            // Conditional rendering: only show badge if there are unread notifications
            <span className="bg-red-500 text-white text-xs rounded-full w-5 h-5">
              {unreadCount > 9 ? '9+' : unreadCount}
            </span>
          )}
        </button>
      </div>
    </header>
  )
}
```

### useState — Local Component State

```tsx
// src/pages/accounts/AccountsPage.tsx
const AccountsPage: React.FC = () => {
  // useState returns [currentValue, setterFunction]
  const [showCreate, setShowCreate] = useState(false)
  // showCreate starts as false
  // setShowCreate(true) triggers a re-render with showCreate = true

  const [newAccountType, setNewAccountType] = useState('CHECKING')

  return (
    <div>
      <button onClick={() => setShowCreate(true)}>Open Account</button>

      {/* Only renders when showCreate is true */}
      {showCreate && (
        <div>
          {/* Clicking SAVINGS sets newAccountType to 'SAVINGS' → re-renders */}
          {['CHECKING', 'SAVINGS', 'INVESTMENT'].map(type => (
            <button
              key={type}
              onClick={() => setNewAccountType(type)}
              className={newAccountType === type ? 'selected' : ''}
            >
              {type}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
```

### useEffect — Side Effects

```tsx
// useEffect runs after render, for side effects (API calls, subscriptions, timers)
const DashboardPage: React.FC = () => {
  const [accounts, setAccounts] = useState([])

  // We DON'T use useEffect for data fetching — we use RTK Query instead
  // But useEffect is used for WebSocket setup:

  useEffect(() => {
    // Set up WebSocket connection when component mounts
    const stompClient = new StompJs.Client({
      brokerURL: 'ws://localhost:8080/ws/notifications',
    })

    stompClient.onConnect = () => {
      stompClient.subscribe('/user/queue/notifications', (message) => {
        const notification = JSON.parse(message.body)
        dispatch(addNotification(notification))  // update Redux store
      })
    }

    stompClient.activate()

    // Cleanup: disconnect when component unmounts
    return () => {
      stompClient.deactivate()
    }
  }, []) // [] = run once after first render (empty dependency array)
```

### React.lazy — Code Splitting

```tsx
// src/App.tsx
// Instead of loading all page code upfront:
// import DashboardPage from './pages/dashboard/DashboardPage'  // all pages downloaded at once

// With lazy loading: each page's code is only downloaded when the user navigates to it
const DashboardPage = lazy(() => import('./pages/dashboard/DashboardPage'))
const AccountsPage = lazy(() => import('./pages/accounts/AccountsPage'))
const TransactionsPage = lazy(() => import('./pages/transactions/TransactionsPage'))

// Wrap in Suspense to show loading spinner while lazy component loads
<Suspense fallback={<LoadingSpinner />}>
  <Routes>
    <Route path="/dashboard" element={<DashboardPage />} />
    <Route path="/accounts"  element={<AccountsPage />} />
  </Routes>
</Suspense>
```

**Why code splitting?**
Without it: the browser downloads ALL page code (dashboard, accounts, transactions, transfers, cards, loans, admin...) on first load. That could be 2MB+ of JavaScript.
With lazy loading: first load downloads only the login page + app shell (~100KB). Each page's code is downloaded on demand.

---

## React Router v6

```tsx
// src/App.tsx
<Routes>
  {/* Public route — no auth needed */}
  <Route path="/login" element={<LoginPage />} />

  {/* Protected routes — ProtectedRoute checks auth */}
  <Route element={<ProtectedRoute />}>          {/* authentication check */}
    <Route element={<Layout />}>                {/* renders sidebar + header */}
      <Route path="/dashboard"    element={<DashboardPage />} />
      <Route path="/accounts"     element={<AccountsPage />} />
      <Route path="/transactions" element={<TransactionsPage />} />
    </Route>
  </Route>

  {/* Redirect root to dashboard */}
  <Route path="/" element={<Navigate to="/dashboard" replace />} />
  {/* 404 → redirect to dashboard */}
  <Route path="*" element={<Navigate to="/dashboard" replace />} />
</Routes>
```

**ProtectedRoute — auth guard:**
```tsx
// src/components/auth/ProtectedRoute.tsx
const ProtectedRoute: React.FC<{ requiredRole?: string }> = ({ requiredRole }) => {
  const { isAuthenticated, roles } = useSelector((state: RootState) => state.auth)
  const location = useLocation()

  if (!isAuthenticated) {
    // Redirect to login, remember where they were trying to go
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (requiredRole && !roles.includes(requiredRole)) {
    return <Navigate to="/dashboard" replace />  // no permission
  }

  return <Outlet />  // render child routes
}
```

**Outlet:** In React Router v6, `<Outlet />` renders the matched child route. So `<Layout />` renders the sidebar/header with `<Outlet />` where the current page goes.

---

## React 18 Specific — Concurrent Features

**React 18 introduced concurrent rendering** — React can pause and resume rendering work, prioritizing urgent updates (user typing) over less urgent ones (background data loading).

**useTransition — mark non-urgent state updates:**
```tsx
// Could be used for filtering large lists without blocking the UI
const [isPending, startTransition] = useTransition()

const handleSearch = (value: string) => {
  // This update is urgent (show the typed character immediately)
  setSearchInput(value)

  // This update is non-urgent (filter results can wait)
  startTransition(() => {
    setFilteredTransactions(
      transactions.filter(t => t.description?.includes(value))
    )
  })
}
```

**Automatic batching:** In React 18, multiple state updates in async functions are batched automatically into one re-render (was only in event handlers in React 17).
