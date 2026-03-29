import { useEffect, useState } from 'react';
import { getNews, getBloombergHtNews } from '../api/newsApi';

const TOPICS = [
  { label: 'Tümü',      keyword: undefined },
  { label: 'Fintech',   keyword: 'fintech' },
  { label: 'Enerji',    keyword: 'energy' },
  { label: 'Makro',     keyword: 'macro economy' },
  { label: 'Halka Arz', keyword: 'IPO' },
  { label: 'Görüş',     keyword: 'market outlook' },
];

function formatDate(raw) {
  if (!raw) return null;
  try { return new Date(raw).toLocaleString('tr-TR'); } catch { return raw; }
}

const styles = {
  tabs: { display: 'flex', gap: '8px', marginBottom: '16px', borderBottom: '1px solid #ddd', paddingBottom: '8px' },
  tab: (active) => ({
    padding: '6px 16px', borderRadius: '4px 4px 0 0', cursor: 'pointer',
    border: '1px solid #ccc', borderBottom: active ? '2px solid #1a73e8' : '1px solid #ccc',
    background: active ? '#e8f0fe' : 'transparent',
    color: active ? '#1a73e8' : 'inherit', fontWeight: active ? 'bold' : 'normal',
  }),
  topicBar: { display: 'flex', gap: '8px', marginBottom: '20px', flexWrap: 'wrap' },
  topicBtn: (active) => ({
    padding: '5px 14px', borderRadius: '20px', cursor: 'pointer', border: '1px solid #ccc',
    background: active ? '#1a73e8' : 'transparent',
    color: active ? '#fff' : 'inherit',
  }),
  card: {
    display: 'flex', gap: '14px', border: '1px solid #ddd',
    borderRadius: '6px', padding: '14px', marginBottom: '12px',
  },
  img: { width: '120px', height: '80px', objectFit: 'cover', borderRadius: '4px', flexShrink: 0 },
  meta: { fontSize: '0.78rem', color: '#888', marginBottom: '4px' },
  title: { margin: '0 0 6px', fontSize: '1rem' },
  desc: { margin: 0, fontSize: '0.875rem', color: '#444' },
};

function NewsCard({ item }) {
  return (
    <div style={styles.card}>
      {item.imageUrl && (
        <img src={item.imageUrl} alt="" style={styles.img}
          onError={(e) => { e.target.style.display = 'none'; }} />
      )}
      <div style={{ flex: 1 }}>
        <div style={styles.meta}>
          {item.source ?? 'Unknown source'}
          {item.author ? ` · ${item.author}` : ''}
          {formatDate(item.publishedAt) ? ` · ${formatDate(item.publishedAt)}` : ''}
        </div>
        <h3 style={styles.title}>
          {item.url
            ? <a href={item.url} target="_blank" rel="noopener noreferrer">{item.title ?? 'No title'}</a>
            : (item.title ?? 'No title')}
        </h3>
        {item.description && <p style={styles.desc}>{item.description}</p>}
      </div>
    </div>
  );
}

export default function NewsPage() {
  const [activeSource, setActiveSource] = useState('international'); // 'international' | 'bloomberg'
  const [activeTopic, setActiveTopic]   = useState(0);

  const [intlNews, setIntlNews]         = useState([]);
  const [bhtNews, setBhtNews]           = useState([]);
  const [loading, setLoading]           = useState(false);
  const [error, setError]               = useState('');

  function fetchIntl(topicIndex) {
    setLoading(true);
    setError('');
    const { keyword } = TOPICS[topicIndex];
    getNews({ category: 'business', country: 'us', keyword, pageSize: 50 })
      .then(setIntlNews)
      .catch((err) => setError(!err.response ? 'Unable to reach the server.' : `Error (${err.response.status})`))
      .finally(() => setLoading(false));
  }

  function fetchBht() {
    setLoading(true);
    setError('');
    getBloombergHtNews()
      .then(setBhtNews)
      .catch((err) => setError(!err.response ? 'Unable to reach the server.' : `Error (${err.response.status})`))
      .finally(() => setLoading(false));
  }

  useEffect(() => { fetchIntl(0); fetchBht(); }, []);

  function handleTopic(idx) {
    setActiveTopic(idx);
    fetchIntl(idx);
  }

  const displayedNews = activeSource === 'bloomberg' ? bhtNews : intlNews;

  return (
    <div>
      <h2>Finance News</h2>

      {/* Source tabs */}
      <div style={styles.tabs}>
        <button style={styles.tab(activeSource === 'international')} onClick={() => setActiveSource('international')}>
          🌍 International
        </button>
        <button style={styles.tab(activeSource === 'bloomberg')} onClick={() => setActiveSource('bloomberg')}>
          🇹🇷 BloombergHT
        </button>
      </div>

      {/* Topic filter — only for international */}
      {activeSource === 'international' && (
        <div style={styles.topicBar}>
          {TOPICS.map((t, idx) => (
            <button key={t.label} style={styles.topicBtn(activeTopic === idx)} onClick={() => handleTopic(idx)}>
              {t.label}
            </button>
          ))}
        </div>
      )}

      {loading && <p>Loading news...</p>}
      {error   && <p style={{ color: 'red' }}>{error}</p>}
      {!loading && !error && displayedNews.length === 0 && <p>No news found.</p>}
      {!loading && !error && displayedNews.map((item, idx) => <NewsCard key={idx} item={item} />)}
    </div>
  );
}
