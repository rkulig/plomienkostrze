import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';

import { AdminStatus } from './auth/admin-status';
import { AuthService } from './auth/auth-service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  private readonly authService = inject(AuthService);
  private readonly adminStatus = inject(AdminStatus);
  private readonly router = inject(Router);

  protected readonly title = signal('Płomień Kostrze');
  protected readonly user = this.authService.user;
  protected readonly isAdmin = this.adminStatus.isAdmin;

  protected signIn(): void {
    this.authService.signInWithGoogle();
  }

  protected signOut(): void {
    this.authService.signOut().then(() => this.router.navigate(['']));
  }
}
