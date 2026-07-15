import { useState } from 'react';
import { X } from 'lucide-react';

export default function TagInput({
  value,
  onChange,
  placeholder,
  maxTags = 12,
}: {
  value: string[];
  onChange: (tags: string[]) => void;
  placeholder?: string;
  maxTags?: number;
}) {
  const [draft, setDraft] = useState('');

  const add = () => {
    const tag = draft.trim();
    if (!tag) return;
    if (value.length >= maxTags) return;
    if (value.some((t) => t.toLowerCase() === tag.toLowerCase())) {
      setDraft('');
      return;
    }
    onChange([...value, tag]);
    setDraft('');
  };

  return (
    <div className="rounded-lg border border-slate-300 bg-white px-2 py-1.5 shadow-sm focus-within:border-brand-500 focus-within:ring-1 focus-within:ring-brand-500">
      <div className="flex flex-wrap items-center gap-1.5">
        {value.map((tag) => (
          <span key={tag} className="badge bg-brand-50 text-brand-700">
            {tag}
            <button
              type="button"
              onClick={() => onChange(value.filter((t) => t !== tag))}
              className="ml-0.5 text-brand-400 hover:text-brand-700"
            >
              <X className="h-3 w-3" />
            </button>
          </span>
        ))}
        <input
          className="min-w-32 flex-1 border-0 px-1.5 py-1 text-sm focus:outline-none focus:ring-0"
          value={draft}
          placeholder={value.length ? '' : placeholder}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ',') {
              e.preventDefault();
              add();
            } else if (e.key === 'Backspace' && !draft && value.length) {
              onChange(value.slice(0, -1));
            }
          }}
          onBlur={add}
        />
      </div>
      <p className="mt-1 px-1.5 text-[11px] text-slate-400">
        Press Enter to add · {value.length}/{maxTags}
      </p>
    </div>
  );
}
