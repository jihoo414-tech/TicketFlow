import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { api, setApiMemberId, type Member } from './api';

const STORAGE_KEY = 'ticketflow.memberId';

type MemberContextValue = {
  members: Member[];
  memberId: number | null;
  currentMember: Member | null;
  memberError: boolean;
  ready: boolean;
  failed: boolean;
  selectMember: (id: number) => void;
};

const MemberContext = createContext<MemberContextValue | null>(null);

function readStoredMemberId(): number | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    return value ? Number(value) : null;
  } catch {
    return null;
  }
}

function storeMemberId(id: number) {
  try {
    localStorage.setItem(STORAGE_KEY, String(id));
  } catch {
    return;
  }
}

export function MemberProvider({ children }: { children: ReactNode }) {
  const [members, setMembers] = useState<Member[]>([]);
  const [memberId, setMemberId] = useState<number | null>(null);
  const [currentMember, setCurrentMember] = useState<Member | null>(null);
  const [memberError, setMemberError] = useState(false);
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    api.members()
      .then(loaded => {
        setMembers(loaded);
        const stored = readStoredMemberId();
        const initial = loaded.find(member => member.id === stored) ?? loaded[0];
        if (initial) {
          setApiMemberId(initial.id);
          setMemberId(initial.id);
        }
      })
      .catch(() => setFailed(true))
      .finally(() => setReady(true));
  }, []);

  useEffect(() => {
    let active = true;
    setCurrentMember(null);
    setMemberError(false);
    if (memberId !== null) {
      api.me().then(member => {
        if (active) setCurrentMember(member);
      }).catch(() => {
        if (active) setMemberError(true);
      });
    }
    return () => { active = false; };
  }, [memberId]);

  const selectMember = (id: number) => {
    setApiMemberId(id);
    storeMemberId(id);
    setMemberId(id);
  };

  return (
    <MemberContext.Provider value={{ members, memberId, currentMember, memberError, ready, failed, selectMember }}>
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
