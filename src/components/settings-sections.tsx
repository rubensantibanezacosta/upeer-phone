import { AboutSettingsSection } from '@/components/about-settings-section';
import { AppearanceSettingsSection } from '@/components/appearance-settings-section';
import { BlockedSettingsSection } from '@/components/blocked-settings-section';
import { ConnectionSettingsSection } from '@/components/connection-settings-section';
import { DevicesSettingsSection } from '@/components/devices-settings-section';
import { NotificationSettingsSection } from '@/components/notification-settings-section';
import { PrivacySettingsSection } from '@/components/privacy-settings-section';
import { SecuritySettingsSection } from '@/components/security-settings-section';
import { StorageSettingsSection } from '@/components/storage-settings-section';
import type { ConnectionSnapshot } from '@/components/session-screen';
import type { UPeerIdentity } from '@/domain/identity-record';

type SectionRenderProps = {
  section: string;
  connection: ConnectionSnapshot;
  identity: UPeerIdentity;
  onCopy: (value: string) => void;
};

export const SETTINGS_SECTION_LABELS: Record<string, string> = {
  privacidad: 'Privacidad',
  seguridad: 'Seguridad',
  dispositivos: 'Dispositivos',
  bloqueados: 'Bloqueados',
  notificaciones: 'Notificaciones',
  apariencia: 'Apariencia',
  almacenamiento: 'Almacenamiento',
  red: 'Conexión',
  acerca: 'Acerca de',
};

export function renderSettingsSection({ section, connection, identity, onCopy }: SectionRenderProps) {
  if (section === 'red') return <ConnectionSettingsSection {...connection} onCopy={onCopy} />;
  if (section === 'dispositivos') return <DevicesSettingsSection />;
  if (section === 'bloqueados') return <BlockedSettingsSection />;
  if (section === 'privacidad') return <PrivacySettingsSection />;
  if (section === 'seguridad') return <SecuritySettingsSection identity={identity} />;
  if (section === 'notificaciones') return <NotificationSettingsSection />;
  if (section === 'almacenamiento') return <StorageSettingsSection />;
  if (section === 'apariencia') return <AppearanceSettingsSection />;
  if (section === 'acerca') return <AboutSettingsSection />;
  return null;
}
