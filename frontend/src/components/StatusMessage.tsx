export default function StatusMessage({ children, error = false }: { children: string; error?: boolean }) {
  return (
    <p className={`rounded-lg border bg-white px-4 py-6 text-center text-sm ${error ? 'border-red-200 text-red-600' : 'border-gray-200 text-gray-500'}`}>
      {children}
    </p>
  );
}
