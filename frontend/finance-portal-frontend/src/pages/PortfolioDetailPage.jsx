import { useParams } from 'react-router-dom';

export default function PortfolioDetailPage() {
  const { id } = useParams();
  return (
    <div>
      <h1>Portfolio Detail</h1>
      <p>Portfolio ID: <strong>{id}</strong></p>
      <p>Holding'ler ve işlem geçmişi burada görünecek.</p>
    </div>
  );
}
