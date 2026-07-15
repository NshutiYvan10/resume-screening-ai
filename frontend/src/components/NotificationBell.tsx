import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { Bell, CheckCheck } from 'lucide-react';
import { api } from '../lib/api';
import { timeAgo } from '../lib/format';
import type { Notification, Page } from '../types';

export default function NotificationBell() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: unread } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: async () => (await api.get<{ count: number }>('/notifications/unread-count')).data.count,
    refetchInterval: 20000,
  });

  const { data: list } = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: async () => (await api.get<Page<Notification>>('/notifications?size=10')).data,
    enabled: open,
  });

  const markRead = useMutation({
    mutationFn: async (id: string) => api.post(`/notifications/${id}/read`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  const markAll = useMutation({
    mutationFn: async () => api.post('/notifications/read-all'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const handleClick = (n: Notification) => {
    if (!n.read) markRead.mutate(n.id);
    if (n.link) {
      setOpen(false);
      navigate(n.link);
    }
  };

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        className="relative rounded-lg p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
        aria-label="Notifications"
      >
        <Bell className="h-5 w-5" />
        {!!unread && unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
          <div className="absolute right-0 z-40 mt-2 w-80 sm:w-96 card overflow-hidden">
            <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
              <h3 className="text-sm font-semibold text-slate-800">Notifications</h3>
              <button
                onClick={() => markAll.mutate()}
                className="flex items-center gap-1 text-xs text-brand-600 hover:text-brand-700"
              >
                <CheckCheck className="h-3.5 w-3.5" /> Mark all read
              </button>
            </div>
            <div className="max-h-96 overflow-y-auto">
              {!list?.content.length && (
                <p className="px-4 py-10 text-center text-sm text-slate-400">No notifications yet</p>
              )}
              {list?.content.map((n) => (
                <button
                  key={n.id}
                  onClick={() => handleClick(n)}
                  className={`flex w-full flex-col items-start gap-0.5 border-b border-slate-50 px-4 py-3 text-left hover:bg-slate-50 ${
                    !n.read ? 'bg-brand-50/40' : ''
                  }`}
                >
                  <div className="flex w-full items-start gap-2">
                    {!n.read && <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-brand-500" />}
                    <div className="flex-1">
                      <p className="text-sm font-medium text-slate-800">{n.title}</p>
                      <p className="mt-0.5 text-xs text-slate-500 line-clamp-2">{n.message}</p>
                      <p className="mt-1 text-[11px] text-slate-400">{timeAgo(n.createdAt)}</p>
                    </div>
                  </div>
                </button>
              ))}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
