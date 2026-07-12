/**
 * Wspólna etykieta autora dla listy, szczegółu i odpowiedzi. API oddaje już
 * bezpieczny `authorDisplayName` (nigdy e-mail ani UID), więc to głównie obrona
 * na pusty/brakujący przypadek → „Kibic".
 */
export function authorLabel(displayName?: string): string {
  return displayName?.trim() || 'Kibic';
}

/** Paleta awatarów forum — musi odpowiadać --avatar-* w styles.scss. */
const AVATAR_PALETTE = ['#1E8A3C', '#D91E2A', '#E0A400', '#2f74d0', '#9b59b6', '#0f8f86'];

/**
 * Deterministyczny awatar z nicku: inicjał = pierwsza litera etykiety autora,
 * kolor = stabilny wybór z palety po prostym hashu nazwy. Bez backendu, ten sam
 * nick zawsze daje ten sam kolor.
 */
export function avatarFor(displayName?: string): { initial: string; color: string } {
  const label = authorLabel(displayName);
  const initial = label.charAt(0).toUpperCase();
  let hash = 0;
  for (let i = 0; i < label.length; i++) {
    hash = (hash * 31 + label.charCodeAt(i)) | 0;
  }
  const color = AVATAR_PALETTE[Math.abs(hash) % AVATAR_PALETTE.length];
  return { initial, color };
}
