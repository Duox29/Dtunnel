export function ErrorNotice({ message }: { message: string }) {
  if (!message) return null;
  return (
    <div className="mx-3 my-2 rounded-md border border-bad/40 bg-bad-bg px-3 py-2 text-sm text-bad">
      {message}
    </div>
  );
}
