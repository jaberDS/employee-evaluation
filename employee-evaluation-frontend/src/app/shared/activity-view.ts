import { ActiviteLog } from '../services/activite.service';
import { IconName } from './lucide-icon/lucide-icon.component';

export interface ActivityView {
  id: number;
  type: string;
  icon: IconName;
  text: string;
  time: string;
  color: string;
}

const TYPE_META: Record<string, { icon: IconName; color: string }> = {
  EMPLOYE_CREE:            { icon: 'user-plus',       color: '#8B0000' },
  EMPLOYE_MODIFIE:         { icon: 'user-cog',        color: '#7C3AED' },
  EMPLOYE_SUPPRIME:        { icon: 'user-x',          color: '#DC2626' },
  CAMPAGNE_CREEE:          { icon: 'megaphone',       color: '#16A34A' },
  CAMPAGNE_MODIFIEE:       { icon: 'pen-square',      color: '#16A34A' },
  CAMPAGNE_SUPPRIMEE:      { icon: 'trash-2',         color: '#DC2626' },
  CAMPAGNE_OUVERTE:        { icon: 'rocket',          color: '#16A34A' },
  CAMPAGNE_FERMEE:         { icon: 'lock',            color: '#F59E0B' },
  CAMPAGNE_CLOTUREE:       { icon: 'check-circle',    color: '#0284C7' },
  QUESTION_AJOUTEE:        { icon: 'list-plus',       color: '#0284C7' },
  FICHE_EVALUEE_N1:        { icon: 'clipboard-check', color: '#0284C7' },
  FICHE_VALIDEE_N2:        { icon: 'check-circle',    color: '#16A34A' },
  FICHE_REFUSEE_N2:        { icon: 'x-circle',        color: '#DC2626' },
  FICHE_ACCEPTEE_EMPLOYE:  { icon: 'thumbs-up',       color: '#16A34A' },
  FICHE_REFUSEE_EMPLOYE:   { icon: 'thumbs-down',     color: '#F59E0B' }
};

const DEFAULT_META: { icon: IconName; color: string } = { icon: 'activity', color: '#8B0000' };

export function relativeTime(iso: string): string {
  const date = new Date(iso);
  const diffMs = Date.now() - date.getTime();
  const diffMin = Math.round(diffMs / 60000);
  if (diffMin < 1) return "À l'instant";
  if (diffMin < 60) return `Il y a ${diffMin} min`;
  const diffH = Math.round(diffMin / 60);
  if (diffH < 24) return `Il y a ${diffH}h`;
  const diffD = Math.round(diffH / 24);
  if (diffD === 1) return 'Hier';
  if (diffD < 30) return `Il y a ${diffD}j`;
  const diffMo = Math.round(diffD / 30);
  return `Il y a ${diffMo} mois`;
}

export function toActivityView(a: ActiviteLog): ActivityView {
  const meta = TYPE_META[a.type] ?? DEFAULT_META;
  const actor = a.acteurNom ? ` · ${a.acteurNom}` : '';
  return {
    id: a.id,
    type: a.type,
    icon: meta.icon,
    color: meta.color,
    text: a.description + actor,
    time: relativeTime(a.createdAt)
  };
}
