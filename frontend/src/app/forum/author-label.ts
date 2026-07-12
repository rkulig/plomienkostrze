/**
 * Wspólna etykieta autora dla listy, szczegółu i odpowiedzi. API oddaje już
 * bezpieczny `authorDisplayName` (nigdy e-mail ani UID), więc to głównie obrona
 * na pusty/brakujący przypadek → „Kibic".
 */
export function authorLabel(displayName?: string): string {
  return displayName?.trim() || 'Kibic';
}
