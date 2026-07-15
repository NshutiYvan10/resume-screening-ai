import { createContext, useCallback, useContext, useEffect, useState, ReactNode } from 'react';
import { api, setAuthFailureHandler, tokenStore } from '../lib/api';
import type { AuthResponse, User } from '../types';

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<User>;
  applySession: (auth: AuthResponse) => void;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  setUser: (user: User) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const logout = useCallback(async () => {
    try {
      if (tokenStore.refresh) await api.post('/auth/logout', { refreshToken: tokenStore.refresh });
    } catch {
      /* ignore */
    }
    tokenStore.clear();
    setUser(null);
  }, []);

  useEffect(() => {
    setAuthFailureHandler(() => {
      tokenStore.clear();
      setUser(null);
    });
  }, []);

  const refreshUser = useCallback(async () => {
    if (!tokenStore.access) {
      setUser(null);
      return;
    }
    try {
      const { data } = await api.get<User>('/auth/me');
      setUser(data);
    } catch {
      setUser(null);
    }
  }, []);

  useEffect(() => {
    (async () => {
      await refreshUser();
      setLoading(false);
    })();
  }, [refreshUser]);

  const login = useCallback(async (email: string, password: string) => {
    const { data } = await api.post<AuthResponse>('/auth/login', { email, password });
    tokenStore.set(data.accessToken, data.refreshToken);
    setUser(data.user);
    return data.user;
  }, []);

  const applySession = useCallback((auth: AuthResponse) => {
    tokenStore.set(auth.accessToken, auth.refreshToken);
    setUser(auth.user);
  }, []);

  return (
    <AuthContext.Provider
      value={{ user, loading, login, applySession, logout, refreshUser, setUser }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
