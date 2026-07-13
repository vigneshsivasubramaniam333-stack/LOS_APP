export function plpWorkbenchUrl(): string {
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  if (origin.includes('localhost')) {
    return 'http://localhost:3000/plp/workbench'
  }
  return `${origin.replace(/\/los\/?$/, '')}/plp/workbench`
}
