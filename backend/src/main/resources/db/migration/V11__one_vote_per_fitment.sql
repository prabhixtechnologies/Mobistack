-- One person, one open vote on a fitment. Extra clicks had each incremented the counter.

DELETE FROM catalog_contributions
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               row_number() OVER (
                   PARTITION BY submitted_by, target_id, kind
                   ORDER BY created_at, id
               ) AS duplicate_number
        FROM catalog_contributions
        WHERE kind IN ('CONFIRM_FITMENT', 'DISPUTE_FITMENT')
          AND status IN ('PENDING', 'APPLIED')
          AND target_id IS NOT NULL
    ) ranked
    WHERE duplicate_number > 1
);

UPDATE catalog_fitments AS fitment
SET confirmations = counted.votes
FROM (
    SELECT fitment_row.id,
           (
               SELECT count(*)
               FROM catalog_contributions vote
               WHERE vote.target_id = fitment_row.id
                 AND vote.kind = 'CONFIRM_FITMENT'
                 AND vote.status = 'APPLIED'
           ) AS votes
    FROM catalog_fitments fitment_row
) AS counted
WHERE fitment.id = counted.id
  AND fitment.confirmations <> counted.votes;

UPDATE catalog_fitments AS fitment
SET disputes = counted.votes
FROM (
    SELECT fitment_row.id,
           (
               SELECT count(*)
               FROM catalog_contributions vote
               WHERE vote.target_id = fitment_row.id
                 AND vote.kind = 'DISPUTE_FITMENT'
                 AND vote.status = 'APPLIED'
           ) AS votes
    FROM catalog_fitments fitment_row
) AS counted
WHERE fitment.id = counted.id
  AND fitment.disputes <> counted.votes;

CREATE UNIQUE INDEX ux_catalog_vote_once
    ON catalog_contributions (submitted_by, target_id, kind)
    WHERE kind IN ('CONFIRM_FITMENT', 'DISPUTE_FITMENT')
      AND status IN ('PENDING', 'APPLIED')
      AND target_id IS NOT NULL;
