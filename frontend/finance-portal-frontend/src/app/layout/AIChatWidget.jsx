import { useState, useRef, useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Bot, X, ArrowUp, Maximize2, Minimize2, Trash2, TrendingUp, Newspaper, Wallet } from 'lucide-react';
import { sendAssistantChat } from '../../api/assistantApi';
import { useAuth } from '../../context/AuthContext';
import { useTranslation } from '../../context/LanguageContext';

const STORAGE_KEY = 'porti_chat_v1';

let msgSeq = 1;
const nextId = () => ++msgSeq;

// Hızlı işlemler — M3 assist-chip; önceden hazır soru gönderir.
const QUICK_ACTIONS = [
  { label: 'Piyasa Özeti', icon: TrendingUp, prompt: 'Dolar, euro, gram altın ve Bitcoin şu an kaç TL?' },
  { label: 'Haberler', icon: Newspaper, prompt: 'Bugün ekonomi haberlerinde ne var? Kısaca özetle.' },
  { label: 'Portföyüm', icon: Wallet, prompt: 'Portföyümü özetle, dağılımım dengeli mi?' },
];

function loadSaved() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const data = JSON.parse(raw);
    if (!data || !Array.isArray(data.messages) || data.messages.length === 0) return null;
    return data;
  } catch {
    return null;
  }
}

export function AIChatWidget() {
  const { t } = useTranslation();
  const location = useLocation();
  const { username, isAuthenticated } = useAuth();
  const greeting = () => ({
    id: 1,
    text: isAuthenticated && username
      ? t('Merhaba {name}! Ben Porti. Piyasa, fiyat ve finans sorularında yardımcı olabilirim. Ne öğrenmek istersin?', { name: username })
      : t('Merhaba! Ben Porti. Piyasa, fiyat ve finans sorularında yardımcı olabilirim. Ne öğrenmek istersin?'),
    isBot: true,
  });

  const [isOpen, setIsOpen] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [bounce, setBounce] = useState(false);
  const [teaser, setTeaser] = useState(false);
  const [messages, setMessages] = useState(() => {
    const s = loadSaved();
    if (s && s.messages.length) {
      msgSeq = s.messages.reduce((mx, m) => Math.max(mx, m.id || 0), 1);
      return s.messages;
    }
    return [greeting()];
  });
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [locked, setLocked] = useState(() => !!loadSaved()?.locked);
  const scrollRef = useRef(null);
  const bounceTmo = useRef(null);

  function triggerBounce(ms = 4000) {
    setBounce(true);
    clearTimeout(bounceTmo.current);
    bounceTmo.current = setTimeout(() => setBounce(false), ms);
  }

  useEffect(() => {
    if (sessionStorage.getItem('porti_bounced')) return;
    triggerBounce(4000);
    sessionStorage.setItem('porti_bounced', '1');
  }, []);

  // Dashboard'a her gelişte: zıplama + proaktif karşılama balonu (sohbet kapalıysa).
  useEffect(() => {
    if (location.pathname !== '/dashboard' || isOpen) return undefined;
    triggerBounce(4500);
    setTeaser(true);
    const tmo = setTimeout(() => setTeaser(false), 11000);
    return () => clearTimeout(tmo);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname]);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ messages: messages.slice(-50), locked }));
    } catch { /* yok say */ }
  }, [messages, locked]);

  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
  }, [messages, loading]);

  function pushBot(text, extra = {}) {
    setMessages(prev => [...prev, { id: nextId(), text, isBot: true, ...extra }]);
  }

  function openChat() {
    setIsOpen(true);
    setBounce(false);
    setTeaser(false);
    sessionStorage.setItem('porti_bounced', '1');
  }

  function clearChat() {
    msgSeq = 1;
    setMessages([greeting()]);
    setLocked(false);
    try { localStorage.removeItem(STORAGE_KEY); } catch { /* yok say */ }
  }

  async function handleSend(preset) {
    const text = (preset ?? input).trim();
    if (!text || loading || locked) return;

    const userMsg = { id: nextId(), text, isBot: false };
    const updated = [...messages, userMsg];
    setMessages(updated);
    setInput('');
    setLoading(true);

    const history = updated
      .filter(m => m.id !== 1)
      .map(m => ({ role: m.isBot ? 'assistant' : 'user', content: m.text }));

    try {
      const res = await sendAssistantChat(history);
      switch (res?.status) {
        case 'OK':
          pushBot(res.reply || t('Bir hata oluştu. Lütfen tekrar deneyin.'));
          break;
        case 'LOGIN_REQUIRED':
          pushBot(t('Devam etmek için lütfen giriş yapın.'), { loginCta: true });
          setLocked(true);
          break;
        case 'RATE_LIMITED':
          pushBot(t('Günlük mesaj hakkınız doldu. Lütfen daha sonra tekrar deneyin.'));
          break;
        case 'BUSY':
          pushBot(t('Şu an yoğunluk var (günlük ücretsiz AI kotası dolmuş olabilir). Lütfen birkaç dakika sonra tekrar deneyin.'));
          break;
        case 'UNAVAILABLE':
          pushBot(t('Asistan şu an kullanılamıyor. Lütfen daha sonra tekrar deneyin.'));
          break;
        default:
          pushBot(t('Bir hata oluştu. Lütfen tekrar deneyin.'));
      }
    } catch {
      pushBot(t('Bir hata oluştu. Lütfen tekrar deneyin.'));
    } finally {
      setLoading(false);
    }
  }

  // ── Kapalı: FAB + (dashboard) teaser ──
  if (!isOpen) {
    return (
      <>
        {teaser && (
          <div
            onClick={openChat}
            className="fixed bottom-24 right-6 z-40 w-[252px] bg-white rounded-2xl shadow-xl shadow-black/10 border border-[#e2e1eb] p-3.5 pr-7 flex items-start gap-2.5 cursor-pointer hover:border-[#093eaa]/30 transition-colors"
          >
            <button
              onClick={e => { e.stopPropagation(); setTeaser(false); }}
              aria-label={t('Kapat')}
              className="absolute top-2 right-2 p-2 sm:p-0 text-[#9a9bab] hover:text-[#434653]"
            >
              <X className="w-3.5 h-3.5" />
            </button>
            <span className="mt-0.5 inline-flex items-center justify-center w-7 h-7 shrink-0 rounded-full bg-[#093eaa]/10 text-[#093eaa]">
              <Bot className="w-4 h-4" />
            </span>
            <p className="text-[12.5px] leading-snug text-[#434653]">
              <span className="font-bold text-[#1a1b22]">{t('Porti')} · </span>
              {t('Bugün portföyünde neler olmuş, birlikte inceleyelim? 👀')}
            </p>
          </div>
        )}
        <button onClick={openChat} aria-label="Porti"
          className={`fixed bottom-6 right-6 z-40 inline-flex items-center justify-center w-14 h-14 rounded-full bg-[#2b5cd1] text-white shadow-md shadow-[#2b5cd1]/25 hover:bg-[#2350c0] hover:shadow-lg transition-all ${bounce ? 'animate-bounce' : ''}`}>
          <Bot className="w-6 h-6" />
        </button>
      </>
    );
  }

  // ── Açık: M3 sohbet paneli ──
  return (
    <div className={`fixed bottom-6 right-6 z-40 bg-white rounded-3xl shadow-2xl shadow-black/15 border border-[#e2e1eb] overflow-hidden flex flex-col transition-all duration-200 ${
      expanded ? 'w-[440px] max-w-[92vw] h-[600px] max-h-[85vh]' : 'w-[360px] max-w-[92vw] h-[520px]'
    }`}>
      {/* Başlık — beyaz yüzey, tonal avatar (lacivert app-bar yok) */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-[#eeedf7]">
        <div className="flex items-center gap-2.5 min-w-0">
          <span className="inline-flex items-center justify-center w-9 h-9 rounded-full bg-[#093eaa]/10 text-[#093eaa]">
            <Bot className="w-5 h-5" />
          </span>
          <div className="min-w-0">
            <h5 className="text-sm font-bold text-[#1a1b22] leading-tight">{t('Porti')}</h5>
            <span className="flex items-center gap-1.5 text-[11px] text-[#747684]">
              <span className="w-1.5 h-1.5 rounded-full bg-[#10b981]" />
              {t('Finansal Asistan')}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-0.5">
          <button onClick={clearChat} title={t('Sohbeti temizle')}
            className="inline-flex items-center justify-center w-8 h-8 rounded-full text-[#747684] hover:bg-[#f3f3fc] hover:text-[#434653] transition-colors">
            <Trash2 className="w-[18px] h-[18px]" />
          </button>
          <button onClick={() => setExpanded(e => !e)} title={expanded ? t('Küçült') : t('Büyüt')}
            className="inline-flex items-center justify-center w-8 h-8 rounded-full text-[#747684] hover:bg-[#f3f3fc] hover:text-[#434653] transition-colors">
            {expanded ? <Minimize2 className="w-[18px] h-[18px]" /> : <Maximize2 className="w-[18px] h-[18px]" />}
          </button>
          <button onClick={() => setIsOpen(false)} aria-label={t('Kapat')}
            className="inline-flex items-center justify-center w-8 h-8 rounded-full text-[#747684] hover:bg-[#f3f3fc] hover:text-[#434653] transition-colors">
            <X className="w-[19px] h-[19px]" />
          </button>
        </div>
      </div>

      {/* Mesajlar */}
      <div ref={scrollRef} className="flex-1 px-3.5 py-4 overflow-y-auto flex flex-col gap-2 bg-[#fafafe]">
        {messages.map(msg => (
          <div key={msg.id} className={`max-w-[86%] px-3.5 py-2.5 text-[13px] leading-relaxed whitespace-pre-wrap ${
            msg.isBot
              ? 'self-start bg-[#f3f3fc] text-[#1a1b22] rounded-2xl rounded-tl-md'
              : 'self-end bg-[#2b5cd1] text-white rounded-2xl rounded-br-md'
          }`}>
            {msg.text}
            {msg.loginCta && (
              <Link to="/login" className="mt-2 inline-flex items-center gap-1 font-bold text-[#093eaa] bg-white px-2.5 py-1 rounded-full text-xs hover:bg-[#eef2fc]">
                {t('Giriş Yap')} →
              </Link>
            )}
          </div>
        ))}
        {loading && (
          <div className="self-start bg-[#f3f3fc] text-[#747684] px-3.5 py-3 rounded-2xl rounded-tl-md inline-flex items-center gap-1 w-fit">
            <span className="w-1.5 h-1.5 bg-[#9a9bab] rounded-full animate-bounce" />
            <span className="w-1.5 h-1.5 bg-[#9a9bab] rounded-full animate-bounce [animation-delay:140ms]" />
            <span className="w-1.5 h-1.5 bg-[#9a9bab] rounded-full animate-bounce [animation-delay:280ms]" />
          </div>
        )}
      </div>

      {/* Alt: hızlı işlemler + giriş */}
      <div className="px-3.5 pt-2.5 pb-3 bg-white border-t border-[#eeedf7]">
        {!locked && (
          <div className="flex flex-wrap gap-1.5 mb-2.5">
            {QUICK_ACTIONS.map(a => (
              <button key={a.label} onClick={() => handleSend(a.prompt)} disabled={loading}
                className="inline-flex items-center gap-1.5 pl-2 pr-3 py-1.5 rounded-full text-xs font-medium border border-[#e2e1eb] text-[#434653] bg-white hover:bg-[#f3f3fc] hover:border-[#093eaa]/40 hover:text-[#093eaa] transition-colors disabled:opacity-50">
                <a.icon className="w-3.5 h-3.5" />
                {t(a.label)}
              </button>
            ))}
          </div>
        )}

        <div className="flex items-end gap-2">
          <div className="flex-1 flex items-center bg-[#f3f3fc] rounded-2xl px-4 py-1">
            <input
              type="text"
              placeholder={locked ? t('Devam etmek için giriş yapın') : t("Porti'ye sorun...")}
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && handleSend()}
              disabled={loading || locked}
              className="w-full bg-transparent border-none py-2 text-[13px] text-[#1a1b22] placeholder-[#9a9bab] focus:outline-none disabled:opacity-60"
            />
          </div>
          <button onClick={() => handleSend()} disabled={loading || locked || !input.trim()}
            aria-label={t('Gönder')}
            className="inline-flex items-center justify-center w-10 h-10 shrink-0 rounded-full bg-[#2b5cd1] text-white shadow-sm hover:bg-[#2350c0] transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
            <ArrowUp className="w-[18px] h-[18px]" />
          </button>
        </div>

        <p className="mt-2 text-center text-[9.5px] text-[#9a9bab] leading-tight">
          {t('Porti yapay zekâdır, hata yapabilir. Yatırım tavsiyesi değildir.')}
        </p>
      </div>
    </div>
  );
}
