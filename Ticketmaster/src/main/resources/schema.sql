CREATE UNIQUE INDEX IF NOT EXISTS uk_active_order_user_event_active
ON active_orders(user_id, event_id)
WHERE status = 'ACTIVE';