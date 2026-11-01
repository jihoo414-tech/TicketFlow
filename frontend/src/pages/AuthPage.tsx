import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useMember } from '../member';
import { toApiError } from '../useLoad';

type Mode = 'login' | 'signup';

const inputClass = 'w-full rounded-lg border border-gray-300 px-3 py-2 outline-none focus:border-violet-500';

export default function AuthPage({ mode }: { mode: Mode }) {
  const { login, signup } = useMember();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      if (mode === 'login') {
        await login(email, password);
      } else {
        await signup(email, password, name);
      }
      navigate(from, { replace: true });
    } catch (caught) {
      setError(toApiError(caught).message);
    } finally {
      setSubmitting(false);
    }
  };

  const isLogin = mode === 'login';

  return (
    <section className="mx-auto max-w-sm">
      <h1 className="mb-6 text-2xl font-bold">{isLogin ? '로그인' : '회원가입'}</h1>
      <form onSubmit={submit} className="space-y-4 rounded-xl border border-gray-200 bg-white p-6">
        <label className="block space-y-1 text-sm">
          <span className="text-gray-700">이메일</span>
          <input type="email" required autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} className={inputClass} />
        </label>
        <label className="block space-y-1 text-sm">
          <span className="text-gray-700">비밀번호{isLogin ? '' : ' (8자 이상)'}</span>
          <input
            type="password"
            required
            minLength={isLogin ? undefined : 8}
            autoComplete={isLogin ? 'current-password' : 'new-password'}
            value={password}
            onChange={event => setPassword(event.target.value)}
            className={inputClass}
          />
        </label>
        {!isLogin && (
          <label className="block space-y-1 text-sm">
            <span className="text-gray-700">이름</span>
            <input required maxLength={50} autoComplete="name" value={name} onChange={event => setName(event.target.value)} className={inputClass} />
          </label>
        )}
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-lg bg-violet-600 py-2.5 font-semibold text-white hover:bg-violet-700 disabled:cursor-not-allowed disabled:bg-gray-300"
        >
          {submitting ? '처리 중...' : isLogin ? '로그인' : '가입하기'}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-gray-500">
        {isLogin ? '아직 회원이 아니신가요? ' : '이미 가입하셨나요? '}
        <Link to={isLogin ? '/signup' : '/login'} state={{ from }} className="font-semibold text-violet-700">
          {isLogin ? '회원가입' : '로그인'}
        </Link>
      </p>
    </section>
  );
}
