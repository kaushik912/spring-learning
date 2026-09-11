# Bug: shared singleton field races under concurrent requests

## The bug

`CurrentRequestContext` (`src/main/java/.../buggy/CurrentRequestContext.java`)
is a plain `@Service`:

```java
@Service
public class CurrentRequestContext {
    private String currentUserId;
    public void setCurrentUserId(String userId) { this.currentUserId = userId; }
    public String getCurrentUserId() { return currentUserId; }
}
```

Spring beans default to **singleton scope** — one instance for the whole
app, shared by every request/thread. `BuggyOrderController` sets
`currentUserId` at the start of a request, does some work, then reads it
back:

```java
currentRequestContext.setCurrentUserId(userId);
Thread.sleep(50);                                    // simulate DB/downstream call
String resolvedUserId = currentRequestContext.getCurrentUserId();
```

Under concurrent load, another thread's `setCurrentUserId` can land in that
sleep window and overwrite the field before this thread reads it back —
one user gets another user's id. Classic read-modify-read race on shared
mutable state.

## Reproduction

`RaceConditionReproductionTest` fires 100 concurrent requests (each with its
own user id) at `/api/buggy/orders/{userId}` and counts how many responses
come back with a `resolvedUserId` that doesn't match the id that request
actually sent.

- `givenSharedSingletonField_whenManyConcurrentRequests_thenUserIdsGetCorrupted` — **corruption > 0**, confirmed.
- Same test against the fixed endpoints below — **zero corruption**.

## The fix — don't put per-request state on a singleton's fields

Three options, in order of preference:

**1. Local variable / method parameter** (`fixed/SafeLocalVarController`)
Simplest fix: don't store it anywhere shared at all. Each thread has its own
stack, so a local variable can never be seen by another thread.
```java
String resolvedUserId = userId; // lives on this thread's stack only
```
Use this whenever the value is only needed within one method call chain you
control directly.

**2. Request-scoped bean**
Declare the bean `@RequestScope` (or `@Scope(WebApplicationContext.SCOPE_REQUEST)`).
Spring then creates one instance per HTTP request instead of one for the
whole app — same field-based API, but no longer shared.
```java
@Service
@RequestScope
public class CurrentRequestContext { ... }
```
Use this when you want the field-holding-object ergonomics but the value
only needs to live for one request and doesn't need to survive onto other
threads spawned by that request.

**3. ThreadLocal-backed context** (`fixed/ThreadLocalRequestContext`)
Keep the bean a singleton, but back the value with a `ThreadLocal` instead
of a plain field — each thread gets its own slot:
```java
private final ThreadLocal<String> currentUserId = new ThreadLocal<>();
public void setCurrentUserId(String userId) { currentUserId.set(userId); }
public String getCurrentUserId() { return currentUserId.get(); }
public void clear() { currentUserId.remove(); }
```
Use this when many methods deep in the call stack need the current user
without threading it through every signature — typically set in a filter/
interceptor at request start. **Must call `.remove()` in a `finally`**:
servers reuse pooled threads, so a stale value would otherwise leak into a
later, unrelated request on that same thread.

## Rule of thumb

`@Service`/`@Component` beans are singletons — write them stateless. Any
value that's specific to one request/user belongs in a local variable, a
request-scoped bean, or thread-local storage — never a plain instance field.
