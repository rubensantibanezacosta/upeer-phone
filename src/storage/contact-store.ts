import * as SQLite from 'expo-sqlite';

import { deleteConversation as deleteNativeConversation, setBlockedPeers, setKnownContacts } from '../../modules/upeer-transport/src/UPeerTransportModule';

export type UPeerContact = {
  upeerId: string;
  address: string;
  name: string;
  publicKey: string | null;
  status: 'pending' | 'incoming' | 'connected' | 'offline' | 'blocked';
  createdAt: number;
  isFavorite?: boolean;
};

type ContactRow = Omit<UPeerContact, 'isFavorite'> & { isFavorite: number };

let database: Promise<SQLite.SQLiteDatabase> | null = null;
let databaseGeneration = 0;

async function getDatabase(): Promise<SQLite.SQLiteDatabase> {
  if (!database) {
    const generation = databaseGeneration;
    database = SQLite.openDatabaseAsync('upeer.db').then(async (db) => {
    await db.execAsync('PRAGMA journal_mode = WAL; CREATE TABLE IF NOT EXISTS contacts (upeerId TEXT PRIMARY KEY NOT NULL, address TEXT NOT NULL, name TEXT NOT NULL, publicKey TEXT, status TEXT NOT NULL, createdAt INTEGER NOT NULL, isFavorite INTEGER NOT NULL DEFAULT 0);');
    const columns = await db.getAllAsync<{ name: string }>('PRAGMA table_info(contacts)');
    if (!columns.some((column) => column.name === 'isFavorite')) {
      await db.execAsync('ALTER TABLE contacts ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0');
    }
    if (generation !== databaseGeneration) {
      await db.closeAsync();
      throw new Error('La sesión cambió durante la apertura de la base de contactos.');
    }
    return db;
  });
  }
  return database;
}

export async function getContacts(): Promise<UPeerContact[]> {
  const db = await getDatabase();
  const rows = await db.getAllAsync<ContactRow>('SELECT * FROM contacts ORDER BY name COLLATE NOCASE, createdAt DESC');
  return rows.map((row) => ({ ...row, isFavorite: row.isFavorite === 1 }));
}

export async function setContactFavorite(upeerId: string, isFavorite: boolean): Promise<void> {
  const db = await getDatabase();
  await db.runAsync('UPDATE contacts SET isFavorite = ? WHERE upeerId = ?', isFavorite ? 1 : 0, upeerId);
}

export async function pushBlockedPeers(): Promise<void> {
  await syncBlockedPeers().catch((cause: unknown) => console.warn('No se pudo enviar la lista de bloqueados al servicio.', cause));
}

async function syncBlockedPeers(): Promise<void> {
  const contacts = await getContacts();
  const blocked = contacts.filter((contact) => contact.status === 'blocked').map((contact) => contact.upeerId);
  await setBlockedPeers(JSON.stringify(blocked));
  await setKnownContacts(JSON.stringify(contacts.map((contact) => contact.upeerId)));
}

export async function saveContact(contact: UPeerContact): Promise<void> {
  const db = await getDatabase();
  await db.runAsync('INSERT INTO contacts (upeerId, address, name, publicKey, status, createdAt) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT(upeerId) DO UPDATE SET address = excluded.address, name = excluded.name, publicKey = excluded.publicKey, status = excluded.status', contact.upeerId, contact.address, contact.name, contact.publicKey, contact.status, contact.createdAt);
  await syncBlockedPeers().catch((cause: unknown) => console.warn('El contacto se guardó, pero el servicio no recibió la lista de bloqueados.', cause));
}

export async function deleteContact(upeerId: string): Promise<void> {
  const db = await getDatabase();
  await db.runAsync('DELETE FROM contacts WHERE upeerId = ?', upeerId);
  await syncBlockedPeers().catch((cause: unknown) => console.warn('El contacto se eliminó, pero el servicio no recibió la lista de bloqueados.', cause));
}

export async function deleteContactAndConversation(upeerId: string): Promise<void> {
  await deleteContact(upeerId);
  try {
    await deleteNativeConversation(upeerId);
  } catch (cause) {
    console.warn('El contacto se eliminó, pero la conversación nativa no se pudo purgar.', cause);
  }
}

export async function clearContacts(): Promise<void> {
  const db = await getDatabase();
  await db.execAsync('DELETE FROM contacts');
}

export async function deleteContactDatabase(): Promise<void> {
  databaseGeneration += 1;
  const pendingDatabase = database;
  database = null;
  let closeError: unknown = null;
  if (pendingDatabase) {
    try {
      const db = await pendingDatabase;
      await db.closeAsync();
    } catch (cause) {
      closeError = cause;
    }
  }
  await SQLite.deleteDatabaseAsync('upeer.db');
  if (closeError) console.warn('La base de contactos se borró, pero la conexión SQLite anterior no se cerró correctamente.', closeError);
}