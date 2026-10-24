import { AboutSettingsSection } from '@/components/about-settings-section';
import { AppearanceSettingsSection } from '@/components/appearance-settings-section';
import { BlockedSettingsSection } from '@/components/blocked-settings-section';
import { ConnectionSettingsSection } from '@/components/connection-settings-section';
import { NotificationSettingsSection } from '@/components/notification-settings-section';
import { PrivacySettingsSection } from '@/components/privacy-settings-section';
import { SecuritySettingsSection } from '@/components/security-settings-section';
import type { ConnectionSnapshot } from '@/components/session-screen';

type SectionRenderProps = {
  section: string;
  connection: ConnectionSnapshot;
  onCopy: (value: string) => void;
};

export const SETTINGS_SECTION_LABELS: Record<string, string> = {
  privacidad: 'Privacidad',
  seguridad: 'Seguridad',
  bloqueados: 'Bloqueados',
  notificaciones: 'Notificaciones',
  apariencia: 'Apariencia',
  almacenamiento: 'Almacenamiento',
  red: 'Conexión',
  acerca: 'Acerca de',
};

export function renderSettingsSection({ section, connection, onCopy }: SectionRenderProps) {
  if (section === 'red') return <ConnectionSettingsSection {...connection} onCopy={onCopy} />;
  if (section === 'seguridad') return <SecuritySettingsSection />;
  if (section === 'bloqueados') return <BlockedSettingsSection />;
  if (section === 'privacidad') return <PrivacySettingsSection />;
  if (section === 'notificaciones') return <NotificationSettingsSection />;
  if (section === 'apariencia') return <AppearanceSettingsSection />;
  if (section === 'acerca') return <AboutSettingsSection />;
  return null;
}
