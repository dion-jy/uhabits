import { createClient } from "@supabase/supabase-js";

const url = import.meta.env.VITE_SUPABASE_URL;
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;

if (!url || !anonKey) {
  throw new Error(
    "Missing VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY. Copy .env.example to .env."
  );
}

// supabase-js persists the session in localStorage and auto-refreshes by default,
// and attaches apikey + the user's Bearer JWT to every PostgREST request.
export const supabase = createClient(url, anonKey);
