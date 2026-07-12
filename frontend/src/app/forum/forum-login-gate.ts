import { Component, inject } from '@angular/core';

import { AuthService } from '../auth/auth-service';

/**
 * Bramka logowania forum (S-07): całe forum jest za logowaniem, więc zamiast po
 * cichu odsyłać gościa, pokazujemy wprost, że forum wymaga konta, z przyciskiem
 * logowania. Klik to gest użytkownika, więc popup Google nie jest blokowany.
 */
@Component({
  selector: 'app-forum-login-gate',
  templateUrl: './forum-login-gate.html',
  styleUrl: './forum-login-gate.scss'
})
export class ForumLoginGate {
  private readonly authService = inject(AuthService);

  protected signIn(): void {
    this.authService.signInWithGoogle();
  }
}
