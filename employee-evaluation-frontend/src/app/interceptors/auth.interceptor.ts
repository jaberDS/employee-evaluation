import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError, BehaviorSubject } from 'rxjs';
import { catchError, filter, take, switchMap } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthInterceptor implements HttpInterceptor {
  private isRefreshing = false;
  private refreshTokenSubject = new BehaviorSubject<string | null>(null);

  /**
   * Endpoints où un 401 ne veut PAS dire « session expirée ».
   *
   * Une cérémonie de second facteur porte son propre jeton d'étape : elle peut
   * échouer alors que la session applicative reste parfaitement valable. Y
   * répondre par un rafraîchissement puis une déconnexion renvoyait
   * l'utilisateur à l'écran de connexion au moindre échec de vivacité, sans
   * même qu'il puisse lire le message d'erreur.
   */
  private readonly ceremonyPaths = ['/auth/', '/mfa/'];

  constructor(private authService: AuthService) {}

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = this.authService.getToken();
    let authReq = request;

    if (token) {
      authReq = this.addTokenToRequest(request, token);
    }

    return next.handle(authReq).pipe(
      catchError((error: HttpErrorResponse) => {
        // Only attempt token refresh for real auth failures (401 on non-login endpoints).
        // 403 = business rule violation (not an auth issue) — pass through directly.
        if (error.status === 401 && !this.isCeremony(authReq.url)) {
          return this.handle401Error(authReq, next);
        }
        return throwError(() => error);
      })
    );
  }

  private isCeremony(url: string): boolean {
    return this.ceremonyPaths.some(chemin => url.includes(chemin));
  }

  private addTokenToRequest(request: HttpRequest<any>, token: string): HttpRequest<any> {
    return request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  private handle401Error(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!this.isRefreshing) {
      this.isRefreshing = true;
      this.refreshTokenSubject.next(null);

      return this.authService.refreshToken().pipe(
        switchMap((response) => {
          this.isRefreshing = false;
          this.refreshTokenSubject.next(response.token);
          return next.handle(this.addTokenToRequest(request, response.token));
        }),
        catchError((error) => {
          this.isRefreshing = false;
          this.authService.logout();
          return throwError(() => error);
        })
      );
    } else {
      return this.refreshTokenSubject.pipe(
        filter(token => token !== null),
        take(1),
        switchMap(token => {
          return next.handle(this.addTokenToRequest(request, token!));
        })
      );
    }
  }
}
