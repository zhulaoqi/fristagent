## Go-Specific Review Rules

### Security
- `fmt.Sprintf` used to construct SQL queries (use parameterized queries)
- `os/exec` with unsanitized user input
- `crypto/rand` vs `math/rand` — use `crypto/rand` for security-sensitive randomness
- Insufficient TLS configuration in `http.Client` (`InsecureSkipVerify: true`)

### Bugs
- Goroutine leak: goroutine started without a termination mechanism
- `defer` in loop not executing until function return (move to separate function)
- Ignoring returned `error` values
- Slice header copy semantics — modifying a slice returned from a function mutates the original
- Race conditions: shared state accessed from multiple goroutines without sync primitive

### Performance
- Unnecessary memory allocations in hot paths (reuse via `sync.Pool`)
- Passing large structs by value instead of pointer in performance-critical code
- `strings.Builder` vs `+` for string concatenation in loops
- Unbuffered channel blocking when buffered channel suffices

### Style
- `err` variable shadowing in nested scopes
- Using `panic` for non-exceptional errors (use error returns)
- Package-level vars that should be constants
- Exported functions missing godoc comments
- Early return pattern not used (deeply nested happy path)
