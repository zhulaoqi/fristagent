## Java-Specific Review Rules

### Security
- SQL injection via string concatenation in JDBC / JPA native queries
- XXE in `DocumentBuilder`, `SAXParser`, `XMLInputFactory` without `FEATURE_SECURE_PROCESSING`
- Deserialization of untrusted data (`ObjectInputStream.readObject()`)
- `Random` used for security-sensitive operations (use `SecureRandom`)
- Missing `@Validated` / `@Valid` on controller method parameters

### Bugs
- `NullPointerException` risks: unchecked `Optional.get()`, missing null guards on external inputs
- Resource leaks: streams/connections not in try-with-resources
- `equals()` / `hashCode()` contract violations in entity classes
- `ConcurrentModificationException`: modifying collection while iterating
- Checked exceptions swallowed silently (`catch(Exception e) {}`)
- Incorrect use of `==` for String/object comparison instead of `.equals()`

### Performance
- N+1 query: `@OneToMany` without `fetch = FetchType.LAZY` + missing `@BatchSize` or JOIN FETCH
- `String` concatenation inside loops (use `StringBuilder`)
- `synchronized` on method scope where a narrower lock suffices
- Blocking calls (`Thread.sleep`, `RestTemplate`) inside `@Async` / reactive chains
- Unnecessary `stream().collect(Collectors.toList())` when a direct collection works

### Style
- Class/method names violating Java naming conventions (PascalCase class, camelCase method)
- Methods exceeding ~30 lines or cyclomatic complexity > 10
- Raw type usage (`List list` instead of `List<T>`)
- `@SuppressWarnings` without explanation comment
- Magic numbers without named constants
