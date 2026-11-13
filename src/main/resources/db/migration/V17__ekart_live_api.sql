-- V17: Ekart moves from its rate card to the live Ekart Elite API (EkartPartnerClient, partner 10).
-- LivePartnerQuoteProvider already skips rate cards of live partners; this also retires the card in the DB so
-- nothing else (admin views, a future switch back) treats it as current. Pincode rows are kept (website lookup).
UPDATE SO_RATE_CARD SET STATUS = 'RETIRED', UPDATED_AT = CURRENT_TIMESTAMP
WHERE STATUS = 'ACTIVE'
  AND SERVICE_ID IN (SELECT ID FROM SO_RATE_SERVICE WHERE PARTNER_ID = 10);

UPDATE SO_RATE_SERVICE SET ENABLED = FALSE WHERE PARTNER_ID = 10;
