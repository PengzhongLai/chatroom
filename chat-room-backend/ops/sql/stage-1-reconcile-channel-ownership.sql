-- One-time Stage 1 reconciliation based on ADR 0001.
-- Precondition: the Stage 0 read-only audit has confirmed that every affected
-- channel has exactly one CREATOR mirror and that it represents an earlier
-- ownership transfer performed by the legacy service.

START TRANSACTION;

-- Lock every mismatched channel and its ownership rows before updating.
SELECT c.id,
       c.creator_id AS previous_creator_id,
       previous_owner.role AS previous_creator_role,
       intended_owner.user_id AS intended_creator_id
FROM channels c
JOIN channel_members previous_owner
  ON previous_owner.channel_id = c.id
 AND previous_owner.user_id = c.creator_id
JOIN channel_members intended_owner
  ON intended_owner.channel_id = c.id
 AND intended_owner.role = 'CREATOR'
 AND intended_owner.user_id <> c.creator_id
ORDER BY c.id
FOR UPDATE;

-- A channel is changed only when it has one and only one CREATOR mirror.
-- The old authoritative owner remains a member and becomes ADMIN.
UPDATE channels c
JOIN channel_members previous_owner
  ON previous_owner.channel_id = c.id
 AND previous_owner.user_id = c.creator_id
JOIN channel_members intended_owner
  ON intended_owner.channel_id = c.id
 AND intended_owner.role = 'CREATOR'
 AND intended_owner.user_id <> c.creator_id
LEFT JOIN channel_members conflicting_creator
  ON conflicting_creator.channel_id = c.id
 AND conflicting_creator.role = 'CREATOR'
 AND conflicting_creator.id <> intended_owner.id
SET c.creator_id = intended_owner.user_id,
    previous_owner.role = 'ADMIN'
WHERE conflicting_creator.id IS NULL
  AND previous_owner.role <> 'CREATOR';

SELECT ROW_COUNT() AS changed_rows;

-- This result set must be empty before the transaction is considered valid.
SELECT c.id,
       c.creator_id,
       authoritative_member.role AS authoritative_member_role,
       SUM(CASE WHEN members.role = 'CREATOR' THEN 1 ELSE 0 END) AS creator_count
FROM channels c
LEFT JOIN channel_members authoritative_member
  ON authoritative_member.channel_id = c.id
 AND authoritative_member.user_id = c.creator_id
LEFT JOIN channel_members members
  ON members.channel_id = c.id
GROUP BY c.id, c.creator_id, authoritative_member.role
HAVING authoritative_member.role IS NULL
    OR authoritative_member.role <> 'CREATOR'
    OR creator_count <> 1;

COMMIT;
