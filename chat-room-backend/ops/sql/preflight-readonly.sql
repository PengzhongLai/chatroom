-- Stage 0 data preflight. This script is read-only and safe to rerun.
START TRANSACTION READ ONLY;

SELECT 'users' AS metric, COUNT(*) AS value FROM users
UNION ALL SELECT 'channels', COUNT(*) FROM channels
UNION ALL SELECT 'channel_members', COUNT(*) FROM channel_members
UNION ALL SELECT 'private_chats', COUNT(*) FROM private_chats
UNION ALL SELECT 'messages', COUNT(*) FROM messages
UNION ALL SELECT 'message_reads', COUNT(*) FROM message_reads;

SELECT COUNT(*) AS invalid_message_target_count
FROM messages
WHERE (channel_id IS NULL AND private_chat_id IS NULL)
   OR (channel_id IS NOT NULL AND private_chat_id IS NOT NULL);

SELECT channel_id, user_id, COUNT(*) AS duplicate_count
FROM channel_members
GROUP BY channel_id, user_id
HAVING COUNT(*) > 1;

SELECT message_id, user_id, COUNT(*) AS duplicate_count
FROM message_reads
GROUP BY message_id, user_id
HAVING COUNT(*) > 1;

SELECT LEAST(user1_id, user2_id) AS lower_user_id,
       GREATEST(user1_id, user2_id) AS higher_user_id,
       COUNT(*) AS duplicate_count
FROM private_chats
GROUP BY LEAST(user1_id, user2_id), GREATEST(user1_id, user2_id)
HAVING COUNT(*) > 1;

SELECT id, user1_id, user2_id, initiator_id
FROM private_chats
WHERE user1_id >= user2_id
   OR initiator_id NOT IN (user1_id, user2_id);

SELECT c.id AS channel_id, c.creator_id
FROM channels c
LEFT JOIN channel_members cm
  ON cm.channel_id = c.id
 AND cm.user_id = c.creator_id
WHERE cm.id IS NULL;

SELECT c.id AS channel_id,
       c.creator_id AS authoritative_creator_id,
       cm.role AS authoritative_member_role
FROM channels c
JOIN channel_members cm
  ON cm.channel_id = c.id
 AND cm.user_id = c.creator_id
WHERE cm.role <> 'CREATOR';

SELECT cm.channel_id,
       cm.user_id AS mirrored_creator_user_id,
       c.creator_id AS authoritative_creator_id
FROM channel_members cm
JOIN channels c ON c.id = cm.channel_id
WHERE cm.role = 'CREATOR'
  AND cm.user_id <> c.creator_id;

SELECT c.id AS channel_id,
       c.creator_id,
       SUM(CASE WHEN cm.role = 'CREATOR' THEN 1 ELSE 0 END) AS creator_count
FROM channels c
LEFT JOIN channel_members cm ON cm.channel_id = c.id
GROUP BY c.id, c.creator_id
HAVING creator_count <> 1;

SELECT COUNT(*) AS orphan_channel_creator_count
FROM channels c
LEFT JOIN users u ON u.id = c.creator_id
WHERE u.id IS NULL;

SELECT COUNT(*) AS orphan_member_count
FROM channel_members cm
LEFT JOIN channels c ON c.id = cm.channel_id
LEFT JOIN users u ON u.id = cm.user_id
WHERE c.id IS NULL OR u.id IS NULL;

SELECT COUNT(*) AS orphan_message_count
FROM messages m
LEFT JOIN users u ON u.id = m.sender_id
LEFT JOIN channels c ON c.id = m.channel_id
LEFT JOIN private_chats pc ON pc.id = m.private_chat_id
WHERE u.id IS NULL
   OR (m.channel_id IS NOT NULL AND c.id IS NULL)
   OR (m.private_chat_id IS NOT NULL AND pc.id IS NULL);

SELECT COUNT(*) AS orphan_read_count
FROM message_reads mr
LEFT JOIN messages m ON m.id = mr.message_id
LEFT JOIN users u ON u.id = mr.user_id
WHERE m.id IS NULL OR u.id IS NULL;

SELECT pc.id, COUNT(m.id) AS message_count
FROM private_chats pc
JOIN messages m ON m.private_chat_id = pc.id
WHERE pc.status = 'DELETED'
GROUP BY pc.id;

SELECT pc.id,
       COUNT(DISTINCT m.id) AS message_count,
       COUNT(mr.id) AS read_count
FROM private_chats pc
JOIN messages m ON m.private_chat_id = pc.id
LEFT JOIN message_reads mr ON mr.message_id = m.id
GROUP BY pc.id
HAVING read_count > 0;

ROLLBACK;
