import { Component, Input } from '@angular/core';
import { TypeAffectation } from '../../models/evaluation.model';

@Component({
  selector: 'app-affectation-badge',
  templateUrl: './affectation-badge.component.html',
  styleUrls: ['./affectation-badge.component.css']
})
export class AffectationBadgeComponent {
  @Input() type: TypeAffectation | null | undefined;
  @Input() size: 'sm' | 'md' = 'md';

  get label(): string {
    return this.type === 'AGENCE' ? 'Agence' : 'Siège';
  }

  get icon(): 'building' | 'landmark' {
    return this.type === 'AGENCE' ? 'building' : 'landmark';
  }
}
