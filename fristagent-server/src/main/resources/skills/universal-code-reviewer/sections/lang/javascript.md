## JavaScript / TypeScript Review Rules

### Security
- `innerHTML` / `document.write()` with user-controlled data (XSS)
- `eval()` or `new Function()` with dynamic strings
- Prototype pollution via `Object.assign({}, userInput)` without sanitization
- JWT stored in `localStorage` (prefer `httpOnly` cookie)
- CORS headers set to `*` on sensitive endpoints

### Bugs
- `==` instead of `===` for equality checks
- Uncaught promise rejections (missing `.catch()` or `try/await` around async calls)
- `var` hoisting causing unexpected scoping (use `const` / `let`)
- Mutating function arguments that are objects/arrays
- Missing `await` before async function calls (silent no-op)
- `Array.prototype.sort()` without comparator on non-string arrays

### Performance
- Memory leaks: event listeners added without removal in component unmount / cleanup
- Large synchronous operations blocking the event loop
- Unnecessary re-renders in React/Vue caused by inline object/function creation in render
- `document.querySelector` inside loops (cache the reference)
- Unthrottled scroll/resize event handlers

### Style (TypeScript)
- `any` type without justification
- Non-null assertion `!` on values that could legitimately be null
- `interface` vs `type` — prefer `interface` for extensible object shapes
- Missing return type annotations on exported functions
- `console.log` left in production code
