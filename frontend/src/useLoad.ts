import { useCallback, useEffect, useState } from 'react';
import { ApiError } from './api';

export type LoadState<T> =
  | { status: 'loading' }
  | { status: 'error'; error: ApiError }
  | { status: 'done'; data: T };

export function useLoad<T>(load: () => Promise<T>, deps: unknown[]) {
  const [state, setState] = useState<LoadState<T>>({ status: 'loading' });
  const run = useCallback(load, deps);

  useEffect(() => {
    let active = true;
    setState({ status: 'loading' });
    run()
      .then(data => active && setState({ status: 'done', data }))
      .catch(error => active && setState({ status: 'error', error: toApiError(error) }));
    return () => {
      active = false;
    };
  }, [run]);

  return state;
}

export function toApiError(error: unknown): ApiError {
  return error instanceof ApiError ? error : new ApiError(0, undefined, '알 수 없는 오류가 발생했습니다.');
}
