import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, setApiAccessToken, setOnUnauthorized, type Member } from './api';

const STORAGE_KEY = 'ticketflow.accessToken';

type MemberContextValue = {
  memberId: number | null;
  currentMember: Member | null;
  ready: boolean;
  login: (email: string, password: string) => Promise<void>;
  signup: (email: string, password: string, name: string) => Promise<void>;
  logout: () => void;
};

const MemberContext = createContext<MemberContextValue | null>(null);

function readStoredToken(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function storeToken(token: string | null) {
  try {
    if (token === null) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, token);
    }
  } catch {
    return;
  }
}

export function MemberProvider({ children }: { children: ReactNode }) {
  const [currentMember, setCurrentMember] = useState<Member | null>(null);
  const [ready, setReady] = useState(false);

  const logout = useCallback(() => {
    storeToken(null);
    setApiAccessToken(null);
    setCurrentMember(null);
  }, []);

  const loadMe = useCallback(async () => {
    try {
      setCurrentMember(await api.me());
    } catch {
      logout();
    }
  }, [logout]);

  useEffect(() => {
    setOnUnauthorized(logout);
    const stored = readStoredToken();
    if (stored === null) {
      setReady(true);
      return;
    }
    setApiAccessToken(stored);
    loadMe().finally(() => setReady(true));
  }, [logout, loadMe]);

  const login = async (email: string, password: string) => {
    const { accessToken } = await api.login(email, password);
    storeToken(accessToken);
    setApiAccessToken(accessToken);
    await loadMe();
  };

  const signup = async (email: string, password: string, name: string) => {
    await api.signup(email, password, name);
    await login(email, password);
  };

  return (
    <MemberContext.Provider value={{ memberId: currentMember?.id ?? null, currentMember, ready, login, signup, logout }}>
      {children}
    </MemberContext.Provider>
  );
}

export function useMember(): MemberContextValue {
  const value = useContext(MemberContext);
  if (!value) {
    throw new Error('MemberProvider 안에서만 쓸 수 있습니다.');
  }
  return value;
}
