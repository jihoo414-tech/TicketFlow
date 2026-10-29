import { useEffect } from 'react';

const DISMISS_MS = 4000;

export default function Toast({ message, onDismiss }: { message: string | null; onDismiss: () => void }) {
  useEffect(() => {
    if (!message) {
      return;
    }
    const timer = setTimeout(onDismiss, DISMISS_MS);
    return () => clearTimeout(timer);
  }, [message, onDismiss]);

  if (!message) {
    return null;
  }

  return (
    <div className="fixed inset-x-0 top-4 z-10 flex justify-center px-4">
      <div role="alert" className="rounded-lg bg-gray-900 px-4 py-3 text-sm text-white shadow-lg">{message}</div>
    </div>
  );
}
