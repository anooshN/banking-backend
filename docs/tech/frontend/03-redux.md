# Redux Toolkit & RTK Query

## What Is Redux?

Redux is a global state management library. It provides a single "store" (a JavaScript object) that holds the entire application state, accessible from any component.

**Simple explanation:** A shared whiteboard that all components can read from and write to. Changes are made through "actions" — messages that describe what changed.

---

## Our Redux Store Structure

```
store/
├── auth                     ← is logged in? who? what roles?
│   ├── isAuthenticated: bool
│   ├── userId: string
│   ├── email: string
│   ├── roles: string[]
│   └── accessToken: string
│
├── notifications             ← in-app notification bell
│   ├── notifications: Notification[]
│   └── unreadCount: number
│
└── api (RTK Query)           ← cached API data
    ├── Account cache
    ├── Transaction cache
    ├── Payment cache
    ├── Card cache
    └── Loan cache
```

---

## createSlice — State + Actions in One

```typescript
// src/store/slices/authSlice.ts
import { createSlice, PayloadAction } from '@reduxjs/toolkit'

interface AuthState {
  isAuthenticated: boolean
  userId: string | null
  email: string | null
  roles: string[]
  accessToken: string | null
}

// Initial state: read from localStorage so user stays logged in after refresh
const initialState: AuthState = {
  isAuthenticated: !!localStorage.getItem('accessToken'),
  userId: localStorage.getItem('userId'),
  email: localStorage.getItem('email'),
  roles: JSON.parse(localStorage.getItem('roles') || '[]'),
  accessToken: localStorage.getItem('accessToken'),
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    // setCredentials is an ACTION — components dispatch this to update state
    setCredentials: (state, action: PayloadAction<AuthResponse>) => {
      const { userId, email, roles, accessToken, refreshToken } = action.payload

      // Immer (built into Redux Toolkit) allows "mutating" state directly
      // It actually creates a new immutable state object under the hood
      state.isAuthenticated = true
      state.userId = userId
      state.email = email
      state.roles = roles
      state.accessToken = accessToken

      // Persist to localStorage for page refreshes
      localStorage.setItem('accessToken', accessToken)
      localStorage.setItem('refreshToken', refreshToken)
      localStorage.setItem('userId', userId)
      localStorage.setItem('email', email)
      localStorage.setItem('roles', JSON.stringify(roles))
    },

    logout: (state) => {
      state.isAuthenticated = false
      state.userId = null
      state.email = null
      state.roles = []
      state.accessToken = null
      localStorage.clear()
    },
  },
})

// Export actions (for dispatch) and reducer (for store)
export const { setCredentials, logout } = authSlice.actions
export default authSlice.reducer
```

**Using the slice:**
```tsx
// In a component:
const dispatch = useDispatch()
const { isAuthenticated, email, roles } = useSelector((state: RootState) => state.auth)

// Login success:
dispatch(setCredentials({ userId, email, roles, accessToken, refreshToken, ... }))

// Logout:
dispatch(logout())
```

---

## RTK Query — Smart Data Fetching

RTK Query is built into Redux Toolkit. It handles:
- Making API calls
- Caching responses
- Loading and error states
- Automatic re-fetching when data becomes stale
- Cache invalidation

```typescript
// src/store/api/accountsApi.ts
import { apiSlice } from './apiSlice'

// Inject endpoints into the base API
export const accountsApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({

    // QUERY = read data
    getMyAccounts: builder.query<Account[], string>({
      // Account[] = return type, string = argument type (userId)
      query: (userId) => ({
        url: `/accounts/user/${userId}`,
        method: 'GET',
      }),
      // Tags: used for cache invalidation
      // 'Account' tag = this data is "about accounts"
      providesTags: ['Account'],
    }),

    getAccount: builder.query<Account, string>({
      query: (id) => ({ url: `/accounts/${id}`, method: 'GET' }),
      // Tag with specific ID: when account "acc-123" is updated,
      // only invalidate THIS account's cache, not all accounts
      providesTags: (result, error, id) => [{ type: 'Account', id }],
    }),

    // MUTATION = create/update/delete data
    createAccount: builder.mutation<Account, { userId: string; type: string }>({
      query: ({ userId, type }) => ({
        url: `/accounts/user/${userId}?type=${type}`,
        method: 'POST',
      }),
      // After creating an account, invalidate ALL Account caches
      // → getMyAccounts will automatically refetch
      invalidatesTags: ['Account'],
    }),
  }),
})

// Export generated hooks
export const {
  useGetMyAccountsQuery,
  useGetAccountQuery,
  useCreateAccountMutation,
} = accountsApi
```

**Using RTK Query hooks:**
```tsx
const AccountsPage: React.FC = () => {
  const { userId } = useSelector((state: RootState) => state.auth)

  // useGetMyAccountsQuery automatically:
  // 1. Makes GET request on component mount
  // 2. Caches the result
  // 3. Returns loading/error/data states
  // 4. Refetches if cache is stale
  const {
    data: accounts,    // the API response data (undefined while loading)
    isLoading,         // true on first load
    isFetching,        // true on background refetch
    isError,           // true if request failed
    error,             // the error object
    refetch,           // call this to manually trigger a refetch
  } = useGetMyAccountsQuery(userId ?? '', {
    skip: !userId,     // don't make the request if userId is null
  })

  const [createAccount, {
    isLoading: creating,  // true while the create request is pending
    isSuccess,            // true after successful creation
  }] = useCreateAccountMutation()

  const handleCreate = async () => {
    try {
      // .unwrap() throws if the request fails (instead of returning error state)
      const newAccount = await createAccount({ userId: userId!, type: 'SAVINGS' }).unwrap()
      console.log('Created:', newAccount)
      // RTK Query automatically refetches getMyAccounts because we invalidatesTags: ['Account']
    } catch (error) {
      console.error('Failed:', error)
    }
  }

  if (isLoading) return <LoadingSpinner />

  return (
    <div>
      {accounts?.map(account => (
        <AccountCard key={account.id} account={account} />
      ))}
    </div>
  )
}
```

---

## Cache Behavior

```
First render: no cache → API call → response stored in cache
Second render: cache hit → returns instantly (no API call)
After createAccount: 'Account' cache invalidated → automatic refetch

Cache lifetime: 60 seconds by default
After 60 seconds: next render triggers a background refetch
```

---

## notificationSlice — Unread Count

```typescript
// src/store/slices/notificationSlice.ts
const notificationSlice = createSlice({
  name: 'notifications',
  initialState: { notifications: [], unreadCount: 0 },
  reducers: {
    addNotification: (state, action: PayloadAction<Notification>) => {
      // Add to front of array (newest first)
      state.notifications.unshift(action.payload)
      if (!action.payload.read) state.unreadCount++
    },

    markAsRead: (state, action: PayloadAction<string>) => {
      const notification = state.notifications.find(n => n.id === action.payload)
      if (notification && !notification.read) {
        notification.read = true
        state.unreadCount = Math.max(0, state.unreadCount - 1)
      }
    },

    markAllAsRead: (state) => {
      state.notifications.forEach(n => { n.read = true })
      state.unreadCount = 0
    },
  },
})
```

**The bell badge in the header:**
```tsx
const Header: React.FC = () => {
  const { unreadCount } = useSelector((state: RootState) => state.notifications)

  return (
    <button>
      <Bell size={20} />
      {unreadCount > 0 && (
        <span className="bg-red-500 text-white rounded-full">
          {unreadCount > 9 ? '9+' : unreadCount}
        </span>
      )}
    </button>
  )
}
```

When a WebSocket notification arrives → `dispatch(addNotification(notification))` → `unreadCount` increments → header re-renders → badge appears.
