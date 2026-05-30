import { useEffect, useState } from 'react';
import { Newspaper } from 'lucide-react';
import { getBloombergHtNews } from '../../../../api/newsApi';
import { useTranslation } from '../../../../context/LanguageContext';

export default function ViopRelatedNews({ contract }) {
  const { t } = useTranslation();
  const [news, setNews] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!contract) return;

    setLoading(true);
    getBloombergHtNews()
      .then(items => {
        // Kontrat adından anahtar kelimeleri çıkar
        const keywords = extractKeywords(contract.name);
        
        // İlgili haberleri filtrele
        const filtered = items.filter(item => {
          const text = ((item.title ?? '') + ' ' + (item.description ?? '')).toLowerCase();
          return keywords.some(k => text.includes(k));
        });

        // En az 3 haber varsa filtrelenmiş listeyi göster, yoksa genel haberleri göster
        setNews(filtered.length >= 3 ? filtered.slice(0, 6) : items.slice(0, 6));
      })
      .catch(() => setNews([]))
      .finally(() => setLoading(false));
  }, [contract]);

  // Kontrat adından anahtar kelimeleri çıkar
  const extractKeywords = (name) => {
    if (!name) return [];
    
    const keywords = [];
    
    // Hisse senedi kodu (ilk kelime genelde)
    const firstWord = name.split(' ')[0];
    if (firstWord && firstWord.length >= 3) {
      keywords.push(firstWord.toLowerCase());
    }
    
    // Endeks isimleri
    if (name.includes('XU030') || name.includes('BIST 30')) {
      keywords.push('bist 30', 'xu030', 'endeks');
    }
    if (name.includes('XU100') || name.includes('BIST 100')) {
      keywords.push('bist 100', 'xu100', 'endeks');
    }
    
    // Genel vadeli işlem terimleri
    keywords.push('vadeli', 'viop', 'türev');
    
    return keywords;
  };

  if (loading) {
    return (
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
        <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
          <Newspaper className="w-5 h-5 text-[#093eaa]" />
          {t('İlgili Haberler')}
        </h2>
        <div className="flex items-center gap-2 py-4">
          <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
          <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
          <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
        <Newspaper className="w-5 h-5 text-[#093eaa]" />
        {t('İlgili Haberler')}
      </h2>

      {news.length === 0 ? (
        <p className="text-gray-400 text-sm">{t('Haber bulunamadı.')}</p>
      ) : (
        <div className="space-y-4">
          {news.map((item, i) => (
            <a
              key={i}
              href={item.url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex gap-3 group hover:bg-gray-50 rounded-xl p-2 -mx-2 transition-colors"
            >
              {item.imageUrl && (
                <img
                  src={item.imageUrl}
                  alt=""
                  className="w-16 h-16 object-cover rounded-lg flex-shrink-0"
                />
              )}
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-gray-800 group-hover:text-[#093eaa] transition-colors line-clamp-2 leading-snug">
                  {item.title}
                </p>
                <p className="text-xs text-gray-400 mt-1">
                  {item.source && <span className="font-semibold">{item.source} · </span>}
                  {item.publishedAt ? new Date(item.publishedAt).toLocaleDateString('tr-TR') : ''}
                </p>
              </div>
            </a>
          ))}
        </div>
      )}
    </div>
  );
}
