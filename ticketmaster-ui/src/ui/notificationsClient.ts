import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client/dist/sockjs';
import type { AssignedRoleDTO, NotificationDTO } from '../api/types';
import { useNotificationsStore } from './notificationsStore';

let client: Client | null = null;
let connectedUserId: string | null = null;

function topicsForRoles(userId: string, roles: AssignedRoleDTO[] | undefined) {
  const topics = new Set<string>();

  // Personal topic: required for delayed delivery replay.
  topics.add(`/topic/user/${userId}`);

  for (const r of roles ?? []) {
    if (r.roleName === 'Manager' && r.eventId) {
      topics.add(`/topic/event/${r.eventId}/managers`);
    }
    if ((r.roleName === 'Owner' || r.roleName === 'Founder') && r.companyId) {
      topics.add(`/topic/company/${r.companyId}/managers`);
    }
  }

  return Array.from(topics);
}

export function connectNotifications(userId: string, assignedRoles: AssignedRoleDTO[] | undefined) {
  if (!userId) return;

  const sameUser = connectedUserId === userId;
  if (client && sameUser && client.connected) {
    return;
  }

  disconnectNotifications();

  const store = useNotificationsStore.getState();

  const next = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 3000,
    debug: () => {},
    onConnect: () => {
      connectedUserId = userId;
      const topics = topicsForRoles(userId, assignedRoles);
      for (const topic of topics) {
        next.subscribe(topic, (msg: IMessage) => {
          try {
            const raw = JSON.parse(msg.body) as NotificationDTO;
            if (raw && raw.title && raw.message) {
              store.addNotification({
                type: raw.type,
                title: raw.title,
                message: raw.message,
                createdAt: raw.createdAt,
              });
            }
          } catch {
            // ignore
          }
        });
      }
    },
    onStompError: () => {
      // ignore
    },
  });

  client = next;
  next.activate();
}

export function disconnectNotifications() {
  connectedUserId = null;
  const c = client;
  client = null;
  if (c) {
    try {
      c.deactivate();
    } catch {
      // ignore
    }
  }
}
