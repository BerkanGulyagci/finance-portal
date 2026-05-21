import { useState } from 'react';
import { Bot, X, Send, MessageCircle } from 'lucide-react';
import { useTranslation } from '../../i18n/LanguageContext';

export function AIChatWidget() {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState([
    { id: 1, text: t('Merhaba! Bugünkü piyasa hareketlerini özetlememi ister misiniz? Ya da bir hisseyi analiz edebilirim.'), isBot: true },
  ]);
  const [input, setInput] = useState('');

  function handleSend() {
    if (!input.trim()) return;
    const userMsg = { id: messages.length + 1, text: input, isBot: false };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setTimeout(() => {
      setMessages(prev => [...prev, {
        id: prev.length + 1,
        text: t('Sorunuzu analiz ediyorum. Size en güncel piyasa verileriyle yardımcı olacağım.'),
        isBot: true,
      }]);
    }, 1000);
  }

  if (!isOpen) {
    return (
      <button onClick={() => setIsOpen(true)}
        className="fixed bottom-6 right-6 z-50 bg-[#093eaa] text-white p-4 rounded-full shadow-xl hover:scale-110 transition-transform">
        <MessageCircle className="w-6 h-6" />
      </button>
    );
  }

  return (
    <div className="fixed bottom-6 right-6 z-50 w-80 bg-white rounded-2xl shadow-2xl border border-gray-200 overflow-hidden">
      <div className="bg-[#093eaa] p-4 text-white flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Bot className="w-5 h-5" />
          <div>
            <h5 className="text-sm font-bold">{t('Warren AI')}</h5>
            <span className="text-[10px] opacity-80 uppercase">{t('Finansal Asistan')}</span>
          </div>
        </div>
        <button onClick={() => setIsOpen(false)} className="opacity-80 hover:opacity-100 transition-opacity">
          <X className="w-5 h-5" />
        </button>
      </div>

      <div className="p-4 h-64 overflow-y-auto flex flex-col gap-3">
        {messages.map(msg => (
          <div key={msg.id} className={`p-3 rounded-lg text-xs leading-relaxed ${
            msg.isBot ? 'bg-gray-100 text-gray-800' : 'bg-[#093eaa] text-white ml-auto max-w-[80%]'
          }`}>
            {msg.text}
          </div>
        ))}
      </div>

      <div className="p-4 border-t border-gray-200">
        <div className="relative">
          <input
            type="text"
            placeholder={t('Sorunuzu buraya yazın...')}
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSend()}
            className="w-full pl-3 pr-10 py-2 bg-gray-100 border-none rounded-lg text-xs focus:outline-none focus:ring-1 focus:ring-[#093eaa]"
          />
          <button onClick={handleSend}
            className="absolute right-2 top-1/2 -translate-y-1/2 text-[#093eaa] hover:opacity-70 transition-opacity">
            <Send className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
