import { Injectable, signal } from '@angular/core';
import { initializeApp } from 'firebase/app';
import {
  GoogleAuthProvider,
  User,
  getAuth,
  onAuthStateChanged,
  signInWithPopup,
  signOut,
} from 'firebase/auth';

import { environment } from '../../environments/environment';

/**
 * Jedyne miejsce w SPA dotykające SDK Firebase (lustrzana konwencja do NewsApi
 * dla HTTP). Stan logowania jako sygnał: undefined = jeszcze nie wiadomo
 * (SDK się inicjalizuje), null = wylogowany, User = zalogowany.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly auth = getAuth(initializeApp(environment.firebase));

  readonly user = signal<User | null | undefined>(undefined);

  constructor() {
    onAuthStateChanged(this.auth, (user) => this.user.set(user));
  }

  // Popup zamiast signInWithRedirect: redirect z localhost jest cross-origin
  // względem authDomain i w przeglądarkach blokujących 3rd-party storage
  // gubi wynik logowania (fallback przewidziany w planie S-02).
  signInWithGoogle(): void {
    signInWithPopup(this.auth, new GoogleAuthProvider()).catch((err) =>
      console.error('Logowanie Google nie powiodło się', err),
    );
  }

  signOut(): Promise<void> {
    return signOut(this.auth);
  }

  async getIdToken(): Promise<string | null> {
    const user = this.auth.currentUser;
    return user ? user.getIdToken() : null;
  }
}
