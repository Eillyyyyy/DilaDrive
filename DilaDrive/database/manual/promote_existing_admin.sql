-- First run this query to identify the existing account to promote:
-- SELECT admin_id, admin_name, admin_email, admin_role, admin_status
-- FROM public.admin
-- ORDER BY admin_id;

-- Replace 0 with the admin_id of the intended existing account.
-- The default 0 intentionally updates no account.
UPDATE public.admin
SET admin_role = 'SUPER_ADMIN'
WHERE admin_id = 0
  AND UPPER(COALESCE(admin_role, 'ADMIN')) = 'ADMIN'
RETURNING admin_id, admin_name, admin_email, admin_role;
