interface InputProps {
  label?: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  placeholder?: string;
  required?: boolean;
  minLength?: number;
  className?: string;
}

export function Input({ label, value, onChange, className = "", ...props }: InputProps) {
  const input = (
    <input
      className={`w-full rounded-md border border-edge bg-panel px-3 py-1.5 text-sm outline-none focus:border-accent ${className}`}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      {...props}
    />
  );
  if (!label) return input;
  return (
    <label className="block text-sm">
      <span className="mb-1 block text-ink-dim">{label}</span>
      {input}
    </label>
  );
}

interface SelectProps {
  value: string;
  onChange: (value: string) => void;
  children: React.ReactNode;
  className?: string;
}

export function Select({ value, onChange, children, className = "" }: SelectProps) {
  return (
    <select
      className={`rounded-md border border-edge bg-panel px-3 py-1.5 text-sm outline-none focus:border-accent ${className}`}
      value={value}
      onChange={(e) => onChange(e.target.value)}
    >
      {children}
    </select>
  );
}
