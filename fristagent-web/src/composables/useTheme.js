import { ref } from 'vue'

const STORAGE_KEY = 'fristagent-theme'
const theme = ref(localStorage.getItem(STORAGE_KEY) || 'dark')

function resolved(val) {
  if (val === 'system') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  return val
}

function apply(r) {
  document.documentElement.dataset.theme = r
  document.documentElement.classList.toggle('dark', r === 'dark')
}

export function initTheme() {
  apply(resolved(theme.value))
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (theme.value === 'system') apply(resolved('system'))
  })
}

export function useTheme() {
  function setTheme(val) {
    theme.value = val
    localStorage.setItem(STORAGE_KEY, val)
    apply(resolved(val))
  }
  return { theme, setTheme }
}
