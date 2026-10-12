import { create } from "zustand";

interface User {
  id: string;
  email?: string;
  name?: string;
  profile_image_url?: string;
  provider?: string;
}

interface AuthState {
  isAuthenticated: boolean;
  user: User | null;
  isLoading: boolean;
  checkAuth: () => Promise<void>;
  login: (provider: "google" | "naver" | "kakao") => void;
  logout: () => Promise<void>;
  setToken: (token: string) => void;
}

// Get API base URL from environment or use relative URL
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";

const TOKEN_KEY = "auth_token";
const NATIVE_SERVICE_JWT_AUTH = "JWTAuth";
const NATIVE_ACTION_GET_TOKEN = "getToken";
const NATIVE_ACTION_UPDATE_TOKEN = "updateToken";
const NATIVE_ACTION_LOGOUT = "logout";

type NativeCallbackResult = {
  requestId?: string;
  resultCode?: string;
  params?: unknown;
};

declare global {
  interface Window {
    CodeEngineBridge?: {
      postMessage: (payload: string) => void;
    };
    callbackListener?: (result: NativeCallbackResult) => void;
  }
}

const nativePendingCallbacks = new Map<
  string,
  (result: NativeCallbackResult) => void
>();
let nativeBridgeInitialized = false;

function initNativeBridgeCallbacks() {
  if (nativeBridgeInitialized) return;
  nativeBridgeInitialized = true;

  const previous = window.callbackListener;
  window.callbackListener = (result: NativeCallbackResult) => {
    const requestId = result?.requestId;
    if (requestId && nativePendingCallbacks.has(requestId)) {
      const cb = nativePendingCallbacks.get(requestId);
      nativePendingCallbacks.delete(requestId);
      cb?.(result);
      return;
    }
    previous?.(result);
  };
}

function hasNativeBridge(): boolean {
  return typeof window.CodeEngineBridge?.postMessage === "function";
}

function nativePostCommand(
  serviceName: string,
  action: string,
  params: Record<string, unknown> = {},
): Promise<NativeCallbackResult> {
  if (!hasNativeBridge()) {
    return Promise.reject(new Error("Native bridge unavailable"));
  }
  initNativeBridgeCallbacks();

  const requestId = `${serviceName}.${action}.${Date.now()}.${Math.random().toString(36).slice(2, 8)}`;
  const command = { serviceName, action, requestId, params };

  return new Promise((resolve, reject) => {
    const timeout = window.setTimeout(() => {
      nativePendingCallbacks.delete(requestId);
      reject(new Error(`Native bridge timeout: ${serviceName}.${action}`));
    }, 4000);

    nativePendingCallbacks.set(requestId, (result) => {
      window.clearTimeout(timeout);
      resolve(result);
    });

    try {
      window.CodeEngineBridge!.postMessage(JSON.stringify(command));
    } catch (e) {
      window.clearTimeout(timeout);
      nativePendingCallbacks.delete(requestId);
      reject(e instanceof Error ? e : new Error(String(e)));
    }
  });
}

function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

function authHeaders(): HeadersInit {
  const token = getStoredToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function syncTokenFromNativeIfNeeded(): Promise<void> {
  if (getStoredToken()) return;
  if (!hasNativeBridge()) return;

  try {
    const result = await nativePostCommand(NATIVE_SERVICE_JWT_AUTH, NATIVE_ACTION_GET_TOKEN);
    const params = typeof result.params === "string" ? JSON.parse(result.params) : result.params;
    const token = (params as { token?: string } | null)?.token;
    if (token && typeof token === "string") {
      localStorage.setItem(TOKEN_KEY, token);
    }
  } catch (e) {
    console.warn("Native token sync failed:", e);
  }
}

async function updateNativeToken(token: string): Promise<void> {
  if (!hasNativeBridge()) return;
  try {
    await nativePostCommand(NATIVE_SERVICE_JWT_AUTH, NATIVE_ACTION_UPDATE_TOKEN, { token });
  } catch (e) {
    console.warn("Native token update failed:", e);
  }
}

async function clearNativeToken(): Promise<void> {
  if (!hasNativeBridge()) return;
  try {
    await nativePostCommand(NATIVE_SERVICE_JWT_AUTH, NATIVE_ACTION_LOGOUT);
  } catch (e) {
    console.warn("Native logout failed:", e);
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  isAuthenticated: false,
  user: null,
  isLoading: true,

  setToken: (token: string) => {
    localStorage.setItem(TOKEN_KEY, token);
    void updateNativeToken(token);
  },

  checkAuth: async () => {
    try {
      await syncTokenFromNativeIfNeeded();
      const response = await fetch(`${API_BASE_URL}/api/auth/verify`, {
        credentials: "include",
        headers: authHeaders(),
      });

      if (response.ok) {
        const data = await response.json();
        set({
          isAuthenticated: true,
          user: data.user,
          isLoading: false,
        });
      } else {
        localStorage.removeItem(TOKEN_KEY);
        await clearNativeToken();
        set({
          isAuthenticated: false,
          user: null,
          isLoading: false,
        });
      }
    } catch (error) {
      console.error("Auth check failed:", error);
      set({
        isAuthenticated: false,
        user: null,
        isLoading: false,
      });
    }
  },

  login: async (provider: "google" | "naver" | "kakao") => {
    try {
      const res = await fetch(`${API_BASE_URL}/api/auth/${provider}/url`, {
        credentials: "include",
      });
      if (res.ok) {
        const data = await res.json();
        window.location.href = data.url;
        return;
      }
    } catch {
      // fall through
    }
    window.location.href = `${API_BASE_URL}/api/auth/${provider}/login`;
  },

  logout: async () => {
    try {
      await fetch(`${API_BASE_URL}/api/auth/logout`, {
        method: "POST",
        credentials: "include",
        headers: authHeaders(),
      });
    } catch (error) {
      console.error("Logout failed:", error);
    }
    localStorage.removeItem(TOKEN_KEY);
    await clearNativeToken();
    set({
      isAuthenticated: false,
      user: null,
    });
  },
}));
