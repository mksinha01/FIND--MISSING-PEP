export interface NotificationItem {
  id: string;
  user_id: string;
  title: string;
  body: string;
  sighting_id?: string | null;
  is_read: boolean;
  data?: Record<string, unknown> | null;
  created_at: string;
}

export interface NotificationListResponse {
  items: NotificationItem[];
  total: number;
  unread_count: number;
}
