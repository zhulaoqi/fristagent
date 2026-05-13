## Python-Specific Review Rules

### Security
- `eval()` / `exec()` on untrusted input
- `subprocess` with `shell=True` and user-controlled input (command injection)
- `pickle.loads()` on untrusted data
- Hardcoded secrets in source (use `os.environ` or config files outside repo)
- SQL injection in raw query strings with `.format()` or `%s` substitution

### Bugs
- Mutable default arguments (`def f(items=[])` — shared across calls)
- Bare `except:` clauses that swallow all exceptions including `KeyboardInterrupt`
- `is` used for value comparison instead of `==`
- Generator exhausted after first use without reset
- Missing `encoding` parameter in `open()` calls (platform-dependent default)

### Performance
- Unnecessary list comprehension when generator expression suffices
- `O(n)` membership test on `list` where `set` should be used
- Reading entire large files into memory (`f.read()`) instead of line-by-line
- Repeated attribute lookup in tight loops (cache with local variable)
- Blocking I/O in async functions without `await` / `asyncio.run_in_executor`

### Style
- PEP 8 violations: snake_case for functions/variables, PascalCase for classes
- Missing type hints on public function signatures
- Functions longer than ~50 lines
- `import *` from modules
- Deeply nested conditions (refactor with early return / guard clauses)
