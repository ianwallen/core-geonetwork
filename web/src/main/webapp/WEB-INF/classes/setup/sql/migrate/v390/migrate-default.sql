-- 3.8.2
UPDATE Sources SET type = 'portal' WHERE type IS null AND uuid = (SELECT value FROM settings WHERE name = 'system/site/siteId');
UPDATE Sources SET type = 'harvester' WHERE type IS null AND uuid != (SELECT value FROM settings WHERE name = 'system/site/siteId');

INSERT INTO StatusValues (id, name, reserved, displayorder, type, notificationLevel) VALUES  (63,'recordrestored','y', 63, 'event', null);

UPDATE Settings SET internal = 'y' WHERE name = 'system/publication/doi/doipassword';

UPDATE Settings SET value='3.9.0' WHERE name='system/platform/version';
UPDATE Settings SET value='SNAPSHOT' WHERE name='system/platform/subVersion';
