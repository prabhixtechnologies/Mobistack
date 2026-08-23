-- Seeded accounts and synthetic phone users move onto the company domain.
UPDATE users
SET email = regexp_replace(email, '@fixflow\.app$', '@prabhixtechnologies.com', 'i')
WHERE email ~* '@fixflow\.app$';

UPDATE users
SET email = regexp_replace(email, '@phone\.fixflow\.local$', '@phone.prabhixtechnologies.local', 'i')
WHERE email ~* '@phone\.fixflow\.local$';

UPDATE user_tokens
SET email = regexp_replace(email, '@fixflow\.app$', '@prabhixtechnologies.com', 'i')
WHERE email ~* '@fixflow\.app$';

UPDATE user_identities
SET email = regexp_replace(email, '@fixflow\.app$', '@prabhixtechnologies.com', 'i')
WHERE email ~* '@fixflow\.app$';

UPDATE workspace_invitations
SET email = regexp_replace(email, '@fixflow\.app$', '@prabhixtechnologies.com', 'i')
WHERE email ~* '@fixflow\.app$';

UPDATE users
SET system_admin = TRUE
WHERE lower(email) = 'owner@prabhixtechnologies.com';
