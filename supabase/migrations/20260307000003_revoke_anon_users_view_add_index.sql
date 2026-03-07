-- Revoke unnecessary anon SELECT on users_with_roles (only admins need this)
REVOKE SELECT ON public.users_with_roles FROM anon;

-- Ensure plugins_with_latest_version grants are explicit
GRANT SELECT ON public.plugins_with_latest_version TO authenticated;
GRANT SELECT ON public.plugins_with_latest_version TO anon;

-- Covering index for deterministic latest-version ordering
CREATE INDEX IF NOT EXISTS idx_plugin_versions_latest
    ON public.plugin_versions(plugin_id, published_at DESC, id DESC);
