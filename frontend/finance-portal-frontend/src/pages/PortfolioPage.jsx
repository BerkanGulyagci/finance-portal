import { useEffect, useState } from 'react';
import { getMyPortfolios } from '../api/portfolioApi';

function fmt(value, fallback = '-') {
  if (value === null || value === undefined) return fallback;
  return typeof value === 'number' ? value.toFixed(2) : value;
}

export default function PortfolioPage() {
  const [portfolios, setPortfolios] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getMyPortfolios()
      .then(setPortfolios)
      .catch((err) => {
        if (!err.response) {
          setError('Unable to reach the server. Check your connection.');
        } else if (err.response.status === 401 || err.response.status === 403) {
          setError('You are not authorized to view this page.');
        } else {
          setError(`Failed to load portfolios (${err.response.status}).`);
        }
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p>Loading portfolios...</p>;
  if (error) return <p style={{ color: 'red' }}>{error}</p>;
  if (portfolios.length === 0) return <p>No portfolios yet.</p>;

  return (
    <div>
      <h2>My Portfolios</h2>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr>
            {['Name', 'Description', 'Currency', 'Total Cost', 'Market Value', 'P&L'].map((h) => (
              <th key={h} style={{ textAlign: 'left', padding: '8px', borderBottom: '1px solid #ccc' }}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {portfolios.map((p) => (
            <tr key={p.id}>
              <td style={{ padding: '8px' }}>{fmt(p.name)}</td>
              <td style={{ padding: '8px' }}>{fmt(p.description)}</td>
              <td style={{ padding: '8px' }}>{fmt(p.currency)}</td>
              <td style={{ padding: '8px' }}>{fmt(p.totalCost, '0.00')}</td>
              <td style={{ padding: '8px' }}>{fmt(p.totalMarketValue, '0.00')}</td>
              <td style={{ padding: '8px', color: (p.totalProfitLoss ?? 0) >= 0 ? 'green' : 'red' }}>
                {fmt(p.totalProfitLoss, '0.00')}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
