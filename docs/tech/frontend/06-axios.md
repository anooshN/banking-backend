# Axios & Interceptors

## What Is Axios?

Axios is an HTTP client library for JavaScript. It wraps the browser's `fetch()` API with a cleaner interface, automatic JSON serialization/deserialization, and most importantly — **interceptors**.

---

## Base Configuration

```typescript
// src/store/api/axiosBaseQuery.ts
import axios, { AxiosError } from 'axios'

const axiosInstance = axios.create({
  withCredentials: true,  // send cookies with cross-origin requests
  headers: {
    'Content-Type': 'application/json',
  },
  // baseURL is set on individual requests via RTK Query
})
```

---

## Request Interceptor — JWT Injection

Every request needs the JWT token in the Authorization header. Instead of manually adding it to every API call, the request interceptor does it automatically:

```typescript
axiosInstance.interceptors.request.use(
  (config) => {
    // Runs BEFORE every request is sent
    const token = localStorage.getItem('accessToken')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // Must return the config (possibly modified)
    return config
  },
  (error) => {
    // Request failed to be constructed (rare)
    return Promise.reject(error)
  }
)
```

**Effect:** Every `axios.get('/api/v1/accounts/...')` automatically has `Authorization: Bearer eyJ...` added — no manual work needed in any component or API call.

---

## Response Interceptor — Automatic Token Refresh

When the 15-minute access token expires, the server returns 401. Without the interceptor, the user would see an error. With it, the token is silently refreshed and the request retried:

```typescript
axiosInstance.interceptors.response.use(
  // Success path — just return the response unchanged
  (response) => response,

  // Error path — runs when any request fails
  async (error: AxiosError) => {
    const originalRequest = error.config as any

    // Only attempt refresh if:
    // 1. It's a 401 Unauthorized response
    // 2. We haven't already retried this request (prevent infinite loop)
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true  // mark as retried

      try {
        const refreshToken = localStorage.getItem('refreshToken')

        // Call refresh endpoint with the refresh token
        const { data } = await axios.post('/api/v1/auth/refresh', null, {
          headers: { 'X-Refresh-Token': refreshToken }
        })
        // Note: use raw axios, not axiosInstance (to avoid triggering this interceptor again)

        // Store new tokens
        localStorage.setItem('accessToken', data.data.accessToken)
        localStorage.setItem('refreshToken', data.data.refreshToken)

        // Update the failed request's Authorization header with new token
        originalRequest.headers.Authorization = `Bearer ${data.data.accessToken}`

        // Retry the original failed request
        return axiosInstance(originalRequest)

      } catch (refreshError) {
        // Refresh also failed (refresh token expired or invalid)
        // Force logout — user must log in again
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        localStorage.removeItem('userId')
        localStorage.removeItem('email')
        localStorage.removeItem('roles')
        window.location.href = '/login'  // hard redirect to login page
        return Promise.reject(refreshError)
      }
    }

    // For non-401 errors (400, 403, 404, 500, etc.) — just reject normally
    return Promise.reject(error)
  }
)
```

**The user experience:**
1. User is on the accounts page, access token expires
2. User clicks "View Transactions"
3. Request fails with 401
4. Interceptor fires: sends refresh token to `/auth/refresh`
5. New tokens received, stored
6. Original "View Transactions" request retried with new token
7. Transactions load normally
8. User never sees any error or login prompt

---

## RTK Query Base Query Using Axios

RTK Query uses a "base query" function to make requests. We use our Axios instance:

```typescript
export const axiosBaseQuery =
  ({ baseUrl }: { baseUrl: string }): BaseQueryFn =>
  async ({ url, method, data, params }) => {
    try {
      const result = await axiosInstance({
        url: baseUrl + url,  // e.g., "/api/v1" + "/accounts/user/123"
        method,
        data,                // request body (for POST/PUT)
        params,              // query params (for GET)
      })
      // RTK Query expects { data: ... } on success
      return { data: result.data }
    } catch (axiosError) {
      const err = axiosError as AxiosError
      // RTK Query expects { error: ... } on failure
      return {
        error: {
          status: err.response?.status,
          data: err.response?.data || err.message,
        },
      }
    }
  }

// Used in apiSlice.ts:
export const apiSlice = createApi({
  reducerPath: 'api',
  baseQuery: axiosBaseQuery({ baseUrl: '/api/v1' }),  // all requests start with /api/v1
  tagTypes: ['Account', 'Transaction', 'Payment', 'Card', 'Loan', 'User', 'Notification'],
  endpoints: () => ({}),  // endpoints added via injectEndpoints in each api file
})
```

---

## Correlation ID Header (Future Enhancement)

We should add correlation ID injection to the request interceptor:

```typescript
axiosInstance.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // Add correlation ID for distributed tracing
  config.headers['X-Correlation-ID'] = crypto.randomUUID()
  return config
})
// Now every frontend request can be traced through all backend services
```
