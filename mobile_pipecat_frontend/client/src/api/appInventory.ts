/**
 * App Inventory API client.
 *
 * Provides typed helpers for querying the user's installed-app inventory
 * stored on the server by the Android native collector.
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";

function authHeaders(): HeadersInit {
  const token = localStorage.getItem("auth_token");
  return token ? { Authorization: `Bearer ${token}` } : {};
}

// ---- Types ----

export interface AppItem {
  package_name: string;
  label: string | null;
  category: string;
  usage_score: number | null;
  sort_order: number;
  is_system: boolean | null;
}

export interface AppInventorySnapshot {
  snapshot_id: string;
  device_id: string;
  platform: string;
  client_sorted: boolean;
  created_at: string;
  client_ts: string;
  apps: AppItem[];
}

// ---- API ----

export async function fetchAppInventory(
  category?: string
): Promise<AppInventorySnapshot | null> {
  const params = category ? `?category=${encodeURIComponent(category)}` : "";
  try {
    const res = await fetch(
      `${API_BASE_URL}/api/v1/users/me/app-inventory${params}`,
      {
        credentials: "include",
        headers: authHeaders(),
      }
    );
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch (err) {
    console.error("[AppInventory] fetch failed:", err);
    return null;
  }
}

export function groupByCategory(
  apps: AppItem[]
): Record<string, AppItem[]> {
  const groups: Record<string, AppItem[]> = {};
  for (const app of apps) {
    if (!groups[app.category]) groups[app.category] = [];
    groups[app.category].push(app);
  }
  return groups;
}
