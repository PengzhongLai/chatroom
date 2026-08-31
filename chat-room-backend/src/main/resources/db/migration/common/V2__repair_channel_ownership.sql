-- The channel row is the source of truth for ownership. Legacy versions could
-- transfer only the member role and leave channels.creator_id unchanged.
INSERT INTO channel_members (
    channel_id,
    user_id,
    role,
    history_level,
    history_limit,
    joined_at
)
SELECT
    c.id,
    c.creator_id,
    'CREATOR',
    'ALL',
    NULL,
    c.created_at
FROM channels c
WHERE NOT EXISTS (
    SELECT 1
    FROM channel_members cm
    WHERE cm.channel_id = c.id
      AND cm.user_id = c.creator_id
);

UPDATE channel_members cm
SET role = 'ADMIN'
WHERE cm.role = 'CREATOR'
  AND cm.user_id <> (
      SELECT c.creator_id
      FROM channels c
      WHERE c.id = cm.channel_id
  );

UPDATE channel_members cm
SET role = 'CREATOR'
WHERE cm.user_id = (
    SELECT c.creator_id
    FROM channels c
    WHERE c.id = cm.channel_id
);
