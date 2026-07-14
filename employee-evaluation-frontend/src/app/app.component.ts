import { Component, OnInit } from '@angular/core';
import { AuthService } from './services/auth.service';
import { ThemeService } from './core/services/theme.service';
import { ConfirmService } from './shared/confirm/confirm.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  constructor(
    public authService: AuthService,
    private themeService: ThemeService,
    public confirmService: ConfirmService
  ) {}

  ngOnInit(): void {
    this.themeService.init();
  }
}
