-- In-app inbox rows are also recorded on the outbox. V6 omitted INBOX.
ALTER TABLE notification_outbox
    DROP CONSTRAINT ck_outbox_channel;

ALTER TABLE notification_outbox
    ADD CONSTRAINT ck_outbox_channel
        CHECK (channel IN ('EMAIL', 'WHATSAPP', 'SMS', 'PUSH', 'LOG', 'INBOX'));
